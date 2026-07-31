package cafe.bluearchive.installer;

import android.app.Activity;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

final class InstallerSettingsController {
    private final Activity activity;
    private final TextView packageNameView;
    private final TextView downloadUrlView;
    private final TextView installerVersionView;
    private final TextView themeValueView;
    private final TextView languageValueView;
    private final Spinner themeSpinner;
    private final Spinner languageSpinner;

    InstallerSettingsController(Activity activity,
                                TextView packageNameView,
                                TextView downloadUrlView,
                                TextView installerVersionView,
                                TextView themeValueView,
                                TextView languageValueView,
                                Spinner themeSpinner,
                                Spinner languageSpinner) {
        this.activity = activity;
        this.packageNameView = packageNameView;
        this.downloadUrlView = downloadUrlView;
        this.installerVersionView = installerVersionView;
        this.themeValueView = themeValueView;
        this.languageValueView = languageValueView;
        this.themeSpinner = themeSpinner;
        this.languageSpinner = languageSpinner;
    }

    void bind(String packageName, String downloadUrl, String versionName, int versionCode) {
        if (packageNameView != null) {
            packageNameView.setText(packageName);
        }
        if (downloadUrlView != null) {
            downloadUrlView.setText(downloadUrl);
        }
        if (installerVersionView != null) {
            installerVersionView.setText(activity.getString(
                    R.string.settings_version_format, versionName, versionCode));
        }

        bindPreferenceSpinner(
                themeSpinner,
                themeValueView,
                R.array.theme_mode_options,
                InstallerPreferences.themeMode(activity),
                InstallerPreferences::themeModeToIndex,
                InstallerPreferences::indexToThemeMode,
                InstallerPreferences.PREF_THEME_MODE);
        bindPreferenceSpinner(
                languageSpinner,
                languageValueView,
                R.array.language_options,
                InstallerPreferences.languageMode(activity),
                InstallerPreferences::languageModeToIndex,
                InstallerPreferences::indexToLanguageMode,
                InstallerPreferences.PREF_LANGUAGE_MODE);
    }

    private void bindPreferenceSpinner(Spinner spinner, TextView valueView, int labelsResId,
                                       String currentValue, PreferenceIndexMapper toIndex,
                                       PreferenceValueMapper toValue, String preferenceKey) {
        if (spinner == null) return;

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                activity, labelsResId, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        int selectedIndex = toIndex.indexFor(currentValue);
        spinner.setSelection(selectedIndex, false);
        updateValueText(valueView, adapter, selectedIndex);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                updateValueText(valueView, adapter, position);
                String value = toValue.valueFor(position);
                if (InstallerPreferences.isStoredValue(activity, preferenceKey, value)) return;
                InstallerPreferences.save(activity, preferenceKey, value);
                activity.recreate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private static void updateValueText(TextView valueView, ArrayAdapter<CharSequence> adapter, int position) {
        if (valueView == null || position < 0 || position >= adapter.getCount()) return;
        valueView.setText(adapter.getItem(position));
    }

    private interface PreferenceIndexMapper {
        int indexFor(String value);
    }

    private interface PreferenceValueMapper {
        String valueFor(int index);
    }
}
