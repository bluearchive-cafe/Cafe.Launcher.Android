package cafe.bluearchive.installer;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Keep;

import cafe.bluearchive.installer.shizuku.IShellService;
import cafe.bluearchive.installer.shizuku.ShellResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Shizuku UserService that executes privileged shell commands on behalf
 * of the installer.  Runs inside the Shizuku server process with root or
 * shell (ADB) identity, so commands such as {@code pm} are elevated.
 *
 * <p>Lifecycle: created by Shizuku when the app binds via
 * {@link rikka.shizuku.Shizuku#bindUserService}.  The {@link #destroy()}
 * method must call {@link System#exit(int)} to cleanly terminate the
 * service process.
 */
public final class ShizukuShellService extends IShellService.Stub {

    private static final String TAG = "ShizukuShellService";

    private static final int MAX_RESULT_BYTES = 64 * 1024; // 64 KiB safety cap

    private Process currentProcess;
    private volatile boolean cancelled;
    private final ExecutorService drainExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ShizukuSvc-drain");
        t.setDaemon(true);
        return t;
    });

    // ── constructors ────────────────────────────────────────────

    /** Required by Shizuku &lt; v13 (falls back to this if Context ctor is absent). */
    public ShizukuShellService() { }

    /**
     * Shizuku v13+ constructor.  Annotated with {@link Keep} so ProGuard / R8
     * does not strip it.
     */
    @Keep
    public ShizukuShellService(Context context) { }

    // ── IShellService ────────────────────────────────────────────

    @Override
    public ShellResult exec(String[] cmd, int timeoutSec) throws RemoteException {
        return execInternal(cmd, timeoutSec, null);
    }

    @Override
    public ShellResult execWithStdin(String[] cmd, int timeoutSec,
                                     ParcelFileDescriptor stdinPipe) throws RemoteException {
        return execInternal(cmd, timeoutSec, stdinPipe);
    }

    private ShellResult execInternal(String[] cmd, int timeoutSec,
                                     ParcelFileDescriptor stdinPipe) {
        cancelled = false;
        int exitCode = -1;
        StringBuilder stdOutSb = new StringBuilder();
        StringBuilder stdErrSb = new StringBuilder();

        try {
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(false)
                    .start();
            currentProcess = process;

            // ** Start stdout/stderr drain threads FIRST **
            // Prevents deadlock: if the process's stdout buffer fills before we
            // start reading, the process blocks indefinitely.
            Thread stdOutThread = drain(process.getInputStream(), stdOutSb);
            Thread stdErrThread = drain(process.getErrorStream(), stdErrSb);

            // Pipe stdin if provided.
            if (stdinPipe != null) {
                try (InputStream pipeIn = new ParcelFileDescriptor
                        .AutoCloseInputStream(stdinPipe);
                     OutputStream processIn = process.getOutputStream()) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while (!cancelled && (n = pipeIn.read(buf)) != -1) {
                        processIn.write(buf, 0, n);
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Stdin pipe error", e);
                }
            }

            // Wait for process completion with timeout.
            if (timeoutSec > 0) {
                if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Command timed out after " + timeoutSec + "s: "
                            + String.join(" ", cmd));
                    process.destroy();
                    process.destroyForcibly();
                    stdErrSb.append("\n[Command timed out after ")
                            .append(timeoutSec).append(" seconds]");
                }
            } else {
                process.waitFor();
            }

            exitCode = process.exitValue();

            // Wait for drain threads with a safety deadline.
            stdOutThread.join(5_000);
            stdErrThread.join(5_000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stdErrSb.append("\n[Command interrupted]");
        } catch (IOException e) {
            Log.e(TAG, "Command exec failed", e);
            stdErrSb.append('\n').append(e.getMessage());
        } finally {
            currentProcess = null;
        }

        return new ShellResult(exitCode,
                truncate(stdOutSb.toString(), MAX_RESULT_BYTES),
                truncate(stdErrSb.toString(), MAX_RESULT_BYTES));
    }

    @Override
    public void cancel() throws RemoteException {
        cancelled = true;
        Process p = currentProcess;
        if (p != null) {
            p.destroy();
            p.destroyForcibly();
        }
    }

    @Override
    public void destroy() throws RemoteException {
        cancel();
        drainExecutor.shutdownNow();
        // Shizuku protocol requires System.exit(0) in destroy().
        System.exit(0);
    }

    // ── helpers ─────────────────────────────────────────────────

    private Thread drain(InputStream in, StringBuilder sb) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[4096];
            try (in) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                // Stream closed — normal on process exit.
            }
        }, "ShizukuSvc-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static String truncate(String s, int maxBytes) {
        if (s == null) return "";
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        if (utf8.length <= maxBytes) return s;
        // Find a safe byte boundary (trailing bytes in multi-byte sequences).
        int cut = maxBytes;
        while (cut > 0) {
            int b = utf8[cut] & 0xFF;
            if ((b & 0xC0) != 0x80) break; // not a continuation byte
            cut--;
        }
        return new String(utf8, 0, cut, StandardCharsets.UTF_8) + "\n[...truncated]";
    }
}
