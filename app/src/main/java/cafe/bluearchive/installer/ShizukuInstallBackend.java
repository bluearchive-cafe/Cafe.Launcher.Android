package cafe.bluearchive.installer;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import cafe.bluearchive.installer.shizuku.IShellService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

/**
 * Installs via Shizuku using the official UserService API.
 *
 * <p>The {@link ShizukuShellService} runs inside the Shizuku server process
 * with root or shell (ADB) identity.  This backend binds to the service,
 * sends shell commands via AIDL, and re-uses the binder across installs.
 */
final class ShizukuInstallBackend implements InstallBackend {

    private static final String TAG = "ShizukuInstallBackend";

    // Timeout constants
    private static final int BIND_TIMEOUT_SEC = 5;
    private static final int EXEC_TIMEOUT_SEC = 30;
    private static final int WRITE_TIMEOUT_SEC = 120;

    // ── UserService state ────────────────────────────────────────

    private IShellService shellService;
    private ServiceConnection serviceConnection;
    private Shizuku.UserServiceArgs serviceArgs;
    private final AtomicBoolean serviceBound = new AtomicBoolean(false);
    private CountDownLatch bindLatch;
    private volatile boolean cancelled;
    private volatile boolean destroyed;

    // Binder lifecycle listeners (registered once).
    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
            this::onBinderReceived;
    private final Shizuku.OnBinderDeadListener binderDeadListener =
            this::onBinderDead;

    ShizukuInstallBackend() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
    }

    // ── InstallBackend ───────────────────────────────────────────

    @Override
    public InstallMode mode() {
        return InstallMode.SHIZUKU;
    }

    @Override
    public boolean isAvailable(Context context) {
        try {
            if (!Shizuku.pingBinder()) {
                return false;
            }
            if (Shizuku.isPreV11()) {
                return false;
            }
            return Shizuku.checkSelfPermission()
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            Log.w(TAG, "Shizuku availability check failed", e);
            return false;
        }
    }

    @Override
    public String unavailableMessage(Context context) {
        if (!Shizuku.pingBinder()) {
            return "Shizuku is not running. Please start Shizuku first.";
        }
        if (Shizuku.isPreV11()) {
            return "Shizuku version is too old. Please update to the latest version.";
        }
        if (Shizuku.checkSelfPermission()
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return "Shizuku permission has not been granted. "
                    + "Please grant permission in Settings.";
        }
        return "Shizuku is not available.";
    }

    @Override
    public void install(Context context, ApksArchive archive, File apksFile,
                        InstallCallback callback) throws Exception {

        if (!isAvailable(context)) {
            throw new IOException(unavailableMessage(context));
        }

        cancelled = false;
        ensureBound(context);

        ShellExecutor shell = new UserServiceShellExecutor();
        ShellInstallSession session = new ShellInstallSession(
                context, archive, apksFile, shell, callback);
        session.run();
    }

    // ── UserService binding ──────────────────────────────────────

    private void ensureBound(Context context) throws IOException {
        if (serviceBound.get() && shellService != null) return;

        serviceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(context.getPackageName(),
                        ShizukuShellService.class.getName()))
                .daemon(false)
                .processNameSuffix("service")
                .tag("shell")
                .version(1);

        bindLatch = new CountDownLatch(1);

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                shellService = IShellService.Stub.asInterface(binder);
                serviceBound.set(true);
                bindLatch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                shellService = null;
                serviceBound.set(false);
            }
        };

        Shizuku.bindUserService(serviceArgs, serviceConnection);

        try {
            if (!bindLatch.await(BIND_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for Shizuku UserService to bind");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while binding Shizuku UserService", e);
        }
    }

    // ── Binder lifecycle ─────────────────────────────────────────

    private void onBinderReceived() {
        // Shizuku restarted — rebind if we were previously bound.
        if (shellService != null) {
            serviceBound.set(false);
            shellService = null;
            // The next install() call will re-bind via ensureBound().
        }
    }

    private void onBinderDead() {
        serviceBound.set(false);
        shellService = null;
    }

    // ── Cancel / destroy ─────────────────────────────────────────

    /**
     * Cancels any in-flight shell command.  Safe to call from any thread.
     */
    void cancel() {
        cancelled = true;
        IShellService svc = shellService;
        if (svc != null) {
            try {
                svc.cancel();
            } catch (RemoteException e) {
                Log.w(TAG, "cancel() failed", e);
            }
        }
    }

    /**
     * Unbinds from the UserService and releases all Shizuku listeners.
     * After calling this the backend must not be reused.
     */
    void destroy() {
        if (destroyed) return;
        destroyed = true;

        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);

        if (serviceConnection != null && serviceArgs != null) {
            Shizuku.unbindUserService(serviceArgs, serviceConnection, true);
        }
        shellService = null;
        serviceBound.set(false);
    }

    // ── ShellExecutor adapter ────────────────────────────────────

    private class UserServiceShellExecutor implements ShellExecutor {

        @Override
        public ShellExecutor.ShellResult execute(String... command) throws Exception {
            IShellService svc = checkService();
            cafe.bluearchive.installer.shizuku.ShellResult r =
                    svc.exec(command, EXEC_TIMEOUT_SEC);
            return convert(r);
        }

        @Override
        public ShellExecutor.ShellResult executeWithStdin(InputStream stdin, String... command)
                throws Exception {
            IShellService svc = checkService();

            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor readEnd = pipe[0];
            ParcelFileDescriptor writeEnd = pipe[1];

            // Write stdin on a background thread.
            Thread stdinThread = new Thread(() -> {
                try (OutputStream out = new ParcelFileDescriptor
                        .AutoCloseOutputStream(writeEnd)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while (!cancelled && (n = stdin.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                } catch (IOException e) {
                    if (!cancelled) {
                        Log.w(TAG, "Stdin write error", e);
                    }
                }
            }, "Shizuku-stdin");
            stdinThread.start();

            try {
                cafe.bluearchive.installer.shizuku.ShellResult r =
                        svc.execWithStdin(command, WRITE_TIMEOUT_SEC, readEnd);
                return convert(r);
            } finally {
                stdinThread.join(30_000);
            }
        }

        private IShellService checkService() throws IOException {
            IShellService svc = shellService;
            if (svc == null) {
                throw new IOException("Shizuku UserService is not bound");
            }
            return svc;
        }

        private ShellExecutor.ShellResult convert(
                cafe.bluearchive.installer.shizuku.ShellResult r) {
            return new ShellExecutor.ShellResult(
                    r.exitCode, r.stdout, r.stderr);
        }
    }
}
