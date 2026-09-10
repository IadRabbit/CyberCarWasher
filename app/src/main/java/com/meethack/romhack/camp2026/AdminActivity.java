package com.meethack.romhack.camp2026;


import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import android.nfc.tech.MifareClassic;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;

public class AdminActivity extends AppCompatActivity {
	private NfcAdapter nfcAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_admin);
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

		nfcAdapter = NfcAdapter.getDefaultAdapter(this);

	}
	@Override
	protected void onResume() {
		super.onResume();
		Log.d("onResume", "Resume");
		Intent intent = new Intent(this, getClass());
		intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

		PendingIntent pendingIntent = PendingIntent.getActivity(
				this, 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
		);

		nfcAdapter.enableForegroundDispatch(
				this,
				pendingIntent,
				new IntentFilter[]{
						new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
				},
				new String[][]{
						new String[]{MifareClassic.class.getName()}
				}
		);
	}

	@Override
	protected void onPause() {
		super.onPause();
		nfcAdapter.disableForegroundDispatch(this);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		handleTag(intent);
	}

	private void handleTag(Intent intent) {
		Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		if (tag == null) return;

		MifareClassic mifare = MifareClassic.get(tag);

		try {
			mifare.connect();

			int sector = 1;


			byte[] testA = new byte[]{
					(byte) 0xc1, (byte)0xe5, (byte)0x1c, (byte)0x63, (byte)0xb8, (byte)0xf5
			};

			testA = new byte[]{
					(byte) 0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1
			};

			byte[] testB = new byte[]{
					//(byte) 0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF
					(byte) 0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1,
			};

			boolean auth = mifare.authenticateSectorWithKeyA(
					sector,testA
			);

			boolean auth2 = mifare.authenticateSectorWithKeyB(
					sector, testB
			);

			if (auth) {
				int block = mifare.sectorToBlock(sector);
				byte[] data = mifare.readBlock(7);

				Log.d("NFC", "Block data: " + bytesToHex(data));

				byte[] writeData2 = new byte[]{
						(byte) 0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, // key A
						(byte) 0x78, (byte)0x70, (byte)0xF8, (byte)0x69, // access bits
						(byte) 0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, // key B
				};

				// For modify access bits
				byte[] writeData = new byte[]{
						(byte) 0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
						(byte) 0xF7, (byte)0x87, (byte)0x80, (byte)0x69,
						(byte) 0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1, (byte)0xA1,
				};
				//mifare.writeBlock(7, writeData2);
			} else {
				Log.e("NFC", "Authentication failed");
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				mifare.close();
			} catch (IOException ignored) {}
		}
	}

	private String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes)
			sb.append(String.format("%02X ", b));
		return sb.toString();
	}
}