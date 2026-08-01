package cafe.bluearchive.installer;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * A single-use install session that installs an APKS archive via a privileged
 * shell (root or Shizuku) using the {@code pm install-create / install-write /
 * install-commit} pattern.
 * <p>
 * Each session serializes with a shared {@link Semaphore} to ensure only one
 * privileged install runs at a time (matching Android platform limits).
 */
final class ShellInstallSession {

    private static final String TAG = "ShellInstallSession";
    private static final int SPLIT_BUFFER_SIZE = 64 * 1024; // 64 KiB
    private static final long PROGRESS_REPORT_THRESHOLD = 256L * 1024L; // 256 KiB
    private static final Semaphore INSTALL_LOCK = new Semaphore(1);

    private final Context context;
    private final ApksArchive archive;
    private final File apksFile;
    private final ShellExecutor shell;
    private final InstallCallback callback;
    private final String installerPackage;
    private volatile boolean cancelled;
    private int pmSessionId = -1;

    ShellInstallSession(Context context, ApksArchive archive, File apksFile,
                        ShellExecutor shell, InstallCallback callback) {
        this.context = context;
        this.archive = archive;
        this.apksFile = apksFile;
        this.shell = shell;
        this.callback = callback;
        this.installerPackage = context.getPackageName();
    }

    void cancel() {
        cancelled = true;
        abandonIfNeeded();
    }

    private void throwIfCancelled() throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new IOException("Install cancelled");
        }
    }

    /**
     * Runs the full install flow synchronously. Exceptions are thrown on
     * failure; the caller should catch and report via {@link InstallCallback}.
     * <p>
     * Acquires the install semaphore (interruptibly) so the caller can cancel
     * a stuck install by interrupting the thread.
     */
    void run() throws Exception {
        try {
            INSTALL_LOCK.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Install cancelled", e);
        }
        try {
            doInstall();
        } finally {
            abandonIfNeeded();
            INSTALL_LOCK.release();
        }
    }

    private void doInstall() throws Exception {
        throwIfCancelled();
        pmSessionId = createSession();
        Log.d(TAG, "Created pm session " + pmSessionId);

        ZipFile zip = new ZipFile(apksFile);
        try {
            long totalSize = archive.totalBytes();
            long totalWritten = 0;

            for (int i = 0; i < archive.splitCount(); i++) {
                throwIfCancelled();
                ApksArchive.Split split = archive.splitAt(i);
                String entryName = split.entryName;
                long splitSize = split.size;

                java.util.zip.ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    throw new IOException("Missing split in APKS: " + entryName);
                }

                // pm install-write -S <size> <sessionId> <fileName>
                // APK bytes are piped to stdin.
                InputStream apkStream = zip.getInputStream(entry);

                // Wrap with progress reporting at ~256 KiB granularity.
                ProgressInputStream progressStream = new ProgressInputStream(
                        apkStream, splitSize, totalWritten, totalSize, i, split, callback);

                ShellExecutor.ShellResult writeResult = shell.executeWithStdin(
                        progressStream,
                        "pm", "install-write",
                        "-S", String.valueOf(splitSize),
                        String.valueOf(pmSessionId),
                        String.format("%d.apk", i));

                if (!writeResult.isSuccess()) {
                    throw new IOException("install-write failed (exit " + writeResult.exitCode
                            + "): " + writeResult.err);
                }
                if (progressStream.splitWritten() != splitSize) {
                    throw new IOException("APK split size did not match the archive metadata");
                }

                totalWritten += splitSize;
                // Final callback for this split (100%).
                callback.onProgress(totalWritten, totalSize, i, split.displayName,
                        splitSize, splitSize);
            }

            // Commit the session.
            ShellExecutor.ShellResult commitResult = shell.execute(
                    "pm", "install-commit", String.valueOf(pmSessionId));

            if (commitResult.isSuccess()) {
                pmSessionId = -1; // already committed, don't abandon
                callback.onSuccess();
            } else {
                String error = parseCommitError(commitResult);
                throw new IOException(error);
            }
        } finally {
            try {
                zip.close();
            } catch (Exception ignored) {
            }
        }
    }

    private int createSession() throws Exception {
        String[][] commands = {
                {
                        "pm", "install-create",
                        "-r",
                        "--install-location", "0",
                        "-i", installerPackage
                },
                {
                        "pm", "install-create",
                        "-r",
                        "-i", installerPackage
                }
        };
        StringBuilder attempts = new StringBuilder();

        for (String[] cmd : commands) {
            ShellExecutor.ShellResult result = shell.execute(cmd);
            attempts.append("Command: ")
                    .append(String.join(" ", cmd))
                    .append("\nExit: ")
                    .append(result.exitCode)
                    .append("\nstdout:\n")
                    .append(result.out == null ? "" : result.out)
                    .append("\nstderr:\n")
                    .append(result.err == null ? "" : result.err)
                    .append("\n\n");

            if (!result.isSuccess()) {
                Log.w(TAG, "install-create command failed: " + String.join(" ", cmd));
                continue;
            }

            Integer sessionId = extractSessionId(result.out);
            if (sessionId != null) {
                return sessionId;
            }
            Log.w(TAG, "Could not parse session ID from: " + result.out);
        }

        throw new IOException("Could not create install session. Attempts:\n" + attempts);
    }

    private void abandonIfNeeded() {
        if (pmSessionId < 0) return;
        try {
            ShellExecutor.ShellResult result = shell.execute(
                    "pm", "install-abandon", String.valueOf(pmSessionId));
            Log.d(TAG, "install-abandon " + pmSessionId + ": " + result.out);
        } catch (Exception e) {
            Log.w(TAG, "Failed to abandon session " + pmSessionId, e);
        } finally {
            pmSessionId = -1;
        }
    }

    /**
     * Parses the numeric session ID from a {@code pm install-create} result
     * like "Success: created install session [12345678]".
     */
    private static Integer extractSessionId(String commandResult) {
        try {
            Pattern pattern = Pattern.compile("\\[(\\d+)]");
            Matcher matcher = pattern.matcher(commandResult);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            // Fallback: try first numeric group (older pm or custom ROMs).
            pattern = Pattern.compile("(\\d+)");
            matcher = pattern.matcher(commandResult);
            if (matcher.find()) {
                Log.w(TAG, "Session ID parsed with fallback regex: " + commandResult);
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse session ID: " + commandResult, e);
        }
        return null;
    }

    /**
     * Maps known {@code pm install-commit} error substrings to user-friendly
     * messages, falling back to the raw output.
     */
    private String parseCommitError(ShellExecutor.ShellResult result) {
        String out = result.out != null ? result.out : "";
        String errText = result.err != null ? result.err : "";
        String combined = out + "\n" + errText;

        for (String code : KNOWN_ERROR_CODES) {
            if (combined.contains(code)) {
                return code;
            }
        }

        if (!errText.isEmpty()) {
            return errText;
        }
        if (!out.isEmpty()) {
            return out;
        }
        return "install-commit failed with exit code " + result.exitCode;
    }

    private static final String[] KNOWN_ERROR_CODES = {
            "INSTALL_FAILED_ALREADY_EXISTS",
            "INSTALL_FAILED_INVALID_APK",
            "INSTALL_FAILED_INSUFFICIENT_STORAGE",
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE",
            "INSTALL_FAILED_VERSION_DOWNGRADE",
            "INSTALL_FAILED_MISSING_SPLIT",
            "INSTALL_FAILED_NO_MATCHING_ABIS",
            "INSTALL_FAILED_VERIFICATION_FAILURE",
            "INSTALL_FAILED_CONFLICTING_PROVIDER",
            "INSTALL_FAILED_OLDER_SDK",
            "INSTALL_FAILED_NEWER_SDK",
            "INSTALL_PARSE_FAILED_NO_CERTIFICATES"
    };

    // ── Progress reporting stream ────────────────────────────────

    /**
     * An {@link InputStream} wrapper that reports per-split progress to the
     * install callback at ~256 KiB granularity so the UI updates live during
     * large split writes rather than only after the entire split finishes.
     */
    private class ProgressInputStream extends InputStream {
        private final InputStream delegate;
        private final long splitSize;
        private final long totalBytesBefore;
        private final long totalArchiveSize;
        private final int splitIndex;
        private final ApksArchive.Split split;
        private final InstallCallback callback;
        private long splitWritten;
        private long lastReported;

        ProgressInputStream(InputStream delegate, long splitSize,
                            long totalBytesBefore, long totalArchiveSize,
                            int splitIndex, ApksArchive.Split split,
                            InstallCallback callback) {
            this.delegate = delegate;
            this.splitSize = splitSize;
            this.totalBytesBefore = totalBytesBefore;
            this.totalArchiveSize = totalArchiveSize;
            this.splitIndex = splitIndex;
            this.split = split;
            this.callback = callback;
        }

        @Override
        public int read() throws IOException {
            throwIfCancelled();
            int b = delegate.read();
            if (b != -1) record(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            throwIfCancelled();
            int n = delegate.read(b, off, len);
            if (n > 0) record(n);
            return n;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void record(long bytes) {
            splitWritten += bytes;
            long since = splitWritten - lastReported;
            if (since >= PROGRESS_REPORT_THRESHOLD || splitWritten >= splitSize) {
                lastReported = splitWritten;
                long totalWritten = totalBytesBefore + splitWritten;
                callback.onProgress(totalWritten, totalArchiveSize,
                        splitIndex, split.displayName,
                        splitWritten, splitSize);
            }
        }

        long splitWritten() {
            return splitWritten;
        }
    }
}
