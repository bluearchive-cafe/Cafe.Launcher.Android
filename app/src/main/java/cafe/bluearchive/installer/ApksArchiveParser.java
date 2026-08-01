package cafe.bluearchive.installer;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.core.content.pm.PackageInfoCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        String apkPackageName = null;
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
                    // Extract package/version info from base.apk via PackageManager.
                    if (pm != null) {
                        PackageInfo pi = readPackageArchiveInfo(zip, entry, pm);
                        if (pi != null) {
                            apkPackageName = pi.packageName;
                            apkVersionName = pi.versionName;
                            apkVersionCode = PackageInfoCompat.getLongVersionCode(pi);
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
        return new ApksArchive(splits, totalBytes, apkPackageName, apkVersionName, apkVersionCode);
    }

    private PackageInfo readPackageArchiveInfo(ZipFile zip, ZipEntry entry,
                                               PackageManager pm) {
        File tempBaseApk = null;
        try {
            tempBaseApk = File.createTempFile("base-", ".apk");
            try (InputStream in = zip.getInputStream(entry);
                 FileOutputStream out = new FileOutputStream(tempBaseApk)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }
            return pm.getPackageArchiveInfo(tempBaseApk.getAbsolutePath(), 0);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (tempBaseApk != null) {
                tempBaseApk.delete();
            }
        }
    }
}
