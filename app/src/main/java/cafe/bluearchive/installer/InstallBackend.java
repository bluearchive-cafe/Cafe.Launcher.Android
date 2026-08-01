package cafe.bluearchive.installer;

import android.content.Context;

import java.io.File;

/**
 * Abstraction for installing an APKS archive via a specific privilege level.
 * <p>
 * Each backend owns its own threading, cancellation, and error reporting.
 * Implementations are stateless singletons — a single instance handles
 * all install requests for that mode.
 */
public interface InstallBackend {

    /** The mode this backend implements. */
    InstallMode mode();

    /**
     * Quick availability check — no side effects, no UI.
     * <p>
     * For Shizuku this checks binder liveness and permission.
     * For Root this checks whether {@code su} is reachable.
     *
     * @return true if this backend is ready to install
     */
    boolean isAvailable(Context context);

    /**
     * Human-readable explanation shown when {@link #isAvailable} returns false.
     */
    String unavailableMessage(Context context);

    /**
     * Install the given APKS archive.
     * <p>
     * This method runs synchronously on the calling thread and blocks until
     * the install completes or fails. The caller should invoke it from a
     * background thread.
     *
     * @param context   application or activity context
     * @param archive   validated split metadata
     * @param apksFile  the downloaded APKS ZIP file on disk
     * @param callback  progress and result callbacks (called from this thread)
     */
    void install(Context context, ApksArchive archive, File apksFile,
                 InstallCallback callback) throws Exception;

    /**
     * Cancels any in-flight install owned by this backend.
     * <p>
     * Implementations should make this safe to call from the UI thread. The
     * default is a no-op for backends that do not keep cancellable state.
     */
    default void cancel(Context context) {
    }
}
