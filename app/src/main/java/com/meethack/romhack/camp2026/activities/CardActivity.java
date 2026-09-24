package com.meethack.romhack.camp2026.activities;

import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.meethack.romhack.camp2026.R;
import com.meethack.romhack.camp2026.assets.Customer;
import com.meethack.romhack.camp2026.assets.Money;
import com.meethack.romhack.camp2026.exceptions.FailedToFetchKeys;
import com.meethack.romhack.camp2026.wrappers.NfcWrapper;

import java.io.IOException;
import java.util.Locale;

/** Read-only "content of my card" screen: shows the customer's data, never writes to the tag. */
public class CardActivity extends NfcActivity {
    private static final String TAG = "CardActivity";

    private View cardPanel;
    private View groupWaiting;
    private TextView textWaitingStatus;
    private TextView textFirstNameValue;
    private TextView textLastNameValue;
    private TextView textDateValue;
    private TextView textBalanceValue;
    private TextView textUidValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_card);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        cardPanel = findViewById(R.id.cardPanel);
        groupWaiting = findViewById(R.id.groupWaiting);
        textWaitingStatus = findViewById(R.id.textWaitingStatus);
        textFirstNameValue = findViewById(R.id.textFirstNameValue);
        textLastNameValue = findViewById(R.id.textLastNameValue);
        textDateValue = findViewById(R.id.textDateValue);
        textBalanceValue = findViewById(R.id.textBalanceValue);
        textUidValue = findViewById(R.id.textUidValue);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonHome).setOnClickListener(v -> finish());
    }

    @Override
    protected void onTagDiscovered(Tag tag) {
        MifareClassic mifare = MifareClassic.get(tag);
        if (mifare == null) {
            showError(getString(R.string.card_error_not_mifare));
            return;
        }

        String uid = toHex(tag.getId());
        showStatus(getString(R.string.card_status_reading));

        startNfcAnimation();
        new Thread(() -> {
            Customer customer;
            try {
                NfcWrapper nfcWrapper = new NfcWrapper(mifare, this);
                customer = nfcWrapper.readData();
            } catch (IOException e) {
                Log.e(TAG, String.valueOf(e));
                onNfcResult(() -> {
                    showError(getString(R.string.card_error_io));
                });
                return;
            } catch (SecurityException e) {
                Log.e(TAG, "Tag out of date", e);
                onNfcResult(() -> {
                    showError(getString(R.string.card_error_moved));
                });
                return;
            } catch (FailedToFetchKeys e) {
                Log.e(TAG, "Cannot fetch keys for card", e);
                onNfcResult(() -> {
                    showError(getString(R.string.card_error_fetch_keys));
                });
                return;
            }

            onNfcResult(() -> {
                showCustomer(customer, uid);
            });
        }).start();
    }

    private void showCustomer(Customer customer, String uid) {
        textFirstNameValue.setText(customer.getName());
        textLastNameValue.setText(customer.getSurname());
        textDateValue.setText(String.format(Locale.ITALY, "%02d/%02d/%04d",
                customer.getCreationDate().get(java.util.Calendar.DAY_OF_MONTH),
                customer.getCreationDate().get(java.util.Calendar.MONTH) + 1,
                customer.getCreationDate().get(java.util.Calendar.YEAR)));
        textBalanceValue.setText(Money.format(customer.getAmount()));
        textUidValue.setText(getString(R.string.card_uid_format, uid));

        groupWaiting.setVisibility(View.GONE);
        cardPanel.setVisibility(View.VISIBLE);
    }

    private void showStatus(String status) {
        textWaitingStatus.setText(status);
        textWaitingStatus.setTextColor(getColor(R.color.text_dim));
    }

    private void showError(String message) {
        cardPanel.setVisibility(View.GONE);
        groupWaiting.setVisibility(View.VISIBLE);
        textWaitingStatus.setText(message);
        textWaitingStatus.setTextColor(getColor(R.color.neon_red));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            if (sb.length() > 0) sb.append(':');
            sb.append(String.format(Locale.US, "%02X", b));
        }
        return sb.toString();
    }
}
