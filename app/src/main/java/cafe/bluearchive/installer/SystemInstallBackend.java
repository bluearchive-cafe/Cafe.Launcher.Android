package cafe.bluearchive.installer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/**
 * Installs via the standard Android {@link PackageInstaller.Session} API.
 * <p>
 * This is the default backend. It requires the "Install unknown apps"
 * permission on Android 8+ and may show a system confirmation dialog.
 */
final class SystemInstallBackend implements InstallBackend {

    private static final String TAG = "SystemInstallBackend";
    private static final String ACTION_INSTALL_STATUS =
            "cafe.bluearchive.installer.INSTALL_STATUS";
    private static final String EXTRA_INSTALL_CALLBACK_TOKEN =
            "cafe.bluearchive.installer.extra.INSTALL_CALLBACK_TOKEN";
    private static final String PREF_INSTALL_CALLBACK_TOKEN = "install_callback_token";

    private static final int SPLIT_BUFFER_SIZE = 1024 * 1024; // 1 MiB

    // Per-install mutable state. Only accessed from the install thread.
    private int activeSessionId = -1;
    private PackageInstaller.Session activeSession;
    private String installCallbackToken;

    SystemInstallBackend() {
    }

    @Override
    public InstallMode mode() {
        return InstallMode.SYSTEM;
    }

    @Override
    public boolean isAvailable(Context context) {
        // The system backend is always available.
        return true;
    }

    @Override
    public String unavailableMessage(Context context) {
        return ""; // never shown
    }

    @Override
    public void install(Context context, ApksArchive archive, File apksFile,
                        InstallCallback callback) throws Exception {
        PackageInstaller pi = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(BuildConfig.GAME_PACKAGE_NAME);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        }

        int sessionId = pi.createSession(params);
        activeSessionId = sessionId;
        PackageInstaller.Session session = null;
        ZipFile zip = null;

        try {
            session = pi.openSession(sessionId);
            activeSession = session;
            zip = new ZipFile(apksFile);

            byte[] buffer = new byte[SPLIT_BUFFER_SIZE];
            long totalWritten = 0;
            long totalSize = archive.totalBytes();

            for (int i = 0; i < archive.splitCount(); i++) {
                ApksArchive.Split split = archive.splitAt(i);
                String name = split.displayName;
                String entryName = split.entryName;
                long splitSize = split.size;
                long splitWritten = 0;

                java.util.zip.ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    throw new IOException("Missing split in APKS: " + entryName);
                }

                try (InputStream input = zip.getInputStream(entry);
                     OutputStream output = session.openWrite(name, 0, splitSize)) {

                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        splitWritten += count;
                        totalWritten += count;

                        callback.onProgress(totalWritten, totalSize, i, name,
                                splitWritten, splitSize);
                    }
                    session.fsync(output);
                }

                if (splitWritten != splitSize) {
                    throw new IOException("APK split size did not match the archive metadata");
                }

                Log.d(TAG, String.format(Locale.ROOT,
                        "Wrote split %d/%d: %s (%,d / %,d bytes)",
                        i + 1, archive.splitCount(), name, splitWritten, splitSize));
            }

            // Commit with a callback intent so we can receive the result.
            installCallbackToken = UUID.randomUUID().toString();
            context.getSharedPreferences(InstallerPreferences.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_INSTALL_CALLBACK_TOKEN, installCallbackToken)
                    .apply();

            Intent callbackIntent = new Intent(context, InstallerActivity.class)
                    .setAction(ACTION_INSTALL_STATUS)
                    .putExtra(EXTRA_INSTALL_CALLBACK_TOKEN, installCallbackToken);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            IntentSender sender = PendingIntent.getActivity(context, 0, callbackIntent, flags)
                    .getIntentSender();
            session.commit(sender);
            activeSession = null;
            activeSessionId = -1;
            session.close();
            session = null;
            zip.close();
            zip = null;

        } catch (Exception e) {
            if (session != null) {
                activeSession = null;
                activeSessionId = -1;
                try {
                    session.abandon();
                } catch (Exception ignored) {
                }
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception ignored) {
                }
            }
            throw e;
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Abandons the currently active session (if any). Safe to call from any thread.
     */
    void abandonActiveSession() {
        PackageInstaller.Session session = activeSession;
        activeSession = null;
        if (session != null) {
            try {
                session.abandon();
            } catch (Exception ignored) {
            }
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
        int sessionId = activeSessionId;
        activeSessionId = -1;
        if (sessionId >= 0) {
            try {
                // We need a Context to abandon here, but since this is called
                // from InstallerActivity we rely on the activity's own cleanup.
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Abandons the session using the given context's PackageInstaller.
     */
    void abandonActiveSession(Context context) {
        PackageInstaller.Session session = activeSession;
        activeSession = null;
        if (session != null) {
            try {
                session.abandon();
            } catch (Exception ignored) {
            }
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
        int sessionId = activeSessionId;
        activeSessionId = -1;
        if (sessionId >= 0) {
            try {
                context.getPackageManager().getPackageInstaller().abandonSession(sessionId);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Processes an install status callback intent.
     *
     * @return a result describing what happened, or null if the intent is not
     *         a valid install status callback
     */
    StatusResult handleInstallStatus(Context context, Intent intent) {
        if (!ACTION_INSTALL_STATUS.equals(intent.getAction())) {
            return null;
        }

        String token = intent.getStringExtra(EXTRA_INSTALL_CALLBACK_TOKEN);
        String expectedToken = installCallbackToken != null
                ? installCallbackToken
                : context.getSharedPreferences(InstallerPreferences.PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_INSTALL_CALLBACK_TOKEN, null);
        if (expectedToken == null || !expectedToken.equals(token)) {
            Log.w(TAG, "Ignoring install status intent with invalid callback token");
            return null;
        }

        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @SuppressWarnings("deprecation")
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            StatusResult result = new StatusResult();
            result.pendingUserAction = true;
            result.confirmationIntent = confirmation;
            return result;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            clearCallbackToken(context);
            StatusResult result = new StatusResult();
            result.success = true;
            return result;
        } else {
            clearCallbackToken(context);
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            int legacyStatus = intent.getIntExtra(
                    "android.content.pm.extra.LEGACY_STATUS", -999);
            StatusResult result = new StatusResult();
            result.success = false;
            result.errorDetail = InstallErrorMapper.parse(context, status, msg, legacyStatus);
            return result;
        }
    }

    private void clearCallbackToken(Context context) {
        installCallbackToken = null;
        context.getSharedPreferences(InstallerPreferences.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_INSTALL_CALLBACK_TOKEN)
                .apply();
    }

    /**
     * Checks whether a system confirmation intent is safe to launch.
     */
    static boolean isSafeInstallConfirmationIntent(Intent intent, Context context) {
        int grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
        if ((intent.getFlags() & grantFlags) != 0) {
            return false;
        }
        return intent.resolveActivity(context.getPackageManager()) != null;
    }

    /**
     * Result of processing an install status intent.
     */
    static final class StatusResult {
        boolean success;
        boolean pendingUserAction;
        Intent confirmationIntent;
        String errorDetail;
    }
}
