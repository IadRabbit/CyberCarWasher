package com.meethack.romhack.camp2026.wrappers;

import android.content.Context;
import android.nfc.tech.MifareClassic;

import com.meethack.romhack.camp2026.assets.Customer;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class NfcWrapper {
    private enum LOGGED {
        UNAUTHORIZED,
        DEFAULT_KEYS,
        COMPUTED_KEYS,
    }
    private static final int retryConnect = 3;
    private final MifareClassic mifareCard;
    private final RequestWrapper requestWrapper;
    private static final int nameSector = 15;
    private static final int surnameSector = 14;
    private static final int creationDateSector = 13;
    private static final int amountSector = 12;
    private byte[][][] sectorKeys = new byte[16][2][Utils.keySize];

    private static final byte[] resetFactoryAccessBits = new byte[]{
            (byte)0xFF, (byte)0x07, (byte)0x80, (byte)0x49
    };

    public NfcWrapper(MifareClassic mifareCard, Context context) throws IOException {
        this.mifareCard = mifareCard;
        this.requestWrapper = new RequestWrapper(context);
        this.mifareCard.connect();
        this.fetchKeys();
        this.mifareCard.close();
    }

    private LOGGED auth(int sector) throws IOException {
        boolean authA;
        boolean authB;

        authA = this.mifareCard.authenticateSectorWithKeyA(sector, MifareClassic.KEY_DEFAULT);
        authB = this.mifareCard.authenticateSectorWithKeyB(sector, MifareClassic.KEY_DEFAULT);

        if (authA && authB){
            return LOGGED.DEFAULT_KEYS;
        }

        authA = this.mifareCard.authenticateSectorWithKeyA(sector, this.sectorKeys[sector][0]);

        if (!authA){
            return LOGGED.UNAUTHORIZED;
        }

        authB = this.mifareCard.authenticateSectorWithKeyB(sector, this.sectorKeys[sector][1]);

        if (!authB){
            return LOGGED.UNAUTHORIZED;
        }

        return LOGGED.COMPUTED_KEYS;
    }

    private void fetchKeys() throws IOException {
        boolean authenticated = this.mifareCard.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT);
        if (!authenticated) {
            throw new IOException("Could not authenticate sector 0 to read block 0");
        }
        byte[] block0 = this.mifareCard.readBlock(0);
        this.sectorKeys = this.requestWrapper.fetchKeys(block0);
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

    private byte[] readTillTheEnd(int block, int sector) throws IOException {
        int tries = 0;
        while (tries < retryConnect){
            try {
                return this.mifareCard.readBlock(block);
            }
            catch (IOException e){
                this.mifareCard.close();
                tries++;
                this.mifareCard.connect();
                this.auth(sector);
            }
        }

        return null;
    }

    private void writeTillTheEnd(int block, int sector, byte[] data) throws IOException {
        int tries = 0;
        while (tries < retryConnect){
            try {
                this.mifareCard.writeBlock(block, data);
                return;
            }
            catch (IOException e){
                this.mifareCard.close();
                tries++;
                this.mifareCard.connect();
                this.auth(sector);
            }
        }
    }

    public List<SectorDump> dumpSectors() throws IOException {
        List<SectorDump> dump = new ArrayList<>();
        this.mifareCard.connect();

        for (int sector = 0; sector < 16; sector++) {
            LOGGED authenticated = this.auth(sector);
            int firstBlockOfSector = this.mifareCard.sectorToBlock(sector);
            int blockCount = this.mifareCard.getBlockCountInSector(sector);
            byte[][] blocks = new byte[blockCount][];

            if (authenticated != LOGGED.UNAUTHORIZED) {
                for (int b = 0; b < blockCount; b++) {
                    blocks[b] = this.readTillTheEnd(firstBlockOfSector + b, sector);
                }

                byte[] keyAB = MifareClassic.KEY_DEFAULT;
                byte[] keyBB = MifareClassic.KEY_DEFAULT;

                if (authenticated == LOGGED.COMPUTED_KEYS) {
                    keyAB = this.sectorKeys[sector][0];
                    keyBB = this.sectorKeys[sector][1];
                }

                System.arraycopy(keyAB, 0, blocks[3], 0, Utils.keySize);
                System.arraycopy(keyBB, 0, blocks[3], 10, Utils.keySize);
            }

            dump.add(new SectorDump(sector, authenticated != LOGGED.UNAUTHORIZED, blocks));
        }
        this.mifareCard.close();
        return dump;
    }

    public static final class RawBlockEdit {
        public final int sector;
        public final int blockIndexInSector;
        public final byte[] data;

        public RawBlockEdit(int sector, int blockIndexInSector, byte[] data) {
            this.sector = sector;
            this.blockIndexInSector = blockIndexInSector;
            this.data = data;
        }
    }


    boolean logicW(int C1, int C2, int C3){
        boolean isWritable = false;

        if (C1 == 0 && C2 == 0 && C3 == 0){
            isWritable = true;
        }

        else if (C1 == 1 && C2 == 0 && C3 == 0){
            isWritable = true;
        }

        else if (C1 == 1 && C2 == 1 && C3 == 0){
            isWritable = true;
        }

        else if (C1 == 0 && C2 == 1 && C3 == 1){
            isWritable = true;
        }

        return isWritable;
    }

    boolean isBlockWritable(byte[] sectorBlock, int blockNum){
        boolean isWritable = false;
        int[] bits2 = Utils.byte2ArrayBits(sectorBlock[7]);
        int[] bits3 = Utils.byte2ArrayBits(sectorBlock[8]);
        int C1, C2, C3;


        switch (blockNum){
            case 0:
                C1 = bits2[3];
                C2 = bits3[7];
                C3 = bits3[3];
                isWritable = logicW(C1, C2, C3);
                break;

            case 1:
                C1 = bits2[2];
                C2 = bits3[6];
                C3 = bits3[2];
                isWritable = logicW(C1, C2, C3);
                break;

            case 2:
                C1 = bits2[1];
                C2 = bits3[5];
                C3 = bits3[1];
                isWritable = logicW(C1, C2, C3);
                break;
        }

        return isWritable;
    }

    public void writeRawBlocks(List<RawBlockEdit> edits) throws IOException {
        int last_auth = 0, block = 0;
        this.mifareCard.connect();
        LOGGED authenticated = this.auth(last_auth);
        byte[] sector = this.readTillTheEnd(last_auth + 3, last_auth);
        this.mifareCard.close();
        this.mifareCard.connect();
        for (RawBlockEdit edit : edits) {
            if (edit.sector != last_auth){
                this.mifareCard.close();
                this.mifareCard.connect();
                authenticated = this.auth(edit.sector);
                last_auth = edit.sector;
                block = this.mifareCard.sectorToBlock(edit.sector);
                sector = this.readTillTheEnd(block + 3, edit.sector);
            }

            if (authenticated == LOGGED.UNAUTHORIZED) {
                throw new IOException("Unauthorized, wrong keys");
            }

            if (block == 0 || (edit.blockIndexInSector != 3 && !this.isBlockWritable(sector, edit.blockIndexInSector))){
                continue;
            }

            this.writeTillTheEnd(block + edit.blockIndexInSector, edit.sector, edit.data);
        }
        this.mifareCard.close();
    }

    public Customer readData() throws IOException {
        this.mifareCard.connect();
        String name = this.readName();
        String surname = this.readSurname();
        Calendar creationDate = this.readCreationDate();
        int amount = this.readAmountValue();
        this.mifareCard.close();
        return new Customer(name, surname, creationDate, amount);
    }

    private int readAmountValue() throws IOException {
        this.auth(amountSector);
        int firstBlockOfSector = this.mifareCard.sectorToBlock(amountSector);
        byte[] block = this.readTillTheEnd(firstBlockOfSector, amountSector);
        return ByteBuffer.wrap(block, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private String readName() throws IOException {
        this.auth(nameSector);
        int firstBlockOfSector = this.mifareCard.sectorToBlock(nameSector);
        byte[] nameB = new byte[48];

        for (int a = 0; a < 3; a++) {
            byte[] block = this.readTillTheEnd(firstBlockOfSector + a, nameSector);
            System.arraycopy(block, 0, nameB, 16 * a, 16);
        }
        return Utils.hexToString(nameB);
    }

    private String readSurname() throws IOException {
        this.auth(surnameSector);
        int firstBlockOfSector = this.mifareCard.sectorToBlock(surnameSector);
        byte[] surnameB = new byte[48];

        for (int a = 0; a < 3; a++) {
            byte[] block = this.readTillTheEnd(firstBlockOfSector + a, surnameSector);
            System.arraycopy(block, 0, surnameB, 16 * a, 16);
        }
        return Utils.hexToString(surnameB);
    }

    private Calendar readCreationDate() throws IOException {
        this.auth(creationDateSector);
        int firstBlockOfSector = this.mifareCard.sectorToBlock(creationDateSector);
        int year = Utils.getValueFromBlock(this.readTillTheEnd(firstBlockOfSector++, creationDateSector));
        int month = Utils.getValueFromBlock(this.readTillTheEnd(firstBlockOfSector++, creationDateSector));
        int day = Utils.getValueFromBlock(this.readTillTheEnd(firstBlockOfSector, creationDateSector));
        Calendar calendar = Calendar.getInstance();
        // Written as a 1-based month (see the admin app's writeCreationDate); Calendar.set expects 0-based.
        calendar.set(year, month - 1, day);
        return calendar;
    }

    private byte[] createResetFactorySectorTrailer(){
        byte[] chunk = new byte[16];

        System.arraycopy(MifareClassic.KEY_DEFAULT, 0, chunk, 0, Utils.keySize);
        System.arraycopy(resetFactoryAccessBits, 0, chunk, 6, 4);
        System.arraycopy(MifareClassic.KEY_DEFAULT, 0, chunk, 10, Utils.keySize);

        return chunk;
    }

    public void format() throws IOException {
        this.mifareCard.connect();
        for (int a = 0; a < 16; a++){
            this.auth(a);
            int firstBlockOfSector = this.mifareCard.sectorToBlock(a);
            this.writeTillTheEnd(firstBlockOfSector + 3, a, this.createResetFactorySectorTrailer());

            // block 0 of sector 0 is the read-only manufacturer block
            int firstDataBlock = (a == 0) ? firstBlockOfSector + 1 : firstBlockOfSector;
            for (int block = firstDataBlock; block < firstBlockOfSector + 3; block++) {
                this.writeTillTheEnd(block, a, new byte[16]);
            }

            this.writeTillTheEnd(firstBlockOfSector + 3, a, this.createResetFactorySectorTrailer());
        }
        this.mifareCard.close();
    }
}
