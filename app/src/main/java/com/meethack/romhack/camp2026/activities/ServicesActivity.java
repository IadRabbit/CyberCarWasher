package com.meethack.romhack.camp2026.activities;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.meethack.romhack.camp2026.R;
import com.meethack.romhack.camp2026.assets.Money;
import com.meethack.romhack.camp2026.assets.Services;

/**
 * Read-only showcase of the car wash catalog. Purely informational: no service here can be
 * bought or selected, it just lists what's on offer.
 */
public class ServicesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_services);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonHome).setOnClickListener(v -> finish());

        LinearLayout container = findViewById(R.id.servicesContainer);
        for (Services.Service service : Services.CATALOG) {
            container.addView(buildServiceRow(service));
            container.addView(buildDivider());
        }
        if (container.getChildCount() > 0) {
            container.removeViewAt(container.getChildCount() - 1);
        }
    }

    private LinearLayout buildServiceRow(Services.Service service) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, 14, 0, 14);

        TextView name = new TextView(this);
        name.setText(service.name);
        name.setTextColor(getColor(R.color.text_primary));
        name.setTextSize(15);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        TextView price = new TextView(this);
        price.setText(Money.format(service.price));
        price.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        price.setTextColor(getColor(R.color.neon_green));
        price.setTextSize(15);
        row.addView(price);

        return row;
    }

    private android.view.View buildDivider() {
        android.view.View divider = new android.view.View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(params);
        divider.setBackgroundResource(R.drawable.dashed_line);
        return divider;
    }
}
