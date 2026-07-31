package cafe.bluearchive.installer;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

final class ApksArchiveParser {
    private final DownloadLimits limits;

    ApksArchiveParser(DownloadLimits limits) {
        this.limits = limits;
    }

    ApksArchive parse(File apksFile) throws IOException {
        return parse(apksFile, null);
    }

    /**
     * Parses the APKS container, optionally extracting version info from
     * {@code base.apk} by calling
     * {@link PackageManager#getPackageArchiveInfo(String, int)}.
     */
    ApksArchive parse(File apksFile,
                      PackageManager pm) throws IOException {
        List<ApksArchive.Split> splits = new ArrayList<>();
        Set<String> displayNames = new HashSet<>();
        long totalBytes = 0;
        boolean hasBaseApk = false;
        String apkVersionName = null;
        long apkVersionCode = -1;

        try (ZipFile zip = new ZipFile(apksFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;

                String entryName = entry.getName();
                if (!entryName.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;

                if (splits.size() >= limits.maxSplitCount) {
                    throw new ZipException("Too many APK splits in APKS file");
                }

                long size = entry.getSize();
                if (size <= 0) {
                    throw new ZipException("Invalid APK split size: " + entryName);
                }
                if (size > limits.maxSingleSplitBytes) {
                    throw new ZipException("APK split is too large: " + entryName);
                }

                String displayName = new File(entryName).getName();
                if (!displayNames.add(displayName)) {
                    throw new ZipException("Duplicate APK filename in APKS file: " + displayName);
                }
                if ("base.apk".equals(displayName)) {
                    hasBaseApk = true;
                    // Extract version info from base.apk via PackageManager
                    // (uses the ZIP entry path as the archive path).
                    if (pm != null) {
                        String archivePath = apksFile.getAbsolutePath() + "!/" + entryName;
                        try {
                            PackageInfo pi = pm.getPackageArchiveInfo(
                                    archivePath, 0);
                            if (pi != null) {
                                apkVersionName = pi.versionName;
                                apkVersionCode = pi.versionCode;
                            }
                        } catch (Exception ignored) {
                            // Version info is best-effort; ignore failures.
                        }
                    }
                }

                try {
                    totalBytes = Math.addExact(totalBytes, size);
                } catch (ArithmeticException e) {
                    throw new ZipException("APKS total size overflow");
                }
                if (totalBytes > limits.maxTotalSplitBytes) {
                    throw new ZipException("APKS split data is too large");
                }

                splits.add(new ApksArchive.Split(displayName, entryName, size));
            }
        }

        if (splits.isEmpty()) {
            throw new ZipException("APKS file contains no APK splits");
        }
        if (!hasBaseApk) {
            throw new ZipException("APKS file is missing base.apk");
        }

        Collections.sort(splits, (left, right) -> left.displayName.compareTo(right.displayName));
        return new ApksArchive(splits, totalBytes, apkVersionName, apkVersionCode);
    }
}
