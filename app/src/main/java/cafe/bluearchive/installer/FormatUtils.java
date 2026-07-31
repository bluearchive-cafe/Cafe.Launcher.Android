package cafe.bluearchive.installer;

import java.util.Locale;

final class FormatUtils {
    private FormatUtils() { }

    static String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        } else {
            return bytes + " B";
        }
    }
}
