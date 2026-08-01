package cafe.bluearchive.installer;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class LicensesActivity extends AppCompatActivity {
    private LicenseItem[] licenseItems;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(InstallerPreferences.applyUserConfiguration(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        SystemBars.applyAppBars(this);

        setContentView(R.layout.activity_licenses);

        MaterialToolbar toolbar = findViewById(R.id.licenses_top_app_bar);
        SystemBars.applyTopAndBottomInsets(this, toolbar, findViewById(R.id.licenses_scroll));
        toolbar.setNavigationOnClickListener(view -> finish());

        licenseItems = new LicenseItem[]{
                new LicenseItem(getString(R.string.license_item_app),
                        getString(R.string.license_mit), R.raw.license_mit),
                new LicenseItem(getString(R.string.license_item_material_components),
                        getString(R.string.license_apache_2), R.raw.license_apache_20),
                new LicenseItem(getString(R.string.license_item_androidx_activity),
                        getString(R.string.license_apache_2), R.raw.license_apache_20),
                new LicenseItem(getString(R.string.license_item_shizuku),
                        getString(R.string.license_apache_2), R.raw.license_apache_20),
                new LicenseItem(getString(R.string.license_item_libsu),
                        getString(R.string.license_apache_2), R.raw.license_apache_20)
        };
        renderLicenseList();
    }

    private void renderLicenseList() {
        LinearLayout list = findViewById(R.id.licenses_list);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < licenseItems.length; i++) {
            LicenseItem licenseItem = licenseItems[i];
            View row = inflater.inflate(R.layout.row_about_list_item, list, false);
            bindLicenseRow(row, licenseItem);
            list.addView(row);
            if (i < licenseItems.length - 1) {
                list.addView(divider());
            }
        }
    }

    private void bindLicenseRow(View row, LicenseItem licenseItem) {
        ImageView icon = row.findViewById(R.id.img_about_item_icon);
        TextView title = row.findViewById(R.id.txt_about_item_title);
        TextView subtitle = row.findViewById(R.id.txt_about_item_subtitle);
        ImageView chevron = row.findViewById(R.id.img_about_item_chevron);

        icon.setImageResource(R.drawable.ic_description_outline_24);
        icon.setColorFilter(getColor(R.color.icon_default));
        icon.setContentDescription(licenseItem.name);
        title.setText(licenseItem.name);
        subtitle.setText(licenseItem.licenseName);
        subtitle.setVisibility(View.VISIBLE);
        chevron.setVisibility(View.VISIBLE);
        row.setOnClickListener(view -> showLicenseDetail(licenseItem));
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.card_stroke));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.setMarginStart(dp(56));
        divider.setLayoutParams(params);
        return divider;
    }

    private void showLicenseDetail(LicenseItem licenseItem) {
        String text = readRawText(licenseItem.rawResId);
        new MaterialAlertDialogBuilder(this)
                .setTitle(licenseItem.name)
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String readRawText(int resId) {
        StringBuilder builder = new StringBuilder();
        try {
            InputStream is = getResources().openRawResource(resId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            reader.close();
        } catch (IOException ignored) {
            return builder.length() > 0 ? builder.toString() : "";
        }
        return builder.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class LicenseItem {
        final String name;
        final String licenseName;
        final int rawResId;

        LicenseItem(String name, String licenseName, int rawResId) {
            this.name = name;
            this.licenseName = licenseName;
            this.rawResId = rawResId;
        }
    }
}
