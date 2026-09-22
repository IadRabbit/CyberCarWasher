package com.meethack.romhack.camp2026.wrappers;

import android.content.Context;
import android.nfc.tech.MifareClassic;
import android.util.Log;

import com.meethack.romhack.camp2026.assets.Customer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Owns all MIFARE Classic I/O performed by the app.
 *
 * <p>A failed Crypto1 authentication can invalidate the active NFC session on some controllers.
 * Consequently, every failed key attempt is followed by a full close/connect cycle and callers
 * never authenticate with key A and key B back-to-back on the same failed session.</p>
 */
public class NfcWrapper {
    private static final String TAG = "NfcWrapper";
    private static final int BLOCK_SIZE = MifareClassic.BLOCK_SIZE;
    private static final int KEY_A_INDEX = 0;
    private static final int KEY_B_INDEX = 1;

    private static final int NAME_SECTOR = 15;
    private static final int SURNAME_SECTOR = 14;
    private static final int CREATION_DATE_SECTOR = 13;
    private static final int AMOUNT_SECTOR = 12;

    private static final byte[] RESET_FACTORY_ACCESS_BITS = new byte[]{
            (byte) 0xFF, (byte) 0x07, (byte) 0x80, (byte) 0x49
    };

    private enum KeyType { A, B }

    private enum KeySource { SERVER, DEFAULT }

    private static final class KeyCandidate {
        final KeyType type;
        final KeySource source;
        final byte[] key;

        KeyCandidate(KeyType type, KeySource source, byte[] key) {
            this.type = type;
            this.source = source;
            this.key = key;
        }
    }

    private static final class Authentication {
        final KeySource source;

        Authentication(KeySource source) {
            this.source = source;
        }
    }

    private final MifareClassic mifareCard;
    private final RequestWrapper requestWrapper;
    private byte[][][] sectorKeys = new byte[0][][];
    private boolean keysLoaded;

    public NfcWrapper(MifareClassic mifareCard, Context context) {
        if (mifareCard == null) {
            throw new IllegalArgumentException("mifareCard must not be null");
        }
        this.mifareCard = mifareCard;
        this.requestWrapper = new RequestWrapper(context);
    }

    /**
     * Authenticates once per candidate and stops at the first usable key. The ordering mirrors the
     * fixed admin APK: server B, server A, default B, default A.
     */
    private Authentication authenticateSector(int sector) throws IOException {
        validateSector(sector);
        fetchKeys();

        for (KeyCandidate candidate : authenticationCandidates(sector)) {
            try {
                if (authenticate(sector, candidate)) {
                    return new Authentication(candidate.source);
                }
            } catch (IOException failure) {
                reconnectAfterFailure(failure);
                continue;
            }

            // A false result leaves Crypto1 unusable on some NFC stacks (notably Samsung's).
            reconnect();
        }
        return null;
    }

    private boolean authenticate(int sector, KeyCandidate candidate) throws IOException {
        if (candidate.type == KeyType.A) {
            return mifareCard.authenticateSectorWithKeyA(sector, candidate.key);
        }
        return mifareCard.authenticateSectorWithKeyB(sector, candidate.key);
    }

    private List<KeyCandidate> authenticationCandidates(int sector) {
        List<KeyCandidate> candidates = new ArrayList<>(4);
        addServerCandidate(candidates, sector, KEY_B_INDEX, KeyType.B);
        addServerCandidate(candidates, sector, KEY_A_INDEX, KeyType.A);
        addCandidate(candidates, new KeyCandidate(KeyType.B, KeySource.DEFAULT,
                MifareClassic.KEY_DEFAULT));
        addCandidate(candidates, new KeyCandidate(KeyType.A, KeySource.DEFAULT,
                MifareClassic.KEY_DEFAULT));
        return candidates;
    }

    /** Key order used when a block operation must be retried with operation-specific access. */
    private List<KeyCandidate> blockOperationCandidates(int sector) {
        List<KeyCandidate> candidates = new ArrayList<>(4);
        addServerCandidate(candidates, sector, KEY_A_INDEX, KeyType.A);
        addServerCandidate(candidates, sector, KEY_B_INDEX, KeyType.B);
        addCandidate(candidates, new KeyCandidate(KeyType.A, KeySource.DEFAULT,
                MifareClassic.KEY_DEFAULT));
        addCandidate(candidates, new KeyCandidate(KeyType.B, KeySource.DEFAULT,
                MifareClassic.KEY_DEFAULT));
        return candidates;
    }

    private void addServerCandidate(List<KeyCandidate> candidates, int sector, int keyIndex,
                                    KeyType type) {
        if (sector >= sectorKeys.length || sectorKeys[sector] == null
                || keyIndex >= sectorKeys[sector].length) {
            return;
        }
        byte[] key = sectorKeys[sector][keyIndex];
        if (key != null && key.length == Utils.keySize) {
            addCandidate(candidates, new KeyCandidate(type, KeySource.SERVER, key));
        }
    }

    private static void addCandidate(List<KeyCandidate> candidates, KeyCandidate candidate) {
        for (KeyCandidate existing : candidates) {
            if (existing.type == candidate.type && Arrays.equals(existing.key, candidate.key)) {
                return;
            }
        }
        candidates.add(candidate);
    }

    /**
     * Loads the per-sector keys exactly once. The tag connection is released while the network
     * request is in progress, then restored for the caller.
     */
    public void fetchKeys() throws IOException {
        if (keysLoaded) {
            return;
        }

        ensureConnected();
        boolean authenticated = mifareCard.authenticateSectorWithKeyA(
                0, MifareClassic.KEY_DEFAULT);
        if (!authenticated) {
            throw new IOException("Could not authenticate sector 0 to read block 0");
        }

        byte[] block0 = mifareCard.readBlock(0);
        closeQuietly();

        byte[][][] fetchedKeys = requestWrapper.fetchKeys(block0);
        validateFetchedKeys(fetchedKeys);
        sectorKeys = fetchedKeys;
        keysLoaded = true;
        ensureConnected();
    }

    private static void validateFetchedKeys(byte[][][] fetchedKeys) throws IOException {
        if (fetchedKeys == null || fetchedKeys.length == 0) {
            throw new IOException("The key service returned no sector keys");
        }
        for (int sector = 0; sector < fetchedKeys.length; sector++) {
            byte[][] keys = fetchedKeys[sector];
            if (keys == null || keys.length < 2
                    || keys[KEY_A_INDEX] == null || keys[KEY_A_INDEX].length != Utils.keySize
                    || keys[KEY_B_INDEX] == null || keys[KEY_B_INDEX].length != Utils.keySize) {
                throw new IOException("Invalid keys returned for sector " + sector);
            }
        }
    }

    public static final class SectorDump {
        public final int sector;
        public final boolean authenticated;
        public final byte[][] blocks;

        SectorDump(int sector, boolean authenticated, byte[][] blocks) {
            this.sector = sector;
            this.authenticated = authenticated;
            this.blocks = blocks;
        }
    }

    public List<SectorDump> dumpSectors() throws IOException {
        ensureConnected();
        try {
            fetchKeys();
            List<SectorDump> dump = new ArrayList<>();
            int sectorCount = mifareCard.getSectorCount();

            for (int sector = 0; sector < sectorCount; sector++) {
                Authentication authentication = authenticateSector(sector);
                int firstBlock = mifareCard.sectorToBlock(sector);
                int blockCount = mifareCard.getBlockCountInSector(sector);
                byte[][] blocks = new byte[blockCount][];

                if (authentication != null) {
                    for (int blockIndex = 0; blockIndex < blockCount; blockIndex++) {
                        // A protected block must not prevent the rest of the diagnostic dump.
                        blocks[blockIndex] = readBlockOrZeros(firstBlock + blockIndex);
                    }
                    injectKnownKeys(sector, authentication, blocks, blockCount - 1);
                }

                dump.add(new SectorDump(sector, authentication != null, blocks));
            }
            return dump;
        } finally {
            closeQuietly();
        }
    }

    private void injectKnownKeys(int sector, Authentication authentication, byte[][] blocks,
                                 int trailerIndex) {
        if (blocks[trailerIndex] == null || blocks[trailerIndex].length != BLOCK_SIZE) {
            return;
        }

        byte[] keyA = MifareClassic.KEY_DEFAULT;
        byte[] keyB = MifareClassic.KEY_DEFAULT;
        if (authentication.source == KeySource.SERVER && sector < sectorKeys.length) {
            keyA = sectorKeys[sector][KEY_A_INDEX];
            keyB = sectorKeys[sector][KEY_B_INDEX];
        }
        System.arraycopy(keyA, 0, blocks[trailerIndex], 0, Utils.keySize);
        System.arraycopy(keyB, 0, blocks[trailerIndex], 10, Utils.keySize);
    }

    public static final class RawBlockEdit {
        public final int sector;
        public final int blockIndexInSector;
        public final byte[] data;

        public RawBlockEdit(int sector, int blockIndexInSector, byte[] data) {
            if (sector < 0 || blockIndexInSector < 0) {
                throw new IllegalArgumentException("Sector and block index must be non-negative");
            }
            if (data == null || data.length != BLOCK_SIZE) {
                throw new IllegalArgumentException("A MIFARE Classic block must contain 16 bytes");
            }
            this.sector = sector;
            this.blockIndexInSector = blockIndexInSector;
            this.data = data.clone();
        }
    }

    static boolean logicW(int c1, int c2, int c3) {
        return (c1 == 0 && c2 == 0 && c3 == 0)
                || (c1 == 1 && c2 == 0 && c3 == 0)
                || (c1 == 1 && c2 == 1 && c3 == 0)
                || (c1 == 0 && c2 == 1 && c3 == 1);
    }

    boolean isBlockWritable(byte[] sectorTrailer, int blockIndex) {
        return isBlockWritable(sectorTrailer, blockIndex, 4);
    }

    private boolean isBlockWritable(byte[] sectorTrailer, int blockIndex, int blockCount) {
        if (!hasValidAccessBits(sectorTrailer)) {
            return false;
        }

        int group = accessGroupForBlock(blockIndex, blockCount);
        if (group < 0 || group > 2) {
            return false;
        }

        int c1 = bit(sectorTrailer[7], 4 + group);
        int c2 = bit(sectorTrailer[8], group);
        int c3 = bit(sectorTrailer[8], 4 + group);
        return logicW(c1, c2, c3);
    }

    private static int accessGroupForBlock(int blockIndex, int blockCount) {
        int trailerIndex = blockCount - 1;
        if (blockIndex < 0 || blockIndex >= trailerIndex) {
            return -1;
        }
        if (blockCount <= 4) {
            return blockIndex;
        }
        // In 16-block sectors, access groups cover blocks 0-4, 5-9 and 10-14.
        return Math.min(blockIndex / 5, 2);
    }

    private static boolean hasValidAccessBits(byte[] trailer) {
        if (trailer == null || trailer.length != BLOCK_SIZE) {
            return false;
        }
        for (int group = 0; group < 4; group++) {
            int c1 = bit(trailer[7], 4 + group);
            int c2 = bit(trailer[8], group);
            int c3 = bit(trailer[8], 4 + group);
            int invertedC1 = bit(trailer[6], group);
            int invertedC2 = bit(trailer[6], 4 + group);
            int invertedC3 = bit(trailer[7], group);
            if (invertedC1 != (c1 ^ 1)
                    || invertedC2 != (c2 ^ 1)
                    || invertedC3 != (c3 ^ 1)) {
                return false;
            }
        }
        return true;
    }

    private static int bit(byte value, int position) {
        return (value >>> position) & 1;
    }

    public void writeRawBlocks(List<RawBlockEdit> edits) throws IOException {
        if (edits == null) {
            throw new IllegalArgumentException("edits must not be null");
        }
        if (edits.isEmpty()) {
            return;
        }

        ensureConnected();
        try {
            fetchKeys();
            validateEdits(edits);

            int activeSector = -1;
            int firstBlock = -1;
            int blockCount = 0;
            byte[] trailer = null;

            for (RawBlockEdit edit : edits) {
                if (edit.sector != activeSector) {
                    requireAuthentication(edit.sector);
                    activeSector = edit.sector;
                    firstBlock = mifareCard.sectorToBlock(activeSector);
                    blockCount = mifareCard.getBlockCountInSector(activeSector);
                    trailer = readBlockResilient(firstBlock + blockCount - 1);
                }

                int absoluteBlock = firstBlock + edit.blockIndexInSector;
                int trailerIndex = blockCount - 1;

                // Block 0 is the immutable manufacturer block. Other blocks in sector 0 remain
                // writable when their access conditions permit it.
                if (absoluteBlock == 0) {
                    continue;
                }
                if (edit.blockIndexInSector != trailerIndex
                        && !isBlockWritable(trailer, edit.blockIndexInSector, blockCount)) {
                    continue;
                }

                writeBlockResilient(absoluteBlock, edit.data);
                if (edit.blockIndexInSector == trailerIndex) {
                    trailer = edit.data.clone();
                }
            }
        } finally {
            closeQuietly();
        }
    }

    private void validateEdits(List<RawBlockEdit> edits) throws IOException {
        int sectorCount = mifareCard.getSectorCount();
        for (RawBlockEdit edit : edits) {
            if (edit == null) {
                throw new IOException("The write list contains an empty edit");
            }
            if (edit.sector >= sectorCount) {
                throw new IOException("Sector " + edit.sector + " is not present on this card");
            }
            int blockCount = mifareCard.getBlockCountInSector(edit.sector);
            if (edit.blockIndexInSector >= blockCount) {
                throw new IOException("Block " + edit.blockIndexInSector
                        + " is not present in sector " + edit.sector);
            }
            if (edit.data.length != BLOCK_SIZE) {
                throw new IOException("Invalid data length for sector " + edit.sector
                        + ", block " + edit.blockIndexInSector);
            }
        }
    }

    public Customer readData() throws IOException {
        ensureConnected();
        try {
            fetchKeys();
            String name = readText(NAME_SECTOR);
            String surname = readText(SURNAME_SECTOR);
            Calendar creationDate = readCreationDate();
            int amount = readAmountValue();
            return new Customer(name, surname, creationDate, amount);
        } finally {
            closeQuietly();
        }
    }

    private int readAmountValue() throws IOException {
        requireAuthentication(AMOUNT_SECTOR);
        int firstBlock = mifareCard.sectorToBlock(AMOUNT_SECTOR);
        byte[] block = readBlockResilient(firstBlock);
        return ByteBuffer.wrap(block, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private String readText(int sector) throws IOException {
        requireAuthentication(sector);
        int firstBlock = mifareCard.sectorToBlock(sector);
        byte[] textBytes = new byte[48];

        for (int blockIndex = 0; blockIndex < 3; blockIndex++) {
            byte[] block = readBlockResilient(firstBlock + blockIndex);
            System.arraycopy(block, 0, textBytes, BLOCK_SIZE * blockIndex, BLOCK_SIZE);
        }
        return Utils.hexToString(textBytes);
    }

    private Calendar readCreationDate() throws IOException {
        requireAuthentication(CREATION_DATE_SECTOR);
        int firstBlock = mifareCard.sectorToBlock(CREATION_DATE_SECTOR);
        int year = Utils.getValueFromBlock(readBlockResilient(firstBlock));
        int month = Utils.getValueFromBlock(readBlockResilient(firstBlock + 1));
        int day = Utils.getValueFromBlock(readBlockResilient(firstBlock + 2));
        Calendar calendar = Calendar.getInstance();
        calendar.setLenient(false);
        calendar.set(year, month - 1, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        // Force validation now so corrupt card data is reported as an I/O failure to the scene.
        try {
            calendar.getTimeInMillis();
        } catch (IllegalArgumentException invalidDate) {
            throw new IOException("The card contains an invalid creation date", invalidDate);
        }
        return calendar;
    }

    private byte[] createResetFactorySectorTrailer() {
        byte[] trailer = new byte[BLOCK_SIZE];
        System.arraycopy(MifareClassic.KEY_DEFAULT, 0, trailer, 0, Utils.keySize);
        System.arraycopy(RESET_FACTORY_ACCESS_BITS, 0, trailer, 6,
                RESET_FACTORY_ACCESS_BITS.length);
        System.arraycopy(MifareClassic.KEY_DEFAULT, 0, trailer, 10, Utils.keySize);
        return trailer;
    }

    public void format() throws IOException {
        ensureConnected();
        try {
            fetchKeys();
            int sectorCount = mifareCard.getSectorCount();
            byte[] emptyBlock = new byte[BLOCK_SIZE];

            for (int sector = 0; sector < sectorCount; sector++) {
                requireAuthentication(sector);
                int firstBlock = mifareCard.sectorToBlock(sector);
                int blockCount = mifareCard.getBlockCountInSector(sector);
                int trailerBlock = firstBlock + blockCount - 1;
                byte[] factoryTrailer = createResetFactorySectorTrailer();

                // Reset access conditions first, then re-authenticate with the new default keys
                // while clearing data. This also works when A and B grant different permissions.
                writeBlockResilient(trailerBlock, factoryTrailer);
                int firstDataBlock = firstBlock == 0 ? firstBlock + 1 : firstBlock;
                for (int block = firstDataBlock; block < trailerBlock; block++) {
                    writeBlockResilient(block, emptyBlock);
                }
                writeBlockResilient(trailerBlock, factoryTrailer);
            }
        } finally {
            closeQuietly();
        }
    }

    /**
     * Reads with the active session first, then reconnects and retries all known A/B keys. A
     * failure is surfaced to business reads; the diagnostic dump alone converts it to zeroes.
     */
    private byte[] readBlockResilient(int block) throws IOException {
        IOException failures = null;
        try {
            return mifareCard.readBlock(block);
        } catch (IOException failure) {
            failures = failure;
        }

        int sector = mifareCard.blockToSector(block);
        for (KeyCandidate candidate : blockOperationCandidates(sector)) {
            try {
                reconnect();
                if (!authenticate(sector, candidate)) {
                    continue;
                }
                return mifareCard.readBlock(block);
            } catch (IOException failure) {
                failures = appendFailure(failures, failure);
            }
        }

        throw operationFailure("read", block, failures);
    }

    private byte[] readBlockOrZeros(int block) throws IOException {
        try {
            return readBlockResilient(block);
        } catch (IOException failure) {
            Log.w(TAG, "Block " + block + " is not readable with the available keys", failure);
            return new byte[BLOCK_SIZE];
        }
    }

    /** Tries the active authentication, then every known key until the write is acknowledged. */
    private void writeBlockResilient(int block, byte[] data) throws IOException {
        IOException failures = null;
        try {
            mifareCard.writeBlock(block, data);
            return;
        } catch (IOException failure) {
            failures = failure;
        }

        int sector = mifareCard.blockToSector(block);
        for (KeyCandidate candidate : blockOperationCandidates(sector)) {
            try {
                reconnect();
                if (!authenticate(sector, candidate)) {
                    continue;
                }
                mifareCard.writeBlock(block, data);
                return;
            } catch (IOException failure) {
                failures = appendFailure(failures, failure);
            }
        }

        throw operationFailure("write", block, failures);
    }

    private void requireAuthentication(int sector) throws IOException {
        if (authenticateSector(sector) == null) {
            throw new IOException("Authentication failed for sector " + sector);
        }
    }

    private void validateSector(int sector) throws IOException {
        if (sector < 0 || sector >= mifareCard.getSectorCount()) {
            throw new IOException("Invalid sector " + sector);
        }
    }

    private void ensureConnected() throws IOException {
        if (!mifareCard.isConnected()) {
            mifareCard.connect();
        }
    }

    private void reconnectAfterFailure(IOException failure) throws IOException {
        try {
            reconnect();
        } catch (IOException reconnectFailure) {
            reconnectFailure.addSuppressed(failure);
            throw reconnectFailure;
        }
    }

    private void reconnect() throws IOException {
        IOException closeFailure = null;
        try {
            mifareCard.close();
        } catch (IOException failure) {
            closeFailure = failure;
        }

        try {
            mifareCard.connect();
        } catch (IOException connectFailure) {
            if (closeFailure != null) {
                connectFailure.addSuppressed(closeFailure);
            }
            throw connectFailure;
        }

        if (closeFailure != null) {
            Log.w(TAG, "NFC connection closed with an error before reconnecting", closeFailure);
        }
    }

    private void closeQuietly() {
        if (!mifareCard.isConnected()) {
            return;
        }
        try {
            mifareCard.close();
        } catch (IOException failure) {
            Log.w(TAG, "Could not close the NFC connection", failure);
        }
    }

    private static IOException appendFailure(IOException current, IOException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private static IOException operationFailure(String operation, int block, IOException cause) {
        IOException failure = new IOException(
                "Could not " + operation + " block " + block + " with the available keys");
        if (cause != null) {
            failure.initCause(cause);
        }
        return failure;
    }
}
