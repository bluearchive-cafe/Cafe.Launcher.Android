package cafe.bluearchive.installer;

import android.util.Log;

import java.io.File;
import java.io.IOException;

final class FileCleanup {
    private FileCleanup() {
    }

    static void deleteRequired(File file) throws IOException {
        if (file != null && file.exists() && !file.delete()) {
            throw new IOException("Could not delete " + file.getName());
        }
    }

    static void deleteBestEffort(String tag, File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (!file.delete()) {
            Log.w(tag, "Could not delete " + file.getAbsolutePath());
        }
    }
}
