package cafe.bluearchive.installer;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipException;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

/**
 * Downloads the latest APKS from the CDN, extracts its split APKs, and installs
 * them via {@link PackageInstaller.Session}.
 *
 * <p>Flow:
 * <ol>
 *   <li>Pre-flight: storage check, existing version
 *   <li>Download APKS from CDN with progress
 *   <li>Extract split APKs from the ZIP container
 *   <li>Confirm install / update
 *   <li>Permission (unknown sources, Android 8+)
 *   <li>Install with per-split progress + ETA
 *   <li>Result — launch, retry, or self-cleanup
 * </ol>
 */
public final class InstallerActivity extends ComponentActivity {

    private static final String TAG = "InstallerActivity";
    private static final String PREF_INSTALL_CALLBACK_TOKEN = "install_callback_token";

    // Notification
    private static final String CHANNEL_ID = "install_progress";
    private static final int NOTIFY_INSTALL = 100;

    // Injected by the build system
    private static final String GAME_PACKAGE_NAME = BuildConfig.GAME_PACKAGE_NAME;
    private static final String GAME_ACTIVITY_NAME = BuildConfig.GAME_ACTIVITY_NAME;
    private static final String APKS_MANIFEST_URL = BuildConfig.APKS_MANIFEST_URL;
    private static final String RELEASE_MANIFEST_PUBLIC_KEY = BuildConfig.RELEASE_MANIFEST_PUBLIC_KEY;

    // CDN
    private static final String APKS_DOWNLOAD_URL = BuildConfig.APKS_DOWNLOAD_URL;

    // Tuning
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 200;
    private static final long MIN_FREE_SPACE_FACTOR = 2;
    private static final int DOWNLOAD_BUFFER_SIZE = 64 * 1024; // 64 KiB

    private static final String APKS_CACHE_FILE_NAME = "latest.apks";

    // ── UI state machine ───────────────────────────────────────

    private enum UiState {
        CHECKING,
        READY_TO_DOWNLOAD,
        ALREADY_INSTALLED,
        DOWNLOADING,
        CONFIRM_INSTALL,
        CONFIRM_UPDATE,
        NETWORK_ERROR,
        CORRUPTED,
        PERMISSION_DENIED,
        INSTALLING,
        CONFIRM_SYSTEM,
        SUCCESS,
        FAILED,
        STORAGE_LOW
    }

    // ── Widgets ────────────────────────────────────────────────

    private ImageView statusIcon;
    private TextView titleText;
    private TextView messageText;
    private TextView supportingText;
    private ProgressBar progressBar;
    private ProgressBar indeterminateBar;
    private TextView splitLabel;
    private TextView etaLabel;
    private TextView errorDetail;
    private Button primaryButton;
    private Button secondaryButton;
    private Button tertiaryButton;
    private MaterialToolbar topAppBar;
    private BottomNavigationView bottomNavigation;
    private NavigationView navigationView;
    private View installContent;
    private View helpContent;
    private View resourcePanelContent;
    private View settingsContent;
    private View settingsThemeRow;
    private View settingsLanguageRow;
    private View settingsInstallModeRow;
    private View settingsShizukuStatusRow;
    private View settingsRootStatusRow;
    private View settingsAboutRow;

    // ── State ──────────────────────────────────────────────────

    private UiState currentState = UiState.CHECKING;
    private boolean installing;
    private PackageInfo existingPackage;
    private ApksArchive apksArchive;
    private ReleaseManifest releaseManifest;
    private long totalInstallBytes;
    private boolean detailExpanded;
    private boolean downloadProgressDeterminate;
    private long lastDownloadNotificationAt;
    private String lastFailureDetail;
    private String installModeFallbackDetail;
    private int selectedNavItemId = R.id.nav_install;
    private boolean destroyed;

    // Download / extract intermediates
    private File apksFile;
    private final AtomicBoolean operationCancelled = new AtomicBoolean(false);

    // Install backend
    private SystemInstallBackend systemBackend;

    // Settings controller (stored so we can call destroy() for listener cleanup)
    private InstallerSettingsController settingsController;
    private ResourcePanelController resourcePanelController;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private NotificationManager notificationManager;
    private String installCallbackToken;
    private ActivityResultLauncher<Intent> unknownSourcesLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    // ── lifecycle ──────────────────────────────────────────────

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(InstallerPreferences.applyUserConfiguration(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        destroyed = false;
        super.onCreate(savedInstanceState);

        // Draw behind system bars and apply their insets to the app chrome so
        // fixed-height bars keep their content out of status/navigation areas.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        SystemBars.applyAppBars(this);

        setContentView(R.layout.activity_installer);
        bindViews();
        applySystemBarInsets();
        bindNavigation();
        registerActivityResultLaunchers();
        createNotificationChannel();

        if (savedInstanceState != null) {
            installing = savedInstanceState.getBoolean("installing", false);
            detailExpanded = savedInstanceState.getBoolean("detailExpanded", false);
            lastFailureDetail = savedInstanceState.getString("lastFailureDetail");
            installModeFallbackDetail = savedInstanceState.getString("installModeFallbackDetail");
            selectedNavItemId = savedInstanceState.getInt("selectedNavItemId", R.id.nav_install);
            currentState = uiStateFromName(savedInstanceState.getString("currentState"));
        }

        // Install backend — used by handleInstallStatus for system mode callbacks.
        systemBackend = (SystemInstallBackend)
                InstallBackendFactory.create(this, InstallMode.SYSTEM);

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        showSection(selectedNavItemId);

        if (handleInstallStatus(getIntent())) {
            return;
        }
        if (savedInstanceState != null && selectedNavItemId != R.id.nav_install) {
            setUiState(currentState);
            return;
        }
        runPreflightChecks();
    }

    private void registerActivityResultLaunchers() {
        unknownSourcesLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                            || getPackageManager().canRequestPackageInstalls()) {
                        maybeRequestNotificationPermissionThenInstall();
                    } else {
                        installing = false;
                        setUiState(UiState.PERMISSION_DENIED);
                    }
                });

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) {
                        lastFailureDetail = getString(R.string.notification_permission_denied_detail);
                    }
                    startInstallSession();
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("installing", installing);
        outState.putBoolean("detailExpanded", detailExpanded);
        outState.putString("lastFailureDetail", lastFailureDetail);
        outState.putString("installModeFallbackDetail", installModeFallbackDetail);
        outState.putInt("selectedNavItemId", selectedNavItemId);
        outState.putString("currentState", currentState.name());
    }

    private UiState uiStateFromName(String name) {
        if (name == null) return UiState.CHECKING;
        try {
            return UiState.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return UiState.CHECKING;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // If installing and user leaves, show persistent notification
        if (installing) {
            showOperationNotification(
                    getString(R.string.notif_title),
                    getString(R.string.notif_install_message),
                    progressBar.getProgress(),
                    false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleInstallStatus(intent);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
        if (settingsController != null) {
            settingsController.destroy();
        }
        if (resourcePanelController != null) {
            resourcePanelController.destroy();
        }
        InstallBackendFactory.destroy();
        if (!installing) {
            cleanupTempFiles();
        }
        // Remove notification when installation is no longer active
        if (!installing && notificationManager != null) {
            notificationManager.cancel(NOTIFY_INSTALL);
        }
    }

    private void postToUi(Runnable runnable) {
        mainHandler.post(() -> {
            if (!destroyed) {
                runnable.run();
            }
        });
    }

    // ── notification channel ─────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notif_channel_desc));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void showOperationNotification(String title, String message,
                                           int progress, boolean indeterminate) {
        Intent intent = new Intent(this, InstallerActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, intent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(message)
                .setOngoing(true)
                .setContentIntent(pending)
                .setProgress(100, indeterminate ? 0 : progress, indeterminate);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationManager.notify(NOTIFY_INSTALL, builder.build());
    }

    private void cancelInstallNotification() {
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFY_INSTALL);
        }
    }

    // ── cancel confirmation ──────────────────────────────────────

    private void confirmCancel() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_confirm_title)
                .setMessage(R.string.cancel_confirm_message)
                .setPositiveButton(R.string.cancel_confirm_yes, (d, w) -> {
                    operationCancelled.set(true);
                    cancelActiveInstall();
                    abandonActiveSession();
                    installing = false;
                    cleanupTempFiles();
                    finishAndRemoveTask();
                })
                .setNegativeButton(R.string.cancel_confirm_no, null)
                .show();
    }

    /**
     * Cancels any in-flight privileged install by interrupting the install
     * thread and killing the shell process.
     */
    private void cancelActiveInstall() {
        ShizukuInstallBackend shizuku = InstallBackendFactory.getShizukuBackendIfPresent();
        if (shizuku != null) {
            shizuku.cancel();
        }
    }

    // ── crossfade helpers ────────────────────────────────────────

    private static final int CROSSFADE_DURATION_MS = 200;

    /**
     * Crossfades from indeterminate spinner to determinate progress bar
     * (or vice versa) with a short fade animation.
     */
    private void crossfadeProgress(ProgressBar from, ProgressBar to) {
        if (!areSystemAnimationsEnabled()) {
            if (from != null) from.setVisibility(View.GONE);
            to.setAlpha(1f);
            to.setVisibility(View.VISIBLE);
            return;
        }

        to.setAlpha(0f);
        to.setVisibility(View.VISIBLE);
        to.animate()
                .alpha(1f)
                .setDuration(CROSSFADE_DURATION_MS)
                .setListener(null);

        if (from != null && from.getVisibility() == View.VISIBLE) {
            from.animate()
                    .alpha(0f)
                    .setDuration(CROSSFADE_DURATION_MS)
                    .withEndAction(() -> from.setVisibility(View.GONE))
                    .setListener(null);
        }
    }

    private boolean areSystemAnimationsEnabled() {
        try {
            return Global.getFloat(getContentResolver(), Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Exception ignored) {
            return true;
        }
    }

    // ── view binding ───────────────────────────────────────────

    private void bindViews() {
        statusIcon = findViewById(R.id.statusIcon);
        titleText = findViewById(R.id.titleText);
        messageText = findViewById(R.id.messageText);
        supportingText = findViewById(R.id.supportingText);
        progressBar = findViewById(R.id.progressBar);
        indeterminateBar = findViewById(R.id.indeterminateBar);
        splitLabel = findViewById(R.id.splitLabel);
        etaLabel = findViewById(R.id.etaLabel);
        errorDetail = findViewById(R.id.errorDetail);
        primaryButton = findViewById(R.id.primaryButton);
        secondaryButton = findViewById(R.id.secondaryButton);
        tertiaryButton = findViewById(R.id.tertiaryButton);

        installContent = findViewById(R.id.installContent);
        helpContent = findViewById(R.id.helpContent);
        resourcePanelContent = findViewById(R.id.resourcePanelContent);
        settingsContent = findViewById(R.id.settingsContent);
        topAppBar = findViewById(R.id.topAppBar);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        navigationView = findViewById(R.id.navigationView);
        settingsThemeRow = findViewById(R.id.settingsThemeRow);
        settingsLanguageRow = findViewById(R.id.settingsLanguageRow);
        settingsInstallModeRow = findViewById(R.id.settingsInstallModeRow);
        settingsShizukuStatusRow = findViewById(R.id.settingsShizukuStatusRow);
        settingsRootStatusRow = findViewById(R.id.settingsRootStatusRow);
        settingsAboutRow = findViewById(R.id.settingsAboutRow);
        bindSettingsContent();
        bindResourcePanelContent();
        ViewCompat.setAccessibilityHeading(titleText, true);
    }

    private void applySystemBarInsets() {
        View root = getWindow().getDecorView();
        InsetsAwareView topBarInsets = InsetsAwareView.from(topAppBar);
        InsetsAwareView bottomBarInsets = InsetsAwareView.from(bottomNavigation);
        InsetsAwareView navigationViewInsets = InsetsAwareView.from(navigationView);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (topBarInsets != null) {
                topBarInsets.apply(systemBars.top, 0);
            }
            if (bottomBarInsets != null) {
                bottomBarInsets.apply(0, systemBars.bottom);
            }
            if (navigationViewInsets != null) {
                navigationViewInsets.apply(systemBars.top, systemBars.bottom);
            }

            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private static final class InsetsAwareView {
        private final View view;
        private final int paddingLeft;
        private final int paddingTop;
        private final int paddingRight;
        private final int paddingBottom;
        private final int height;

        private InsetsAwareView(View view) {
            this.view = view;
            paddingLeft = view.getPaddingLeft();
            paddingTop = view.getPaddingTop();
            paddingRight = view.getPaddingRight();
            paddingBottom = view.getPaddingBottom();
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            height = layoutParams != null ? layoutParams.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        static InsetsAwareView from(View view) {
            return view == null ? null : new InsetsAwareView(view);
        }

        void apply(int topInset, int bottomInset) {
            if (view == null) return;
            view.setPadding(paddingLeft, paddingTop + topInset, paddingRight, paddingBottom + bottomInset);

            if (height >= 0) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.height = height + topInset + bottomInset;
                    view.setLayoutParams(layoutParams);
                }
            }
        }
    }

    private void bindSettingsContent() {
        settingsController = new InstallerSettingsController(
                this,
                settingsThemeRow,
                settingsLanguageRow,
                settingsInstallModeRow,
                settingsShizukuStatusRow,
                settingsRootStatusRow,
                settingsAboutRow);
        settingsController.bind();
    }

    private void bindResourcePanelContent() {
        resourcePanelController = new ResourcePanelController(this, resourcePanelContent);
        resourcePanelController.bind();
    }

    private void bindNavigation() {
        if (bottomNavigation != null) {
            bottomNavigation.setOnItemSelectedListener(this::onNavigationItemSelected);
        }
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
        }
    }

    private boolean onNavigationItemSelected(MenuItem item) {
        showSection(item.getItemId(), false);
        return true;
    }

    private void showSection(int itemId) {
        showSection(itemId, true);
    }

    private void showSection(int itemId, boolean syncNavigation) {
        if (itemId != R.id.nav_help && itemId != R.id.nav_resource_panel && itemId != R.id.nav_settings) {
            itemId = R.id.nav_install;
        }
        selectedNavItemId = itemId;

        if (installContent != null) {
            installContent.setVisibility(itemId == R.id.nav_install ? View.VISIBLE : View.GONE);
        }
        if (helpContent != null) {
            helpContent.setVisibility(itemId == R.id.nav_help ? View.VISIBLE : View.GONE);
        }
        if (resourcePanelContent != null) {
            resourcePanelContent.setVisibility(itemId == R.id.nav_resource_panel ? View.VISIBLE : View.GONE);
        }
        if (settingsContent != null) {
            settingsContent.setVisibility(itemId == R.id.nav_settings ? View.VISIBLE : View.GONE);
        }

        if (syncNavigation && bottomNavigation != null && bottomNavigation.getSelectedItemId() != itemId) {
            bottomNavigation.setSelectedItemId(itemId);
        }
        if (navigationView != null) {
            navigationView.setCheckedItem(itemId);
        }
        if (itemId == R.id.nav_resource_panel && resourcePanelController != null) {
            resourcePanelController.onShown();
        }
        updateToolbarTitle(itemId);
    }

    private void updateToolbarTitle(int itemId) {
        if (topAppBar == null) return;
        int titleResId;
        if (itemId == R.id.nav_help) {
            titleResId = R.string.help_title;
        } else if (itemId == R.id.nav_resource_panel) {
            titleResId = R.string.resource_panel_title;
        } else if (itemId == R.id.nav_settings) {
            titleResId = R.string.settings_title;
        } else {
            titleResId = R.string.app_name;
        }
        topAppBar.setTitle(titleResId);
    }

    // ── temp file cleanup ──────────────────────────────────────

    private void cleanupTempFiles() {
        File partial = new File(getCacheDir(), APKS_CACHE_FILE_NAME + ".partial");
        if (partial.exists()) {
            partial.delete();
        }
    }

    private void abandonActiveSession() {
        if (systemBackend != null) {
            systemBackend.abandonActiveSession(this);
        }
    }

    // ── UI state dispatcher ────────────────────────────────────

    private void setUiState(UiState state) {
        currentState = state;

        // Reset all widgets
        progressBar.setVisibility(View.GONE);
        indeterminateBar.setVisibility(View.GONE);
        splitLabel.setVisibility(View.GONE);
        etaLabel.setVisibility(View.GONE);
        errorDetail.setVisibility(View.GONE);
        supportingText.setVisibility(View.GONE);
        primaryButton.setVisibility(View.GONE);
        secondaryButton.setVisibility(View.GONE);
        tertiaryButton.setVisibility(View.GONE);
        primaryButton.setOnClickListener(null);
        secondaryButton.setOnClickListener(null);
        tertiaryButton.setOnClickListener(null);
        primaryButton.setEnabled(true);
        secondaryButton.setEnabled(true);
        tertiaryButton.setEnabled(true);
        primaryButton.setContentDescription(null);
        secondaryButton.setContentDescription(null);
        tertiaryButton.setContentDescription(null);
        messageText.setGravity(Gravity.CENTER);
        messageText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        statusIcon.setImageResource(R.drawable.ic_status_info);

        switch (state) {
            case CHECKING -> showChecking();
            case READY_TO_DOWNLOAD -> showReadyToDownload();
            case ALREADY_INSTALLED -> showAlreadyInstalled();
            case DOWNLOADING -> showDownloading();
            case CONFIRM_INSTALL -> showConfirmInstall();
            case CONFIRM_UPDATE -> showConfirmUpdate();
            case NETWORK_ERROR -> showNetworkError();
            case CORRUPTED -> showCorrupted();
            case PERMISSION_DENIED -> showPermissionDenied();
            case INSTALLING -> showInstalling();
            case CONFIRM_SYSTEM -> showConfirmSystem();
            case SUCCESS -> showSuccess();
            case FAILED -> showFailed();
            case STORAGE_LOW -> showStorageLow();
        }

        // Announce state change to screen readers (treat title as announcement)
        titleText.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT);
    }

    // ── state: CHECKING ────────────────────────────────────────

    private void showChecking() {
        titleText.setText(R.string.checking_title);
        messageText.setText(R.string.checking_message);
        indeterminateBar.setVisibility(View.VISIBLE);
        indeterminateBar.setAlpha(1f);
        indeterminateBar.setContentDescription(getString(R.string.cd_progress_indeterminate));
    }

    // ── state: READY_TO_DOWNLOAD ────────────────────────────────

    private void showReadyToDownload() {
        titleText.setText(R.string.ready_download_title);
        messageText.setText(R.string.ready_download_message);
        primaryButton.setText(R.string.ready_download_button);
        primaryButton.setOnClickListener(v -> startDownload());
        primaryButton.setVisibility(View.VISIBLE);
        primaryButton.setContentDescription(getString(R.string.ready_download_button));

        secondaryButton.setText(R.string.close);
        secondaryButton.setOnClickListener(v -> finishAndRemoveTask());
        secondaryButton.setVisibility(View.VISIBLE);
    }

    // ── state: ALREADY_INSTALLED ───────────────────────────────

    private void showAlreadyInstalled() {
        String label = getAppLabel();
        String ver = existingPackage != null ? existingPackage.versionName : "?";
        titleText.setText(R.string.already_installed_title);
        messageText.setText(getString(R.string.already_installed_message, label, ver));

        primaryButton.setText(R.string.already_installed_launch);
        primaryButton.setOnClickListener(v -> launchGame());
        primaryButton.setVisibility(View.VISIBLE);
        primaryButton.setContentDescription(getString(R.string.already_installed_launch));

        secondaryButton.setText(R.string.already_installed_reinstall);
        secondaryButton.setOnClickListener(v -> startDownload());
        secondaryButton.setVisibility(View.VISIBLE);
    }

    // ── state: DOWNLOADING ─────────────────────────────────────

    private void showDownloading() {
        titleText.setText(R.string.downloading_title);
        messageText.setText(R.string.downloading_preparing);
        downloadProgressDeterminate = false;
        lastDownloadNotificationAt = 0;
        indeterminateBar.setVisibility(View.VISIBLE);
        indeterminateBar.setAlpha(1f);
        progressBar.setVisibility(View.GONE);
        progressBar.setProgress(0);
        progressBar.setIndeterminate(false);
        progressBar.setContentDescription(getString(R.string.cd_progress_indeterminate));
        showOperationNotification(
                getString(R.string.notif_download_title),
                getString(R.string.notif_download_message),
                0,
                true);
    }

    // ── state: NETWORK_ERROR ───────────────────────────────────

    private void showNetworkError() {
        cancelInstallNotification();
        statusIcon.setImageResource(R.drawable.ic_status_error);
        titleText.setText(R.string.network_error_title);
        messageText.setText(R.string.network_error_message);

        if (lastFailureDetail != null && !lastFailureDetail.isEmpty()) {
            errorDetail.setText(lastFailureDetail);
            errorDetail.setVisibility(View.VISIBLE);
        }

        primaryButton.setText(R.string.download_retry);
        primaryButton.setOnClickListener(v -> startDownload());
        primaryButton.setVisibility(View.VISIBLE);

        secondaryButton.setText(R.string.close);
        secondaryButton.setOnClickListener(v -> finishAndRemoveTask());
        secondaryButton.setVisibility(View.VISIBLE);
    }

    private void showCorrupted() {
        statusIcon.setImageResource(R.drawable.ic_status_error);
        titleText.setText(R.string.corrupted_title);
        messageText.setText(getString(R.string.corrupted_message,
                lastFailureDetail != null ? lastFailureDetail : getString(R.string.unknown_error)));

        primaryButton.setText(R.string.download_retry);
        primaryButton.setOnClickListener(v -> startDownload());
        primaryButton.setVisibility(View.VISIBLE);

        secondaryButton.setText(R.string.close);
        secondaryButton.setOnClickListener(v -> finishAndRemoveTask());
        secondaryButton.setVisibility(View.VISIBLE);
    }

    // ── state: CONFIRM_INSTALL / CONFIRM_UPDATE ────────────────

    private void showConfirmInstall() {
        messageText.setGravity(Gravity.START);
        messageText.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        titleText.setText(R.string.confirm_install_title);
        messageText.setText(getString(R.string.confirm_install_message,
                getAppLabel(), downloadedPackageName(), downloadedVersionLabel(),
                formatBytes(totalInstallBytes), apksArchive.splitCount()));

        String detail = buildSplitListDetail(apksArchive);
        if (detail != null) {
            supportingText.setText(detail);
            supportingText.setVisibility(View.VISIBLE);
        }

        primaryButton.setText(R.string.confirm_install_button);
        primaryButton.setOnClickListener(v -> {
            primaryButton.setEnabled(false);
            maybeRequestPermission();
        });
        primaryButton.setVisibility(View.VISIBLE);

        secondaryButton.setText(R.string.confirm_cancel);
        secondaryButton.setOnClickListener(v -> confirmCancel());
        secondaryButton.setVisibility(View.VISIBLE);
    }

    private void showConfirmUpdate() {
        String oldVer = existingPackage != null ? existingPackage.versionName : "?";
        String newVer = downloadedVersionLabel();
        messageText.setGravity(Gravity.START);
        messageText.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        titleText.setText(R.string.confirm_update_title);
        messageText.setText(getString(R.string.confirm_update_message,
                getAppLabel(), downloadedPackageName(),
                oldVer, newVer,
                formatBytes(totalInstallBytes), apksArchive.splitCount()));

        String detail = buildSplitListDetail(apksArchive);
        StringBuilder detailBuilder = new StringBuilder();
        if (detail != null) {
            detailBuilder.append(detail);
        }
        detailBuilder.append('\n').append(getString(R.string.confirm_update_warning));
        supportingText.setText(detailBuilder.toString());
        supportingText.setVisibility(View.VISIBLE);

        primaryButton.setText(R.string.confirm_update_button);
        primaryButton.setOnClickListener(v -> {
            primaryButton.setEnabled(false);
            maybeRequestPermission();
        });
        primaryButton.setVisibility(View.VISIBLE);

        secondaryButton.setText(R.string.confirm_cancel);
        secondaryButton.setOnClickListener(v -> confirmCancel());
        secondaryButton.setVisibility(View.VISIBLE);
    }

    private String buildSplitListDetail(ApksArchive archive) {
        List<ApksArchive.Split> splits = archive.splits();
        if (splits.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.confirm_split_list_title)).append(':');
        for (ApksArchive.Split s : splits) {
            sb.append('\n').append("  • ").append(s.displayName)
              .append(" (") .append(formatBytes(s.size)).append(')');
        }
        return sb.toString();
    }

    // ── state: PERMISSION_DENIED ───────────────────────────────

    private void showPermissionDenied() {
        statusIcon.setImageResource(R.drawable.ic_status_warning);
        titleText.setText(R.string.permission_title);
        messageText.setText(R.string.permission_message);

        primaryButton.setText(R.string.permission_grant);
        primaryButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                unknownSourcesLauncher.launch(intent);
            } else {
                maybeRequestNotificationPermissionThenInstall();
            }
        });
        primaryButton.setVisibility(View.VISIBLE);

        secondaryButton.setText(R.string.close);
        secondaryButton.setOnClickListener(v -> finishAndRemoveTask());
        secondaryButton.setVisibility(View.VISIBLE);
    }

    // ── state: INSTALLING ──────────────────────────────────────

    private void showInstalling() {
        titleText.setText(R.string.installing_title);
        messageText.setText(lastFailureDetail != null
                ? lastFailureDetail : getString(R.string.installing_preparing));
        indeterminateBar.setVisibility(View.VISIBLE);
        indeterminateBar.setAlpha(1f);
        indeterminateBar.setContentDescription(getString(R.string.cd_progress_indeterminate));
        if (installModeFallbackDetail != null && !installModeFallbackDetail.isEmpty()) {
            supportingText.setText(installModeFallbackDetail);
            supportingText.setVisibility(View.VISIBLE);
        }
        // Show indeterminate notification initially
        showOperationNotification(
                getString(R.string.notif_title),
                getString(R.string.notif_install_message),
                0,
                true);
    }

    // ── state: CONFIRM_SYSTEM ──────────────────────────────────

    private void showConfirmSystem() {
        titleText.setText(R.string.installing_title);
        messageText.setText(R.string.installing_confirm_system);
        indeterminateBar.setVisibility(View.VISIBLE);
        indeterminateBar.setAlpha(1f);
        indeterminateBar.setContentDescription(getString(R.string.cd_progress_indeterminate));
    }

    // ── state: SUCCESS ─────────────────────────────────────────

    private void showSuccess() {
        installing = false;
        cancelInstallNotification();
        cleanupTempFiles();
        statusIcon.setImageResource(R.drawable.ic_status_success);
        titleText.setText(R.string.success_title);
        messageText.setText(getString(R.string.success_message, getAppLabel()));

        primaryButton.setText(R.string.success_launch);
        primaryButton.setOnClickListener(v -> launchGame());
        primaryButton.setVisibility(View.VISIBLE);

        secondaryButton.setText(R.string.success_cleanup);
        secondaryButton.setOnClickListener(v -> requestSelfUninstall());
        secondaryButton.setVisibility(View.VISIBLE);
    }

    // ── state: FAILED ──────────────────────────────────────────

    private void showFailed() {
        installing = false;
        cancelInstallNotification();
        statusIcon.setImageResource(R.drawable.ic_status_error);
        titleText.setText(R.string.failed_title);
        messageText.setText(R.string.failed_message);

        if (lastFailureDetail != null && !lastFailureDetail.isEmpty()) {
            errorDetail.setText(lastFailureDetail);
            errorDetail.setVisibility(detailExpanded ? View.VISIBLE : View.GONE);
        }

        primaryButton.setText(R.string.failed_retry);
        primaryButton.setOnClickListener(v -> {
            detailExpanded = false;
            lastFailureDetail = null;
            cleanupTempFiles();
            apksArchive = null;
            releaseManifest = null;
            totalInstallBytes = 0;
            startDownload();
        });
        primaryButton.setVisibility(View.VISIBLE);

        if (lastFailureDetail != null && !lastFailureDetail.isEmpty()) {
            secondaryButton.setText(detailExpanded
                    ? R.string.failed_details_hide : R.string.failed_details_show);
            secondaryButton.setOnClickListener(v -> {
                detailExpanded = !detailExpanded;
                setUiState(UiState.FAILED);
            });
            tertiaryButton.setText(R.string.close);
            tertiaryButton.setOnClickListener(v -> finishAndRemoveTask());
            tertiaryButton.setVisibility(View.VISIBLE);
        } else {
            secondaryButton.setText(R.string.close);
            secondaryButton.setOnClickListener(v -> finishAndRemoveTask());
        }
        secondaryButton.setVisibility(View.VISIBLE);
    }

    // ── state: STORAGE_LOW ─────────────────────────────────────

    private void showStorageLow() {
        long free = getFreeSpace();
        long required = totalInstallBytes * MIN_FREE_SPACE_FACTOR;
        statusIcon.setImageResource(R.drawable.ic_status_warning);
        titleText.setText(R.string.storage_low_title);
        messageText.setText(getString(R.string.storage_low_message,
                formatBytes(required), formatBytes(free)));

        primaryButton.setText(R.string.storage_recheck);
        primaryButton.setOnClickListener(v -> runPreflightChecks());
        primaryButton.setVisibility(View.VISIBLE);

        secondaryButton.setText(R.string.close);
        secondaryButton.setOnClickListener(v -> finishAndRemoveTask());
        secondaryButton.setVisibility(View.VISIBLE);
    }

    // ── pre-flight ─────────────────────────────────────────────

    private void runPreflightChecks() {
        setUiState(UiState.CHECKING);
        new Thread(() -> {
            try {
                existingPackage = findExistingPackage();

                // Quick storage sanity check (200 MB estimated minimum)
                long estimatedSize = 200L * 1024 * 1024;
                long free = getFreeSpace();
                if (free < estimatedSize * MIN_FREE_SPACE_FACTOR) {
                    totalInstallBytes = estimatedSize;
                    postToUi(() -> setUiState(UiState.STORAGE_LOW));
                    return;
                }

                postToUi(() -> {
                    if (existingPackage != null) {
                        setUiState(UiState.ALREADY_INSTALLED);
                    } else {
                        setUiState(UiState.READY_TO_DOWNLOAD);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Preflight failed", e);
                lastFailureDetail = e.getMessage();
                postToUi(() -> setUiState(UiState.NETWORK_ERROR));
            }
        }).start();
    }

    // ── download ───────────────────────────────────────────────

    private void startDownload() {
        operationCancelled.set(false);
        setUiState(UiState.DOWNLOADING);

        new Thread(() -> {
            File outputFile = null;
            try {
                if (!hasInternetConnection()) {
                    throw new IOException(getString(R.string.error_no_network));
                }

                String apksUrl = APKS_DOWNLOAD_URL;
                long expectedSize = -1;
                String expectedSha256 = null;

                ApksDownloader downloader = new ApksDownloader(
                        DownloadLimits.defaults(), DOWNLOAD_BUFFER_SIZE, PROGRESS_UPDATE_INTERVAL_MS);

                if (!RELEASE_MANIFEST_PUBLIC_KEY.isEmpty()) {
                    try {
                        ReleaseManifest manifest = fetchAndVerifyReleaseManifest(downloader);
                        releaseManifest = manifest;
                        apksUrl = manifest.apksUrl;
                        expectedSize = manifest.apksSize;
                        expectedSha256 = manifest.apksSha256;
                    } catch (Exception manifestError) {
                        Log.w(TAG, "Release manifest unavailable; falling back to download headers", manifestError);
                    }
                }

                if (expectedSize < 0 || expectedSha256 == null) {
                    try {
                        ApksDownloader.ProbeResult probe = downloader.probe(apksUrl, operationCancelled);
                        if (expectedSize < 0) {
                            expectedSize = probe.contentLength;
                        }
                        if (expectedSha256 == null) {
                            expectedSha256 = probe.headerSha256;
                        }
                    } catch (Exception probeError) {
                        Log.w(TAG, "Download header probe failed; cache validation will parse only", probeError);
                    }
                }

                outputFile = new File(getCacheDir(), APKS_CACHE_FILE_NAME);
                ApksArchive cachedArchive = tryUseCachedApks(outputFile, expectedSize, expectedSha256);
                if (cachedArchive != null) {
                    apksFile = outputFile;
                    apksArchive = cachedArchive;
                    totalInstallBytes = apksArchive.totalBytes();
                    long free = getFreeSpace();
                    if (free < totalInstallBytes * MIN_FREE_SPACE_FACTOR) {
                        postToUi(() -> setUiState(UiState.STORAGE_LOW));
                        return;
                    }
                    postToUi(() -> {
                        if (existingPackage != null) {
                            setUiState(UiState.CONFIRM_UPDATE);
                        } else {
                            setUiState(UiState.CONFIRM_INSTALL);
                        }
                    });
                    return;
                }

                ApksDownloader.Result result = downloader.download(
                        apksUrl, outputFile, operationCancelled, this::postDownloadProgress);

                // Verify against manifest only when a signed manifest was used.
                if (expectedSize >= 0 && expectedSize != result.bytes) {
                    throw new IOException(getString(R.string.error_manifest_size_mismatch));
                }
                if (expectedSha256 != null && !expectedSha256.equals(result.sha256)) {
                    throw new IOException(getString(R.string.error_manifest_hash_mismatch));
                }

                apksFile = outputFile;
                apksArchive = new ApksArchiveParser(DownloadLimits.defaults())
                        .parse(outputFile, getPackageManager());
                totalInstallBytes = apksArchive.totalBytes();

                long free = getFreeSpace();
                if (free < totalInstallBytes * MIN_FREE_SPACE_FACTOR) {
                    postToUi(() -> setUiState(UiState.STORAGE_LOW));
                    return;
                }

                postToUi(() -> {
                    if (existingPackage != null) {
                        setUiState(UiState.CONFIRM_UPDATE);
                    } else {
                        setUiState(UiState.CONFIRM_INSTALL);
                    }
                });

            } catch (Exception e) {
                if (e instanceof ApksDownloader.DownloadCancelledException) {
                    Log.i(TAG, "Download cancelled");
                    if (outputFile != null) outputFile.delete();
                    apksFile = null;
                    apksArchive = null;
                    return;
                }
                Log.e(TAG, "Download failed", e);
                lastFailureDetail = e.getMessage();
                if (outputFile != null) outputFile.delete();
                apksFile = null;
                apksArchive = null;
                UiState failureState = e instanceof ZipException
                        ? UiState.CORRUPTED : UiState.NETWORK_ERROR;
                postToUi(() -> setUiState(failureState));
            }
        }).start();
    }

    private ApksArchive tryUseCachedApks(File cacheFile, long expectedSize,
                                         String expectedSha256) {
        if (!cacheFile.exists() || cacheFile.length() <= 0) {
            return null;
        }
        postToUi(() -> messageText.setText(R.string.downloading_using_cache));
        try {
            if (expectedSize >= 0 && cacheFile.length() != expectedSize) {
                throw new IOException(getString(R.string.error_manifest_size_mismatch));
            }
            if (expectedSha256 != null && !expectedSha256.equals(ApksDownloader.sha256(cacheFile))) {
                throw new IOException(getString(R.string.error_manifest_hash_mismatch));
            }
            return new ApksArchiveParser(DownloadLimits.defaults())
                    .parse(cacheFile, getPackageManager());
        } catch (Exception e) {
            Log.w(TAG, "Cached APKS is invalid", e);
            postToUi(() -> messageText.setText(R.string.downloading_cache_invalid));
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            return null;
        }
    }

    private ReleaseManifest fetchAndVerifyReleaseManifest(ApksDownloader downloader) throws Exception {
        String manifestJson = downloader.downloadText(
                APKS_MANIFEST_URL, DownloadLimits.defaults().maxArchiveBytes, operationCancelled);
        ReleaseManifestVerifier verifier = new ReleaseManifestVerifier(
                GAME_PACKAGE_NAME, RELEASE_MANIFEST_PUBLIC_KEY);
        return verifier.verify(manifestJson);
    }

    private void postDownloadProgress(long downloaded, long total,
                                      long bytesPerSec, long elapsedMs) {
        int pct = total > 0 ? (int) (downloaded * 100 / total) : 0;
        String sizeText = total > 0
                ? getString(R.string.downloading_progress,
                formatBytes(downloaded), formatBytes(total))
                : getString(R.string.downloading_progress_unknown, formatBytes(downloaded));
        String speedText = formatBytes(bytesPerSec) + "/s";
        String etaText = total > 0 && bytesPerSec > 0
                ? formatETA(total - downloaded, bytesPerSec) : "";

        postToUi(() -> {
            if (currentState != UiState.DOWNLOADING) return;

            // Crossfade from indeterminate to determinate only when the server reports a total size.
            if (total > 0 && !downloadProgressDeterminate && downloaded > 0) {
                downloadProgressDeterminate = true;
                crossfadeProgress(indeterminateBar, progressBar);
            }

            if (total > 0) {
                progressBar.setProgress(pct);
            }
            progressBar.setContentDescription(total > 0
                    ? getString(R.string.cd_progress_download, pct)
                    : getString(R.string.cd_progress_download_unknown, formatBytes(downloaded)));
            messageText.setText(sizeText);

            splitLabel.setText(getString(R.string.downloading_speed, speedText));
            splitLabel.setVisibility(View.VISIBLE);

            if (!etaText.isEmpty()) {
                etaLabel.setText(etaText);
                etaLabel.setVisibility(View.VISIBLE);
            }

            long now = System.currentTimeMillis();
            if (now - lastDownloadNotificationAt >= 1000 || (total > 0 && downloaded >= total)) {
                lastDownloadNotificationAt = now;
                showOperationNotification(
                        getString(R.string.notif_download_title),
                        sizeText,
                        pct,
                        total <= 0);
            }
        });
    }

    // ── install helpers ────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private boolean hasInternetConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
            return hasInternetCapability(caps);
        }

        Network[] networks = cm.getAllNetworks();
        for (Network network : networks) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (hasInternetCapability(caps)) return true;
        }
        return false;
    }

    private boolean hasInternetCapability(NetworkCapabilities caps) {
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @SuppressWarnings("deprecation")
    private long getFreeSpace() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return stat.getAvailableBytes();
        } else {
            return (long) stat.getAvailableBlocks() * stat.getBlockSize();
        }
    }

    private PackageInfo findExistingPackage() {
        try {
            return getPackageManager().getPackageInfo(GAME_PACKAGE_NAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    // ── install flow ───────────────────────────────────────────

    private void maybeRequestPermission() {
        InstallMode mode = InstallerPreferences.installMode(this);

        // Shizuku and Root modes bypass the unknown-sources permission.
        if (mode == InstallMode.SHIZUKU || mode == InstallMode.ROOT) {
            maybeRequestNotificationPermissionThenInstall();
            return;
        }

        // System mode: need unknown-sources permission on Android 8+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            unknownSourcesLauncher.launch(intent);
            return;
        }
        maybeRequestNotificationPermissionThenInstall();
    }

    private void maybeRequestNotificationPermissionThenInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        startInstallSession();
    }

    private void startInstallSession() {
        if (installing) return;
        if (apksArchive == null || apksArchive.splitCount() == 0) {
            lastFailureDetail = getString(R.string.error_no_install_files);
            setUiState(UiState.FAILED);
            return;
        }
        InstallMode mode = InstallerPreferences.installMode(this);
        final InstallBackend requestedBackend = InstallBackendFactory.create(this, mode);
        final InstallBackend backend = InstallBackendFactory.createOrFallback(this, mode);
        if (requestedBackend != null && backend.mode() != requestedBackend.mode()) {
            installModeFallbackDetail = getString(
                    R.string.install_mode_fallback_message,
                    requestedBackend.unavailableMessage(this));
        } else {
            installModeFallbackDetail = null;
        }

        installing = true;
        setUiState(UiState.INSTALLING);

        installStartTime = System.currentTimeMillis();
        new Thread(() -> {
            try {
                backend.install(this, apksArchive, apksFile, new InstallCallback() {
                    @Override
                    public void onProgress(long written, long total, int splitIndex,
                                           String splitName, long splitWritten, long splitSize) {
                        long elapsedMs = System.currentTimeMillis() - installStartTime;
                        postProgress(written, total, splitIndex, splitName,
                                splitWritten, splitSize, elapsedMs);
                    }

                    @Override
                    public void onSuccess() {
                        clearInstallCallbackToken();
                        postToUi(() -> setUiState(UiState.SUCCESS));
                    }

                    @Override
                    public void onFailure(String detail) {
                        clearInstallCallbackToken();
                        postToUi(() -> {
                            lastFailureDetail = detail;
                            setUiState(UiState.FAILED);
                        });
                    }

                    @Override
                    public void onConfirmSystem(Intent confirmationIntent) {
                        postToUi(() -> {
                            setUiState(UiState.CONFIRM_SYSTEM);
                            if (confirmationIntent != null
                                    && SystemInstallBackend.isSafeInstallConfirmationIntent(
                                            confirmationIntent, InstallerActivity.this)) {
                                startActivity(confirmationIntent);
                            } else {
                                lastFailureDetail = getString(R.string.error_invalid_confirmation_intent);
                                setUiState(UiState.FAILED);
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Install failed", e);
                String detail = e.getMessage() != null ? e.getMessage()
                        : getString(R.string.unknown_error);
                clearInstallCallbackToken();
                postToUi(() -> {
                    lastFailureDetail = detail;
                    setUiState(UiState.FAILED);
                });
            }
        }, "install-thread").start();
    }

    private long installStartTime;

    // ── post progress ──────────────────────────────────────

    private void postProgress(long totalWritten, long totalSize,
                              int splitIndex, String splitName,
                              long splitWritten, long splitSize,
                              long elapsedMs) {
        int overallPct = totalSize > 0 ? (int) (totalWritten * 100 / totalSize) : 0;
        int splitPct = splitSize > 0 ? (int) (splitWritten * 100 / splitSize) : 0;

        String splitText = getString(R.string.installing_progress_split,
                splitName, splitIndex + 1, apksArchive.splitCount());
        String pctText = getString(R.string.installing_progress_pct, overallPct);

        String etaText = "";
        if (totalWritten > 0 && totalSize > 0) {
            long remaining = totalSize - totalWritten;
            long bytesPerSec = totalWritten * 1000 / Math.max(elapsedMs, 1);
            if (bytesPerSec > 0) {
                etaText = formatETA(remaining, bytesPerSec);
            }
        }

        String finalEtaText = etaText;
        postToUi(() -> {
            if (currentState != UiState.INSTALLING) return;
            titleText.setText(R.string.installing_title);
            messageText.setText(pctText);

            // Crossfade from indeterminate to determinate on first progress
            if (indeterminateBar.getVisibility() == View.VISIBLE) {
                crossfadeProgress(indeterminateBar, progressBar);
            }

            progressBar.setProgress(overallPct);
            progressBar.setContentDescription(
                    getString(R.string.cd_progress_install, overallPct));

            splitLabel.setText(splitText);
            splitLabel.setVisibility(View.VISIBLE);
            splitLabel.setContentDescription(
                    getString(R.string.cd_progress_split, splitName, splitPct));

            if (!finalEtaText.isEmpty()) {
                etaLabel.setText(finalEtaText);
                etaLabel.setVisibility(View.VISIBLE);
            }

            // Update persistent notification
            showOperationNotification(
                    getString(R.string.notif_title),
                    getString(R.string.notif_install_message),
                    overallPct,
                    false);
        });
    }

    // ── install result callback ────────────────────────────────

    private void clearInstallCallbackToken() {
        installCallbackToken = null;
        getSharedPreferences(InstallerPreferences.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(PREF_INSTALL_CALLBACK_TOKEN)
                .apply();
    }

    private boolean handleInstallStatus(Intent intent) {
        // Delegate to the system backend, which owns the callback token logic.
        if (systemBackend == null) {
            systemBackend = (SystemInstallBackend)
                    InstallBackendFactory.create(this, InstallMode.SYSTEM);
        }
        SystemInstallBackend.StatusResult result = systemBackend.handleInstallStatus(this, intent);
        if (result == null) {
            return false;
        }

        if (result.pendingUserAction) {
            setUiState(UiState.CONFIRM_SYSTEM);
            Intent confirmation = result.confirmationIntent;
            if (confirmation != null
                    && SystemInstallBackend.isSafeInstallConfirmationIntent(confirmation, this)) {
                startActivity(confirmation);
            } else {
                lastFailureDetail = getString(R.string.error_invalid_confirmation_intent);
                setUiState(UiState.FAILED);
            }
            return true;
        }

        if (result.success) {
            clearInstallCallbackToken();
            setUiState(UiState.SUCCESS);
        } else {
            clearInstallCallbackToken();
            lastFailureDetail = result.errorDetail;
            setUiState(UiState.FAILED);
        }
        return true;
    }

    // ── launch ─────────────────────────────────────────────────

    private void launchGame() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(GAME_PACKAGE_NAME);
        if (launch == null) {
            launch = Intent.makeMainActivity(new ComponentName(
                    GAME_PACKAGE_NAME, GAME_ACTIVITY_NAME));
        }

        try {
            startActivity(launch);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Game launch failed", e);
            lastFailureDetail = getString(R.string.failed_launch_not_found);
            setUiState(UiState.FAILED);
        }
    }

    // ── self-cleanup ───────────────────────────────────────────

    private void requestSelfUninstall() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cleanup_title)
                .setMessage(R.string.cleanup_message)
                .setPositiveButton(R.string.cleanup_confirm, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_DELETE,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    finishAndRemoveTask();
                })
                .setNegativeButton(R.string.cleanup_keep, null)
                .show();
    }

    // ── formatting utilities ───────────────────────────────────

    private String downloadedPackageName() {
        if (apksArchive != null && apksArchive.packageName() != null
                && !apksArchive.packageName().isEmpty()) {
            return apksArchive.packageName();
        }
        return GAME_PACKAGE_NAME;
    }

    private String downloadedVersionLabel() {
        if (apksArchive == null) {
            return getString(R.string.latest_version);
        }
        String versionName = apksArchive.versionName();
        long versionCode = apksArchive.versionCode();
        if (versionName != null && !versionName.isEmpty() && versionCode >= 0) {
            return versionName + " (" + versionCode + ")";
        }
        if (versionName != null && !versionName.isEmpty()) {
            return versionName;
        }
        if (versionCode >= 0) {
            return String.valueOf(versionCode);
        }
        return getString(R.string.latest_version);
    }

    private String formatBytes(long bytes) {
        return FormatUtils.formatBytes(bytes);
    }

    private String formatETA(long remainingBytes, long bytesPerSec) {
        if (bytesPerSec <= 0) return "";
        long remainingSeconds = remainingBytes / bytesPerSec;

        if (remainingSeconds <= 0) {
            return getString(R.string.eta_less_than_minute);
        } else if (remainingSeconds < 60) {
            return getString(R.string.eta_seconds, remainingSeconds);
        } else {
            long minutes = remainingSeconds / 60;
            long seconds = remainingSeconds % 60;
            return getString(R.string.eta_minutes_seconds, minutes, seconds);
        }
    }

    private static String describeHttpStatus(int status) {
        String detail;
        switch (status) {
            case 400 -> detail = " (Bad Request — the server rejected the request)";
            case 401 -> detail = " (Unauthorized — authentication is required)";
            case 403 -> detail = " (Forbidden — access is denied)";
            case 404 -> detail = " (Not Found — the requested file was not found on the server)";
            case 405 -> detail = " (Method Not Allowed)";
            case 408 -> detail = " (Request Timeout)";
            case 429 -> detail = " (Too Many Requests — rate limited, try again later)";
            case 500 -> detail = " (Internal Server Error — the server encountered a problem)";
            case 502 -> detail = " (Bad Gateway — the upstream server returned an error)";
            case 503 -> detail = " (Service Unavailable — the server is temporarily down)";
            case 504 -> detail = " (Gateway Timeout — the upstream server did not respond in time)";
            default -> detail = status >= 500
                    ? " (Server Error)"
                    : status >= 400 ? " (Client Error)" : "";
        }
        return "HTTP " + status + detail;
    }

    private String getAppLabel() {
        try {
            PackageInfo pi = findExistingPackage();
            if (pi != null && pi.applicationInfo != null) {
                CharSequence label = getPackageManager()
                        .getApplicationLabel(pi.applicationInfo);
                if (label != null && label.length() > 0) return label.toString();
            }
        } catch (Exception ignored) { /* fall through */ }
        return GAME_PACKAGE_NAME;
    }
}
