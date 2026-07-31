package cafe.bluearchive.installer;

import android.content.Context;
import android.content.Intent;

import java.io.File;

/**
 * Callbacks from an {@link InstallBackend} during installation.
 * <p>
 * All methods are invoked from a background thread. The caller is responsible
 * for posting to the UI thread as needed.
 */
public interface InstallCallback {

    /**
     * Called for every split write progress update.
     *
     * @param written      total bytes written across all splits so far
     * @param total        total bytes across all splits
     * @param splitIndex   zero-based index of the current split
     * @param splitName    display name of the current split (e.g. "base.apk")
     * @param splitWritten bytes written for the current split so far
     * @param splitSize    total bytes of the current split
     */
    void onProgress(long written, long total, int splitIndex, String splitName,
                    long splitWritten, long splitSize);

    /** Installation completed successfully. */
    void onSuccess();

    /**
     * Installation failed.
     *
     * @param detail human-readable error description for the user
     */
    void onFailure(String detail);

    /**
     * The system installer requires the user to confirm via a system dialog.
     * Only emitted by the {@link InstallMode#SYSTEM} backend.
     *
     * @param confirmationIntent the intent to launch for user confirmation
     */
    void onConfirmSystem(Intent confirmationIntent);
}
