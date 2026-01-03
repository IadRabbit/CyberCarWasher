package com.meethack.romhack.camp2026;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

public class ReadCard extends AppCompatActivity {
	private NfcAdapter nfcAdapter;
	private TextView statusText;
	private PendingIntent pendingIntent;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_read_card);
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

		Intent adminActivity = new Intent(this, AdminActivity.class);
		startActivity(adminActivity);

//		statusText = findViewById(R.id.statusText);
//		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
//
//		if (nfcAdapter == null) {
//			statusText.setText("NFC not supported");
//			return;
//		}
//
//		if (!nfcAdapter.isEnabled()) {
//			statusText.setText("Please enable NFC");
//			return;
//		}
//		try (MifareClassic card = MifareClassic.get(getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG))) {
//			card.connect();
//			byte[] my_keys = MifareClassic.KEY_DEFAULT;
//			my_keys[0] = (byte)0xFF;
//			card.authenticateSectorWithKeyA(0, my_keys);
//			card.authenticateSectorWithKeyB(0, MifareClassic.KEY_DEFAULT);
//			byte[] payload = card.readBlock(0);
//			StringBuilder sb = new StringBuilder();
//			for (byte v : payload) {
//				sb.append(String.format("0x%X ", v & 0xFF));
//			}
//			Log.d("HEX", sb.toString());
//
//
//			Log.i("APP", "READ");
//		} catch (IOException e) {
//			Log.e("APP", "IOException while reading MifareUltralight message...", e);
//		}
	}
}