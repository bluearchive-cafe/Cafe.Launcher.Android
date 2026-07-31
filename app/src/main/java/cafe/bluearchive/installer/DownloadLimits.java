package cafe.bluearchive.installer;

final class DownloadLimits {
    static final int DEFAULT_MAX_SPLIT_COUNT = 128;
    static final long DEFAULT_MAX_ARCHIVE_BYTES = 3L * 1024L * 1024L * 1024L;
    static final long DEFAULT_MAX_TOTAL_SPLIT_BYTES = 3L * 1024L * 1024L * 1024L;
    static final long DEFAULT_MAX_SINGLE_SPLIT_BYTES = 2L * 1024L * 1024L * 1024L;

    final long maxArchiveBytes;
    final long maxTotalSplitBytes;
    final long maxSingleSplitBytes;
    final int maxSplitCount;

    DownloadLimits(long maxArchiveBytes, long maxTotalSplitBytes,
                   long maxSingleSplitBytes, int maxSplitCount) {
        if (maxArchiveBytes <= 0 || maxTotalSplitBytes <= 0 || maxSingleSplitBytes <= 0) {
            throw new IllegalArgumentException("Download limits must be positive");
        }
        if (maxSplitCount <= 0) {
            throw new IllegalArgumentException("Split count limit must be positive");
        }
        this.maxArchiveBytes = maxArchiveBytes;
        this.maxTotalSplitBytes = maxTotalSplitBytes;
        this.maxSingleSplitBytes = maxSingleSplitBytes;
        this.maxSplitCount = maxSplitCount;
    }

    static DownloadLimits defaults() {
        return new DownloadLimits(
                DEFAULT_MAX_ARCHIVE_BYTES,
                DEFAULT_MAX_TOTAL_SPLIT_BYTES,
                DEFAULT_MAX_SINGLE_SPLIT_BYTES,
                DEFAULT_MAX_SPLIT_COUNT);
    }
}
