package cafe.bluearchive.installer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ApksArchive {
    private final List<Split> splits;
    private final long totalBytes;
    private final String packageName;
    private final String versionName;
    private final long versionCode;

    ApksArchive(List<Split> splits, long totalBytes) {
        this(splits, totalBytes, null, null, -1);
    }

    ApksArchive(List<Split> splits, long totalBytes,
                String packageName, String versionName, long versionCode) {
        this.splits = Collections.unmodifiableList(new ArrayList<>(splits));
        this.totalBytes = totalBytes;
        this.packageName = packageName;
        this.versionName = versionName;
        this.versionCode = versionCode;
    }

    List<Split> splits() {
        return splits;
    }

    int splitCount() {
        return splits.size();
    }

    long totalBytes() {
        return totalBytes;
    }

    Split splitAt(int index) {
        return splits.get(index);
    }

    /** Package name from the embedded base.apk, or null when unavailable. */
    String packageName() { return packageName; }

    /** Version name from the embedded base.apk, or null when unavailable. */
    String versionName() { return versionName; }

    /** Version code from the embedded base.apk, or -1 when unavailable. */
    long versionCode() { return versionCode; }

    static final class Split {
        final String displayName;
        final String entryName;
        final long size;

        Split(String displayName, String entryName, long size) {
            this.displayName = displayName;
            this.entryName = entryName;
            this.size = size;
        }
    }
}
