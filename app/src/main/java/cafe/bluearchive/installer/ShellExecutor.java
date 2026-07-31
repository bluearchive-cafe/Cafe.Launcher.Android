package cafe.bluearchive.installer;

import java.io.InputStream;

/**
 * Abstraction over how a privileged shell command is executed.
 * <p>
 * Root uses libsu; Shizuku uses {@code Shizuku.newProcess()}.
 */
interface ShellExecutor {

    /**
     * Run a command and return its result.
     * <p>
     * This blocks until the process exits. The implementation must drain
     * stdout and stderr before returning.
     */
    ShellResult execute(String... command) throws Exception;

    /**
     * Run a command whose stdin is fed from {@code stdin}.
     * <p>
     * The implementation copies all bytes from {@code stdin} into the
     * process's stdin, then closes the stream, then waits for the process
     * to exit. Stdout and stderr are captured and returned.
     */
    ShellResult executeWithStdin(InputStream stdin, String... command) throws Exception;

    /** Result of a shell command. */
    final class ShellResult {
        final int exitCode;
        final String out;
        final String err;

        public ShellResult(int exitCode, String out, String err) {
            this.exitCode = exitCode;
            this.out = out;
            this.err = err;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
