package cafe.bluearchive.installer;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

final class ApksAcceptanceValidator {
    private final Context context;
    private final DownloadLimits limits;

    ApksAcceptanceValidator(Context context, DownloadLimits limits) {
        this.context = context.getApplicationContext();
        this.limits = limits;
    }

    ApksArchive validate(File apksFile, long expectedSize, String expectedSha256,
                         ReleaseManifest manifest, String expectedPackageName)
            throws IOException {
        if (expectedSize >= 0 && apksFile.length() != expectedSize) {
            throw new IOException(context.getString(R.string.error_manifest_size_mismatch));
        }

        String normalizedExpectedSha256 = normalizeSha256(expectedSha256);
        if (normalizedExpectedSha256 != null) {
            String actualSha256 = ApksDownloader.sha256(apksFile);
            if (!normalizedExpectedSha256.equals(actualSha256)) {
                throw new IOException(context.getString(R.string.error_manifest_hash_mismatch));
            }
        }

        ApksArchive archive = new ApksArchiveParser(limits)
                .parse(apksFile, context.getPackageManager(), context.getCacheDir(), true);
        String archivePackage = archive.packageName();
        if (!expectedPackageName.equals(archivePackage)) {
            throw new IOException("Install package identity does not match the expected package");
        }
        if (manifest != null) {
            if (!manifest.packageName.equals(archivePackage)) {
                throw new IOException("Install package identity does not match the signed manifest");
            }
            String expectedSigner = normalizeSha256(manifest.signerSha256);
            if (expectedSigner != null) {
                String actualSigner = normalizeSha256(archive.signerSha256());
                if (actualSigner == null) {
                    throw new IOException("Install package signer could not be verified");
                }
                if (!expectedSigner.equals(actualSigner)) {
                    throw new IOException("Install package signer does not match the signed manifest");
                }
            }
        }
        return archive;
    }

    private static String normalizeSha256(String value) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IOException("Expected SHA-256 digest is invalid");
        }
        return normalized;
    }
}
