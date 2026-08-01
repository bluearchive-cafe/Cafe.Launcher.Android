package cafe.bluearchive.installer;

import android.content.Context;
import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Installs via root ({@code su}) using libsu.
 * <p>
 * Uses the same {@code pm install-create/write/commit} pattern as
 * {@link ShellInstallSession}, executed through a libsu root shell.
 */
final class RootInstallBackend implements InstallBackend {

    private static final String TAG = "RootInstallBackend";
    private static final int ROOT_CHECK_TIMEOUT_SEC = 5;

    private final Object sessionLock = new Object();
    private ShellInstallSession activeSession;

    RootInstallBackend() {
    }

    @Override
    public InstallMode mode() {
        return InstallMode.ROOT;
    }

    @Override
    public boolean isAvailable(Context context) {
        return isRootAvailable();
    }

    static boolean isRootAvailable() {
        try {
            Boolean grant = Shell.isAppGrantedRoot();
            if (grant != null) {
                return grant;
            }

            Shell cachedShell = Shell.getCachedShell();
            if (cachedShell != null) {
                return cachedShell.isRoot();
            }

            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean hasRoot = new AtomicBoolean(false);
            AtomicReference<Exception> error = new AtomicReference<>();

            Shell.getShell(shell -> {
                try {
                    hasRoot.set(shell != null && shell.isRoot());
                } catch (Exception e) {
                    error.set(e);
                } finally {
                    latch.countDown();
                }
            });

            if (!latch.await(ROOT_CHECK_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                Log.w(TAG, "Root availability check timed out");
                return false;
            }
            if (error.get() != null) {
                Log.w(TAG, "Root availability callback failed", error.get());
                return false;
            }
            return hasRoot.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Root availability check interrupted", e);
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Root availability check failed", e);
            return false;
        }
    }

    @Override
    public String unavailableMessage(Context context) {
        return "Root access is not available. Make sure your device is rooted and the superuser app has granted permission.";
    }

    ShellExecutor.ShellResult executeShell(String... command) throws Exception {
        return new LibsuShellExecutor().execute(command);
    }

    @Override
    public void install(Context context, ApksArchive archive, File apksFile,
                        InstallCallback callback) throws Exception {

        ShellExecutor shell = new LibsuShellExecutor();
        ShellInstallSession session = new ShellInstallSession(
                context, archive, apksFile, shell, callback);
        synchronized (sessionLock) {
            activeSession = session;
        }
        try {
            session.run();
        } finally {
            synchronized (sessionLock) {
                if (activeSession == session) {
                    activeSession = null;
                }
            }
        }
    }

    @Override
    public void cancel(Context context) {
        ShellInstallSession session;
        synchronized (sessionLock) {
            session = activeSession;
        }
        if (session != null) {
            session.cancel();
        }
    }

    /**
     * Adapts libsu's shell API to our {@link ShellExecutor} interface.
     */
    private static class LibsuShellExecutor implements ShellExecutor {

        @Override
        public ShellResult execute(String... command) throws Exception {
            java.util.List<String> out = new java.util.ArrayList<>();
            java.util.List<String> err = new java.util.ArrayList<>();
            Shell.Result result = Shell.cmd(joinShellCommand(command))
                    .to(out, err)
                    .exec();
            return new ShellResult(result.getCode(), linesToString(out), linesToString(err));
        }

        @Override
        public ShellResult executeWithStdin(InputStream stdin, String... command) throws Exception {
            // libsu serves job sources to the same shell stdin in order; the APK
            // stream is consumed by the preceding pm command while it reads '-'.
            java.util.List<String> out = new java.util.ArrayList<>();
            java.util.List<String> err = new java.util.ArrayList<>();
            Shell.Result result = Shell.cmd(joinShellCommand(command))
                    .add(stdin)
                    .to(out, err)
                    .exec();
            return new ShellResult(result.getCode(), linesToString(out), linesToString(err));
        }

        private static String joinShellCommand(String... command) {
            StringBuilder sb = new StringBuilder();
            for (String arg : command) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(shellQuote(arg));
            }
            return sb.toString();
        }

        private static String shellQuote(String arg) {
            if (arg == null || arg.isEmpty()) return "''";
            if (arg.matches("[A-Za-z0-9_@%+=:,./-]+")) return arg;
            return "'" + arg.replace("'", "'\\''") + "'";
        }

        private static String linesToString(java.util.List<String> lines) {
            if (lines == null || lines.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
