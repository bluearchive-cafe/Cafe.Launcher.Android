package cafe.bluearchive.installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class ApksDownloader {
    interface ProgressCallback {
        void onProgress(long downloaded, long total, long bytesPerSec, long elapsedMs);
    }

    static final class Result {
        final File file;
        final long bytes;
        final String sha256;
        final String headerSha256;

        Result(File file, long bytes, String sha256, String headerSha256) {
            this.file = file;
            this.bytes = bytes;
            this.sha256 = sha256;
            this.headerSha256 = headerSha256;
        }
    }

    private final DownloadLimits limits;
    private final int bufferSize;
    private final int progressIntervalMs;

    ApksDownloader(DownloadLimits limits, int bufferSize, int progressIntervalMs) {
        this.limits = limits;
        this.bufferSize = bufferSize;
        this.progressIntervalMs = progressIntervalMs;
    }

    Result download(String urlString, File dest, AtomicBoolean cancelled,
                    ProgressCallback progressCallback) throws IOException {
        HttpURLConnection conn = null;
        File partial = new File(dest.getParentFile(), dest.getName() + ".partial");
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("GET");
            conn.connect();

            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status);
            }

            long totalSize = contentLength(conn);
            if (totalSize > limits.maxArchiveBytes) {
                throw new IOException("Download is too large");
            }
            String headerSha256 = normalizedSha256Header(conn);

            deleteIfExists(partial);
            deleteIfExists(dest);

            MessageDigest digest = sha256Digest();
            long downloaded = 0;
            long startTime = System.currentTimeMillis();
            long lastUiUpdate = 0;
            byte[] buffer = new byte[bufferSize];

            try (InputStream raw = conn.getInputStream();
                 DigestInputStream in = new DigestInputStream(raw, digest);
                 FileOutputStream out = new FileOutputStream(partial)) {
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (cancelled != null && cancelled.get()) {
                        throw new DownloadCancelledException();
                    }
                    downloaded += count;
                    if (downloaded > limits.maxArchiveBytes) {
                        throw new IOException("Download exceeded size limit");
                    }
                    out.write(buffer, 0, count);

                    long now = System.currentTimeMillis();
                    if (progressCallback != null && now - lastUiUpdate >= progressIntervalMs) {
                        lastUiUpdate = now;
                        long elapsed = now - startTime;
                        long bps = elapsed > 0 ? downloaded * 1000 / elapsed : 0;
                        progressCallback.onProgress(downloaded, totalSize, bps, elapsed);
                    }
                }
            }

            if (totalSize >= 0 && downloaded != totalSize) {
                throw new IOException("Downloaded size does not match Content-Length");
            }

            String sha256 = toHex(digest.digest());
            if (headerSha256 != null && !headerSha256.equals(sha256)) {
                throw new IOException("Downloaded SHA-256 does not match response header");
            }

            if (!partial.renameTo(dest)) {
                throw new IOException("Could not promote partial download");
            }

            if (progressCallback != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                long bps = elapsed > 0 ? downloaded * 1000 / elapsed : 0;
                progressCallback.onProgress(downloaded, totalSize, bps, elapsed);
            }
            return new Result(dest, downloaded, sha256, headerSha256);
        } catch (IOException e) {
            deleteIfExists(partial);
            throw e;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    static String sha256(File file) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = new FileInputStream(file)) {
            int count;
            while ((count = in.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return toHex(digest.digest());
    }

    private static long contentLength(HttpURLConnection conn) {
        String value = conn.getHeaderField("Content-Length");
        if (value == null) return -1;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String normalizedSha256Header(HttpURLConnection conn) {
        String value = conn.getHeaderField("x-eos-hash-sha256");
        if (value == null || value.isEmpty()) {
            value = conn.getHeaderField("x-amz-content-sha256");
        }
        if (value == null) return null;
        value = value.trim().toLowerCase(Locale.ROOT);
        return value.matches("[0-9a-f]{64}") ? value : null;
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            out[i * 2] = hex[value >>> 4];
            out[i * 2 + 1] = hex[value & 0x0f];
        }
        return new String(out);
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete " + file.getName());
        }
    }

    static final class DownloadCancelledException extends IOException {
        DownloadCancelledException() {
            super("Download cancelled");
        }
    }
}
