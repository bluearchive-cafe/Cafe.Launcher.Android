package cafe.bluearchive.installer.shizuku;

import cafe.bluearchive.installer.shizuku.ShellResult;

interface IShellService {
    void destroy() = 16777114;

    ShellResult exec(in String[] cmd, int timeoutSec) = 1;

    ShellResult execWithStdin(in String[] cmd, int timeoutSec,
        in android.os.ParcelFileDescriptor stdinPipe) = 2;

    void cancel() = 3;
}
