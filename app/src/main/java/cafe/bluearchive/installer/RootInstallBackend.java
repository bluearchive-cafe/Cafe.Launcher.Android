package cafe.bluearchive.installer;

import android.content.Context;
import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    RootInstallBackend() {
    }

    @Override
    public InstallMode mode() {
        return InstallMode.ROOT;
    }

    @Override
    public boolean isAvailable(Context context) {
        try {
            // Use a latch to make the async check synchronous.
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean hasRoot = new AtomicBoolean(false);

            Shell.getShell(shell -> {
                hasRoot.set(shell != null && shell.isRoot());
                latch.countDown();
            });

            latch.await(3, TimeUnit.SECONDS);
            return hasRoot.get();
        } catch (Exception e) {
            Log.w(TAG, "Root availability check failed", e);
            return false;
        }
    }

    @Override
    public String unavailableMessage(Context context) {
        return "Root access is not available. Make sure your device is rooted and the superuser app has granted permission.";
    }

    @Override
    public void install(Context context, ApksArchive archive, File apksFile,
                        InstallCallback callback) throws Exception {

        ShellExecutor shell = new LibsuShellExecutor();
        ShellInstallSession session = new ShellInstallSession(
                context, archive, apksFile, shell, callback);
        session.run();
    }

    /**
     * Adapts libsu's shell API to our {@link ShellExecutor} interface.
     */
    private static class LibsuShellExecutor implements ShellExecutor {

        @Override
        public ShellResult execute(String... command) throws Exception {
            Shell.Job job = Shell.cmd(command[0]);
            for (int i = 1; i < command.length; i++) {
                job.add(command[i]);
            }
            Shell.Result result = job.exec();
            return new ShellResult(result.getCode(), outToString(result.getOut()), "");
        }

        @Override
        public ShellResult executeWithStdin(InputStream stdin, String... command) throws Exception {
            // libsu supports piping stdin via Shell.Job.add(InputStream).
            Shell.Job job = Shell.cmd(command[0]);
            for (int i = 1; i < command.length; i++) {
                job.add(command[i]);
            }
            job.add(stdin);
            Shell.Result result = job.exec();
            return new ShellResult(result.getCode(), outToString(result.getOut()), "");
        }

        private static String outToString(java.util.List<String> lines) {
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
