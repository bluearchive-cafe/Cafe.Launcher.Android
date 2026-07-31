package cafe.bluearchive.installer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ApksArchive {
    private final List<Split> splits;
    private final long totalBytes;

    ApksArchive(List<Split> splits, long totalBytes) {
        this.splits = Collections.unmodifiableList(new ArrayList<>(splits));
        this.totalBytes = totalBytes;
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
