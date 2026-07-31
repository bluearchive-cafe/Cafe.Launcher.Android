package cafe.bluearchive.installer;

import android.content.Context;

/**
 * Creates the appropriate {@link InstallBackend} for the given mode.
 */
public final class InstallBackendFactory {

    private static final Object LOCK = new Object();

    private static volatile SystemInstallBackend systemBackend;
    private static volatile ShizukuInstallBackend shizukuBackend;
    private static volatile RootInstallBackend rootBackend;

    private InstallBackendFactory() {
    }

    /**
     * Returns the backend for {@code mode}, or the system backend as a
     * safe fallback for unrecognized modes.
     */
    public static InstallBackend create(Context context, InstallMode mode) {
        switch (mode) {
            case SYSTEM:
                return getSystemBackend();
            case SHIZUKU:
                return getShizukuBackend();
            case ROOT:
                return getRootBackend();
            default:
                return getSystemBackend();
        }
    }

    /**
     * Returns the backend only if it passes {@link InstallBackend#isAvailable}.
     * Otherwise returns the system backend as a safe fallback.
     */
    public static InstallBackend createOrFallback(Context context, InstallMode mode) {
        InstallBackend backend = create(context, mode);
        if (backend != null && backend.isAvailable(context)) {
            return backend;
        }
        return getSystemBackend();
    }

    // ── singleton accessors (double-checked locking) ─────────────

    private static SystemInstallBackend getSystemBackend() {
        if (systemBackend == null) {
            synchronized (LOCK) {
                if (systemBackend == null) {
                    systemBackend = new SystemInstallBackend();
                }
            }
        }
        return systemBackend;
    }

    private static ShizukuInstallBackend getShizukuBackend() {
        if (shizukuBackend == null) {
            synchronized (LOCK) {
                if (shizukuBackend == null) {
                    shizukuBackend = new ShizukuInstallBackend();
                }
            }
        }
        return shizukuBackend;
    }

    private static RootInstallBackend getRootBackend() {
        if (rootBackend == null) {
            synchronized (LOCK) {
                if (rootBackend == null) {
                    rootBackend = new RootInstallBackend();
                }
            }
        }
        return rootBackend;
    }

    // ── lifecycle ────────────────────────────────────────────────

    /**
     * Returns the Shizuku backend instance (may be null if never created).
     * Used by the Activity to wire cancellation.
     */
    static ShizukuInstallBackend getShizukuBackendIfPresent() {
        return shizukuBackend;
    }

    /**
     * Destroys the Shizuku backend (unbinds UserService, removes listeners).
     * Safe to call multiple times. After calling, the Shizuku backend will
     * be re-created on the next {@link #create(Context, InstallMode)} call.
     */
    public static void destroy() {
        ShizukuInstallBackend backend;
        synchronized (LOCK) {
            backend = shizukuBackend;
            shizukuBackend = null;
        }
        if (backend != null) {
            backend.destroy();
        }
    }
}
