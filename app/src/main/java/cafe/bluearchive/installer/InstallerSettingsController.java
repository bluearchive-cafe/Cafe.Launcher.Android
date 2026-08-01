package cafe.bluearchive.installer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

final class InstallerSettingsController {

    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;

    private final Activity activity;
    private final View themeRow;
    private final View languageRow;
    private final View installModeRow;
    private final View shizukuStatusRow;
    private final View rootStatusRow;
    private final View aboutRow;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int rootStatusGeneration;
    private boolean destroyed;

    // Holds state for a pending Shizuku permission request.
    private InstallMode pendingShizukuMode;
    private Shizuku.OnRequestPermissionResultListener shizukuPermissionListener;
    private Shizuku.OnBinderReceivedListener binderReceivedListener;
    private Shizuku.OnBinderDeadListener binderDeadListener;

    InstallerSettingsController(Activity activity,
                                View themeRow,
                                View languageRow,
                                View installModeRow,
                                View shizukuStatusRow,
                                View rootStatusRow,
                                View aboutRow) {
        this.activity = activity;
        this.themeRow = themeRow;
        this.languageRow = languageRow;
        this.installModeRow = installModeRow;
        this.shizukuStatusRow = shizukuStatusRow;
        this.rootStatusRow = rootStatusRow;
        this.aboutRow = aboutRow;
    }

    void bind() {
        destroyed = false;
        bindThemeRow();
        bindLanguageRow();
        bindInstallModeRow();
        bindShizukuStatusRow();
        bindRootStatusRow();
        bindAboutRow();
    }

    /**
     * Must be called from {@link Activity#onDestroy()} to clean up Shizuku listeners.
     */
    void destroy() {
        destroyed = true;
        rootStatusGeneration++;
        clearPendingShizukuPermission();
        if (binderReceivedListener != null) {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            binderReceivedListener = null;
        }
        if (binderDeadListener != null) {
            Shizuku.removeBinderDeadListener(binderDeadListener);
            binderDeadListener = null;
        }
    }

    private void bindThemeRow() {
        if (themeRow == null) return;

        ImageView icon = themeRow.findViewById(R.id.settingIcon);
        TextView title = themeRow.findViewById(R.id.settingTitle);
        TextView subtitle = themeRow.findViewById(R.id.settingSubtitle);
        ImageView chevron = themeRow.findViewById(R.id.settingChevron);

        icon.setImageResource(R.drawable.ic_brightness_medium_24);
        icon.setContentDescription(activity.getString(R.string.settings_theme_label));
        icon.setVisibility(View.VISIBLE);
        title.setText(R.string.settings_theme_label);
        updateThemeSubtitle(subtitle);
        chevron.setVisibility(View.GONE);

        themeRow.setOnClickListener(view -> showThemeMenu());
    }

    private void updateThemeSubtitle(TextView subtitle) {
        if (subtitle == null) return;
        String value = InstallerPreferences.themeMode(activity);
        if (InstallerPreferences.THEME_DARK.equals(value)) {
            subtitle.setText(R.string.settings_theme_dark);
        } else if (InstallerPreferences.THEME_LIGHT.equals(value)) {
            subtitle.setText(R.string.settings_theme_light);
        } else {
            subtitle.setText(R.string.settings_theme_system);
        }
    }

    private void bindLanguageRow() {
        if (languageRow == null) return;

        ImageView icon = languageRow.findViewById(R.id.settingIcon);
        TextView title = languageRow.findViewById(R.id.settingTitle);
        TextView subtitle = languageRow.findViewById(R.id.settingSubtitle);
        ImageView chevron = languageRow.findViewById(R.id.settingChevron);

        icon.setImageResource(R.drawable.ic_language_24);
        icon.setContentDescription(activity.getString(R.string.settings_language_label));
        icon.setVisibility(View.VISIBLE);
        title.setText(R.string.settings_language_label);
        updateLanguageSubtitle(subtitle);
        chevron.setVisibility(View.GONE);

        languageRow.setOnClickListener(view -> showLanguageMenu());
    }

    private void updateLanguageSubtitle(TextView subtitle) {
        if (subtitle == null) return;
        String value = InstallerPreferences.languageMode(activity);
        if (InstallerPreferences.LANGUAGE_ZH_HANS.equals(value)) {
            subtitle.setText(R.string.settings_language_zh_hans);
        } else if (InstallerPreferences.LANGUAGE_ZH_HANT.equals(value)) {
            subtitle.setText(R.string.settings_language_zh_hant);
        } else if (InstallerPreferences.LANGUAGE_EN.equals(value)) {
            subtitle.setText(R.string.settings_language_en);
        } else {
            subtitle.setText(R.string.settings_language_system);
        }
    }

    private void bindInstallModeRow() {
        if (installModeRow == null) return;

        ImageView icon = installModeRow.findViewById(R.id.settingIcon);
        TextView title = installModeRow.findViewById(R.id.settingTitle);
        TextView subtitle = installModeRow.findViewById(R.id.settingSubtitle);
        ImageView chevron = installModeRow.findViewById(R.id.settingChevron);

        icon.setImageResource(R.drawable.ic_system_update_24);
        icon.setContentDescription(activity.getString(R.string.settings_install_mode_label));
        icon.setVisibility(View.VISIBLE);
        title.setText(R.string.settings_install_mode_label);
        updateInstallModeSubtitle(subtitle);
        chevron.setVisibility(View.GONE);

        // Binder lifecycle — when Shizuku dies while settings is open, clear
        // any pending permission state so the UI stays consistent.
        binderReceivedListener = () -> mainHandler.post(this::updateShizukuStatusRow);
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);

        binderDeadListener = () -> {
            mainHandler.post(() -> {
                clearPendingShizukuPermission();
                updateInstallModeSubtitle(
                        installModeRow.findViewById(R.id.settingSubtitle));
                updateShizukuStatusRow();
            });
        };
        Shizuku.addBinderDeadListener(binderDeadListener);

        installModeRow.setOnClickListener(view -> showInstallModeMenu());
    }

    private void updateInstallModeSubtitle(TextView subtitle) {
        if (subtitle == null) return;
        InstallMode mode = InstallerPreferences.installMode(activity);
        if (mode == InstallMode.SHIZUKU) {
            subtitle.setText(R.string.settings_install_mode_shizuku);
        } else if (mode == InstallMode.ROOT) {
            subtitle.setText(R.string.settings_install_mode_root);
        } else {
            subtitle.setText(R.string.settings_install_mode_system);
        }
    }

    private void bindShizukuStatusRow() {
        if (shizukuStatusRow == null) return;

        ImageView icon = shizukuStatusRow.findViewById(R.id.settingIcon);
        TextView title = shizukuStatusRow.findViewById(R.id.settingTitle);
        ImageView chevron = shizukuStatusRow.findViewById(R.id.settingChevron);

        icon.setImageResource(R.drawable.ic_settings_24);
        icon.setContentDescription(activity.getString(R.string.settings_shizuku_status_label));
        icon.setVisibility(View.VISIBLE);
        title.setText(R.string.settings_shizuku_status_label);
        chevron.setVisibility(View.GONE);
        shizukuStatusRow.setOnClickListener(null);
        updateShizukuStatusRow();
    }

    private void updateShizukuStatusRow() {
        if (shizukuStatusRow == null) return;
        TextView subtitle = shizukuStatusRow.findViewById(R.id.settingSubtitle);
        if (subtitle == null) return;

        try {
            if (!Shizuku.pingBinder()) {
                subtitle.setText(R.string.settings_status_shizuku_not_running);
            } else if (Shizuku.isPreV11()) {
                subtitle.setText(R.string.settings_status_shizuku_outdated);
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                subtitle.setText(R.string.settings_status_available);
            } else {
                subtitle.setText(R.string.settings_status_permission_required);
            }
        } catch (Exception e) {
            subtitle.setText(R.string.settings_status_unavailable);
        }
    }

    private void bindRootStatusRow() {
        if (rootStatusRow == null) return;

        ImageView icon = rootStatusRow.findViewById(R.id.settingIcon);
        TextView title = rootStatusRow.findViewById(R.id.settingTitle);
        TextView subtitle = rootStatusRow.findViewById(R.id.settingSubtitle);
        ImageView chevron = rootStatusRow.findViewById(R.id.settingChevron);

        icon.setImageResource(R.drawable.ic_tag_24);
        icon.setContentDescription(activity.getString(R.string.settings_root_status_label));
        icon.setVisibility(View.VISIBLE);
        title.setText(R.string.settings_root_status_label);
        subtitle.setText(R.string.settings_status_checking);
        chevron.setVisibility(View.GONE);
        rootStatusRow.setOnClickListener(null);
        refreshRootStatusRow();
    }

    private void refreshRootStatusRow() {
        if (rootStatusRow == null) return;
        int generation = ++rootStatusGeneration;
        TextView subtitle = rootStatusRow.findViewById(R.id.settingSubtitle);
        if (subtitle != null) {
            subtitle.setText(R.string.settings_status_checking);
        }

        new Thread(() -> {
            boolean isRoot = RootInstallBackend.isRootAvailable();
            mainHandler.post(() -> {
                if (destroyed || generation != rootStatusGeneration || rootStatusRow == null) return;
                TextView rootSubtitle = rootStatusRow.findViewById(R.id.settingSubtitle);
                if (rootSubtitle == null) return;
                rootSubtitle.setText(isRoot
                        ? R.string.settings_status_available
                        : R.string.settings_status_unavailable);
            });
        }, "Root-status").start();
    }

    private void refreshStatusRows() {
        updateShizukuStatusRow();
        refreshRootStatusRow();
    }

    private void bindAboutRow() {
        if (aboutRow == null) return;

        ImageView icon = aboutRow.findViewById(R.id.settingIcon);
        TextView title = aboutRow.findViewById(R.id.settingTitle);
        TextView subtitle = aboutRow.findViewById(R.id.settingSubtitle);
        ImageView chevron = aboutRow.findViewById(R.id.settingChevron);

        icon.setImageResource(R.drawable.ic_info_24);
        icon.setContentDescription(activity.getString(R.string.setting_about));
        icon.setVisibility(View.VISIBLE);
        title.setText(R.string.setting_about);
        subtitle.setText(R.string.setting_about_subtitle);
        chevron.setVisibility(View.VISIBLE);

        aboutRow.setOnClickListener(view ->
                activity.startActivity(new Intent(activity, AboutActivity.class)));
    }

    // ── Theme popup ──────────────────────────────────────────────

    private void showThemeMenu() {
        String current = InstallerPreferences.themeMode(activity);
        String[] labels = {
                activity.getString(R.string.settings_theme_system),
                activity.getString(R.string.settings_theme_dark),
                activity.getString(R.string.settings_theme_light)
        };
        String[] values = {
                InstallerPreferences.THEME_SYSTEM,
                InstallerPreferences.THEME_DARK,
                InstallerPreferences.THEME_LIGHT
        };
        showMenu(themeRow, current, labels, values, value -> {
            InstallerPreferences.save(activity, InstallerPreferences.PREF_THEME_MODE, value);
            TextView subtitle = themeRow.findViewById(R.id.settingSubtitle);
            updateThemeSubtitle(subtitle);
            activity.recreate();
        });
    }

    // ── Language popup ───────────────────────────────────────────

    private void showLanguageMenu() {
        String current = InstallerPreferences.languageMode(activity);
        String[] labels = {
                activity.getString(R.string.settings_language_system),
                activity.getString(R.string.settings_language_zh_hans),
                activity.getString(R.string.settings_language_zh_hant),
                activity.getString(R.string.settings_language_en)
        };
        String[] values = {
                InstallerPreferences.LANGUAGE_SYSTEM,
                InstallerPreferences.LANGUAGE_ZH_HANS,
                InstallerPreferences.LANGUAGE_ZH_HANT,
                InstallerPreferences.LANGUAGE_EN
        };
        showMenu(languageRow, current, labels, values, value -> {
            InstallerPreferences.save(activity, InstallerPreferences.PREF_LANGUAGE_MODE, value);
            TextView subtitle = languageRow.findViewById(R.id.settingSubtitle);
            updateLanguageSubtitle(subtitle);
            activity.recreate();
        });
    }

    // ── Install mode popup ───────────────────────────────────────

    private void showInstallModeMenu() {
        InstallMode current = InstallerPreferences.installMode(activity);
        String currentValue = InstallerPreferences.installModeToString(current);
        String[] labels = {
                activity.getString(R.string.settings_install_mode_system),
                activity.getString(R.string.settings_install_mode_shizuku),
                activity.getString(R.string.settings_install_mode_root)
        };
        String[] values = {
                InstallerPreferences.INSTALL_MODE_SYSTEM,
                InstallerPreferences.INSTALL_MODE_SHIZUKU,
                InstallerPreferences.INSTALL_MODE_ROOT
        };
        showMenu(installModeRow, currentValue, labels, values, value -> {
            InstallMode selectedMode = InstallerPreferences.installModeFromString(value);
            if (selectedMode == current) return;

            if (selectedMode == InstallMode.SHIZUKU) {
                validateShizukuAndSave(current, selectedMode);
            } else if (selectedMode == InstallMode.ROOT) {
                validateRootAndSave(current, selectedMode);
            } else {
                persistAndRecreate(selectedMode);
            }
        });
    }

    private void validateShizukuAndSave(InstallMode currentMode, InstallMode selectedMode) {
        try {
            if (!Shizuku.pingBinder()) {
                showUnavailableAndRevert(currentMode,
                        activity.getString(R.string.settings_install_mode_shizuku_not_running));
                return;
            }
            if (Shizuku.isPreV11()) {
                showUnavailableAndRevert(currentMode,
                        activity.getString(R.string.settings_install_mode_shizuku_outdated));
                return;
            }
            int permission = Shizuku.checkSelfPermission();
            if (permission == PackageManager.PERMISSION_GRANTED) {
                persistAndRecreate(selectedMode);
                return;
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                showUnavailableAndRevert(currentMode,
                        activity.getString(R.string.settings_install_mode_shizuku_permission_denied));
                return;
            }

            pendingShizukuMode = selectedMode;
            if (shizukuPermissionListener != null) {
                Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
            }
            shizukuPermissionListener = (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_PERMISSION_REQUEST_CODE) return;
                mainHandler.post(() -> {
                    InstallMode mode = pendingShizukuMode;
                    clearPendingShizukuPermission();
                    updateShizukuStatusRow();
                    if (mode == null) return;
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        persistAndRecreate(mode);
                    } else {
                        showUnavailableAndRevert(currentMode,
                                activity.getString(R.string.settings_install_mode_shizuku_permission_denied));
                    }
                });
            };
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
        } catch (Exception e) {
            clearPendingShizukuPermission();
            showUnavailableAndRevert(currentMode,
                    activity.getString(R.string.settings_install_mode_shizuku_request_failed));
        }
    }

    private void clearPendingShizukuPermission() {
        if (shizukuPermissionListener != null) {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
            shizukuPermissionListener = null;
        }
        pendingShizukuMode = null;
    }

    private void validateRootAndSave(InstallMode currentMode, InstallMode selectedMode) {
        // Perform the root check off the main thread; libsu may show a superuser prompt.
        new Thread(() -> {
            boolean isRoot = RootInstallBackend.isRootAvailable();
            mainHandler.post(() -> {
                if (isRoot) {
                    persistAndRecreate(selectedMode);
                } else {
                    refreshRootStatusRow();
                    showUnavailableAndRevert(currentMode,
                            activity.getString(R.string.settings_install_mode_unavailable_root));
                }
            });
        }, "Root-check").start();
    }

    private void showUnavailableAndRevert(InstallMode currentMode, String message) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.settings_install_mode_unavailable_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    updateInstallModeSubtitle(
                            installModeRow.findViewById(R.id.settingSubtitle));
                })
                .setOnDismissListener(dialog -> {
                    updateInstallModeSubtitle(
                            installModeRow.findViewById(R.id.settingSubtitle));
                })
                .show();
    }

    private void persistAndRecreate(InstallMode mode) {
        String value = InstallerPreferences.installModeToString(mode);
        if (InstallerPreferences.isStoredValue(activity,
                InstallerPreferences.PREF_INSTALL_MODE, value)) {
            return;
        }
        InstallerPreferences.save(activity, InstallerPreferences.PREF_INSTALL_MODE, value);
        updateInstallModeSubtitle(installModeRow.findViewById(R.id.settingSubtitle));
        refreshStatusRows();
        activity.recreate();
    }

    // ── Popup menu ───────────────────────────────────────────────

    @SuppressLint("InflateParams")
    private void showMenu(View anchor, String current, String[] labels, String[] values,
                          final ChoiceHandler handler) {
        Context context = activity;
        LinearLayout menuView = (LinearLayout) LayoutInflater.from(context)
                .inflate(R.layout.popup_setting_menu, null, false);
        PopupWindow popupWindow = new PopupWindow(menuView,
                dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(
                new ColorDrawable(context.getColor(R.color.surface_elevation_8)));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(dp(8));

        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            TextView item = (TextView) LayoutInflater.from(context)
                    .inflate(R.layout.row_setting_menu_item, menuView, false);
            item.setText(labels[i]);
            if (value.equals(current)) {
                selectedIndex = i;
                item.setBackgroundResource(R.drawable.setting_menu_item_selected);
                item.setTextColor(context.getColor(R.color.primary));
            }
            item.setOnClickListener(view -> {
                popupWindow.dismiss();
                if (!value.equals(current)) {
                    handler.onChoice(value);
                }
            });
            menuView.addView(item);
        }

        int verticalOffset = -anchor.getHeight()
                - dp(8)
                - (selectedIndex * dp(48))
                + ((anchor.getHeight() - dp(48)) / 2);
        int horizontalOffset = dp(24) + dp(16) + dp(16); // icon + margin, align popup text with row title
        popupWindow.showAsDropDown(anchor, horizontalOffset, verticalOffset);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private interface ChoiceHandler {
        void onChoice(String value);
    }
}
