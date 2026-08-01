package cafe.bluearchive.installer;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.pm.PackageInfoCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        return parse(apksFile, null, null, false);
    }

    ApksArchive parse(File apksFile, PackageManager pm) throws IOException {
        return parse(apksFile, pm, null, false);
    }

    /**
     * Parses the APKS container, optionally extracting package metadata from
     * {@code base.apk} by calling
     * {@link PackageManager#getPackageArchiveInfo(String, int)}.
     */
    ApksArchive parse(File apksFile, PackageManager pm, File tempDir,
                      boolean requirePackageMetadata) throws IOException {
        List<ApksArchive.Split> splits = new ArrayList<>();
        Set<String> displayNames = new HashSet<>();
        long totalBytes = 0;
        boolean hasBaseApk = false;
        String apkPackageName = null;
        String apkVersionName = null;
        long apkVersionCode = -1;
        String signerSha256 = null;

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
                    if (pm != null) {
                        PackageInfo pi = readPackageArchiveInfo(zip, entry, pm, tempDir,
                                requirePackageMetadata);
                        if (pi != null) {
                            apkPackageName = pi.packageName;
                            apkVersionName = pi.versionName;
                            apkVersionCode = PackageInfoCompat.getLongVersionCode(pi);
                            try {
                                signerSha256 = firstSignerSha256(pi);
                            } catch (NoSuchAlgorithmException e) {
                                throw new IOException("SHA-256 is not available", e);
                            }
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
        if (requirePackageMetadata && (apkPackageName == null || apkPackageName.isEmpty())) {
            throw new ZipException("Could not read package metadata from base.apk");
        }

        Collections.sort(splits, (left, right) -> left.displayName.compareTo(right.displayName));
        return new ApksArchive(splits, totalBytes, apkPackageName, apkVersionName,
                apkVersionCode, signerSha256);
    }

    private PackageInfo readPackageArchiveInfo(ZipFile zip, ZipEntry entry,
                                               PackageManager pm, File tempDir,
                                               boolean requirePackageMetadata)
            throws IOException {
        File tempBaseApk = null;
        try {
            tempBaseApk = File.createTempFile("base-", ".apk", tempDir);
            try (InputStream in = zip.getInputStream(entry);
                 FileOutputStream out = new FileOutputStream(tempBaseApk)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }
            PackageInfo pi = pm.getPackageArchiveInfo(tempBaseApk.getAbsolutePath(), packageInfoFlags());
            if (pi == null && requirePackageMetadata) {
                throw new ZipException("Could not parse package metadata from base.apk");
            }
            return pi;
        } catch (IOException e) {
            if (requirePackageMetadata) throw e;
            return null;
        } catch (Exception e) {
            if (requirePackageMetadata) {
                ZipException zipException = new ZipException("Could not parse package metadata from base.apk");
                zipException.initCause(e);
                throw zipException;
            }
            return null;
        } finally {
            if (tempBaseApk != null && !tempBaseApk.delete()) {
                tempBaseApk.deleteOnExit();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static int packageInfoFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return PackageManager.GET_SIGNING_CERTIFICATES;
        }
        return PackageManager.GET_SIGNATURES;
    }

    @SuppressWarnings("deprecation")
    private static String firstSignerSha256(PackageInfo pi) throws NoSuchAlgorithmException {
        android.content.pm.Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && pi.signingInfo != null) {
            signatures = pi.signingInfo.hasMultipleSigners()
                    ? pi.signingInfo.getApkContentsSigners()
                    : pi.signingInfo.getSigningCertificateHistory();
        } else if (pi.signatures != null) {
            signatures = pi.signatures;
        }
        if (signatures == null || signatures.length == 0) {
            return null;
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(signatures[0].toByteArray());
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }
}
