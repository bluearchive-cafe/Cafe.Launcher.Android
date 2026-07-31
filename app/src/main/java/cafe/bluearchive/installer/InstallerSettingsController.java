package cafe.bluearchive.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

final class InstallerSettingsController {

    private final Activity activity;
    private final TextView packageNameView;
    private final TextView downloadUrlView;
    private final TextView installerVersionView;
    private final TextView themeValueView;
    private final TextView languageValueView;
    private final Spinner themeSpinner;
    private final Spinner languageSpinner;
    private final TextView installModeValueView;
    private final Spinner installModeSpinner;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Holds state for a pending Shizuku permission request.
    private InstallMode pendingShizukuMode;
    private int pendingShizukuPosition;
    private ArrayAdapter<CharSequence> pendingShizukuAdapter;
    private Shizuku.OnRequestPermissionResultListener shizukuPermissionListener;
    private Shizuku.OnBinderReceivedListener binderReceivedListener;
    private Shizuku.OnBinderDeadListener binderDeadListener;

    InstallerSettingsController(Activity activity,
                                TextView packageNameView,
                                TextView downloadUrlView,
                                TextView installerVersionView,
                                TextView themeValueView,
                                TextView languageValueView,
                                Spinner themeSpinner,
                                Spinner languageSpinner,
                                TextView installModeValueView,
                                Spinner installModeSpinner) {
        this.activity = activity;
        this.packageNameView = packageNameView;
        this.downloadUrlView = downloadUrlView;
        this.installerVersionView = installerVersionView;
        this.themeValueView = themeValueView;
        this.languageValueView = languageValueView;
        this.themeSpinner = themeSpinner;
        this.languageSpinner = languageSpinner;
        this.installModeValueView = installModeValueView;
        this.installModeSpinner = installModeSpinner;
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

        bindInstallModeSpinner();
    }

    /**
     * Must be called from {@link Activity#onDestroy()} to clean up Shizuku listeners.
     */
    void destroy() {
        if (shizukuPermissionListener != null) {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
            shizukuPermissionListener = null;
        }
        if (binderReceivedListener != null) {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            binderReceivedListener = null;
        }
        if (binderDeadListener != null) {
            Shizuku.removeBinderDeadListener(binderDeadListener);
            binderDeadListener = null;
        }
    }

    private void bindInstallModeSpinner() {
        if (installModeSpinner == null) return;

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                activity, R.array.install_mode_options,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        installModeSpinner.setAdapter(adapter);

        InstallMode currentMode = InstallerPreferences.installMode(activity);
        int selectedIndex = InstallerPreferences.installModeToIndex(currentMode);
        installModeSpinner.setSelection(selectedIndex, false);
        updateInstallModeValueText(adapter, selectedIndex);

        // Binder lifecycle — when Shizuku dies while settings is open, clear
        // any pending permission state so the UI stays consistent.
        binderReceivedListener = () -> {
            // Shizuku reconnected; no action needed — next mode selection
            // will re-validate availability.
        };
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);

        binderDeadListener = () -> {
            mainHandler.post(() -> {
                if (shizukuPermissionListener != null) {
                    Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
                    shizukuPermissionListener = null;
                }
                pendingShizukuMode = null;
                pendingShizukuAdapter = null;
            });
        };
        Shizuku.addBinderDeadListener(binderDeadListener);

        installModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean isInitialSelection = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view,
                                       int position, long id) {
                if (isInitialSelection) {
                    isInitialSelection = false;
                    return;
                }

                InstallMode selectedMode = InstallerPreferences.indexToInstallMode(position);
                InstallMode currentMode = InstallerPreferences.installMode(activity);

                if (selectedMode == currentMode) return;

                // Validate availability for privileged modes before persisting.
                if (selectedMode == InstallMode.SHIZUKU) {
                    validateShizukuAndSave(currentMode, selectedMode, position, adapter);
                } else if (selectedMode == InstallMode.ROOT) {
                    validateRootAndSave(currentMode, selectedMode, position, adapter);
                } else {
                    // System mode is always available.
                    persistAndRecreate(selectedMode, position, adapter);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void validateShizukuAndSave(InstallMode currentMode, InstallMode selectedMode,
                                        int position, ArrayAdapter<CharSequence> adapter) {
        try {
            if (!Shizuku.pingBinder()) {
                showUnavailableAndRevert(currentMode,
                        activity.getString(R.string.settings_install_mode_unavailable_shizuku));
                return;
            }
            if (Shizuku.isPreV11()) {
                showUnavailableAndRevert(currentMode,
                        activity.getString(R.string.settings_install_mode_unavailable_shizuku));
                return;
            }
            int permission = Shizuku.checkSelfPermission();
            if (permission == PackageManager.PERMISSION_GRANTED) {
                persistAndRecreate(selectedMode, position, adapter);
            } else {
                // Register a listener that fires when the user grants permission
                // via the Shizuku permission dialog.
                pendingShizukuMode = selectedMode;
                pendingShizukuPosition = position;
                pendingShizukuAdapter = adapter;

                if (shizukuPermissionListener != null) {
                    Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
                }
                shizukuPermissionListener = (requestCode, grantResult) -> {
                    mainHandler.post(() -> {
                        if (pendingShizukuMode == null) return;
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            persistAndRecreate(pendingShizukuMode,
                                    pendingShizukuPosition, pendingShizukuAdapter);
                        } else {
                            showUnavailableAndRevert(currentMode,
                                    activity.getString(R.string.settings_install_mode_unavailable_shizuku));
                        }
                        pendingShizukuMode = null;
                        pendingShizukuAdapter = null;
                    });
                };
                Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
                Shizuku.requestPermission(0);
            }
        } catch (Exception e) {
            showUnavailableAndRevert(currentMode,
                    activity.getString(R.string.settings_install_mode_unavailable_shizuku));
        }
    }

    private void validateRootAndSave(InstallMode currentMode, InstallMode selectedMode,
                                     int position, ArrayAdapter<CharSequence> adapter) {
        // Perform a quick root check using libsu.
        new Thread(() -> {
            try {
                com.topjohnwu.superuser.Shell shell =
                        com.topjohnwu.superuser.Shell.getCachedShell();
                boolean isRoot;
                if (shell != null) {
                    isRoot = shell.isRoot();
                } else {
                    com.topjohnwu.superuser.Shell.Result result =
                            com.topjohnwu.superuser.Shell.cmd("id").exec();
                    isRoot = result.isSuccess() && result.getOut().contains("uid=0");
                }

                mainHandler.post(() -> {
                    if (isRoot) {
                        persistAndRecreate(selectedMode, position, adapter);
                    } else {
                        showUnavailableAndRevert(currentMode,
                                activity.getString(R.string.settings_install_mode_unavailable_root));
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showUnavailableAndRevert(currentMode,
                            activity.getString(R.string.settings_install_mode_unavailable_root));
                });
            }
        }).start();
    }

    private void showUnavailableAndRevert(InstallMode currentMode, String message) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.settings_install_mode_unavailable_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int revertIndex = InstallerPreferences.installModeToIndex(currentMode);
                    installModeSpinner.setSelection(revertIndex, false);
                })
                .setOnDismissListener(dialog -> {
                    int revertIndex = InstallerPreferences.installModeToIndex(currentMode);
                    installModeSpinner.setSelection(revertIndex, false);
                })
                .show();
    }

    private void persistAndRecreate(InstallMode mode, int position,
                                    ArrayAdapter<CharSequence> adapter) {
        String value = InstallerPreferences.installModeToString(mode);
        if (InstallerPreferences.isStoredValue(activity, InstallerPreferences.PREF_INSTALL_MODE, value)) {
            return;
        }
        InstallerPreferences.save(activity, InstallerPreferences.PREF_INSTALL_MODE, value);
        updateInstallModeValueText(adapter, position);
        activity.recreate();
    }

    private void updateInstallModeValueText(ArrayAdapter<CharSequence> adapter, int position) {
        if (installModeValueView == null || position < 0 || position >= adapter.getCount()) return;
        installModeValueView.setText(adapter.getItem(position));
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
            private boolean isInitialSelection = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view,
                                       int position, long id) {
                if (isInitialSelection) {
                    isInitialSelection = false;
                    return;
                }
                updateValueText(valueView, adapter, position);
                String value = toValue.valueFor(position);
                if (InstallerPreferences.isStoredValue(activity, preferenceKey, value)) return;
                InstallerPreferences.save(activity, preferenceKey, value);
                activity.recreate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private static void updateValueText(TextView valueView, ArrayAdapter<CharSequence> adapter,
                                        int position) {
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
