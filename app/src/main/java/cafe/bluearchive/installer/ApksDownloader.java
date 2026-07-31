package cafe.bluearchive.installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLException;

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

        // Follow redirects manually because Android's built-in
        // HttpURLConnection.setInstanceFollowRedirects(true) refuses to follow
        // HTTPS → HTTP downgrades, which many CDNs use for content delivery.
        int maxRedirects = 5;
        for (int redirectCount = 0; redirectCount <= maxRedirects; redirectCount++) {
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestMethod("GET");
                conn.connect();

                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        status == 307 ||
                        status == 308) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn = null;
                    if (location == null || location.isEmpty()) {
                        throw new IOException("HTTP " + status + " with no Location header");
                    }
                    urlString = new URL(new URL(urlString), location).toString();
                    continue;
                }

                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException(describeHttpStatus(status));
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
                // Don't wrap DownloadCancelledException
                if (e instanceof DownloadCancelledException) {
                    throw e;
                }
                throw wrapNetworkError(e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw new IOException("Too many redirects");
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

    private static IOException wrapNetworkError(IOException original) {
        Throwable cause = original.getCause();
        if (cause instanceof SocketTimeoutException) {
            return new IOException("Connection timed out", original);
        }
        if (cause instanceof UnknownHostException || original instanceof UnknownHostException) {
            return new IOException("DNS resolution failed", original);
        }
        if (cause instanceof SSLException || original instanceof SSLException) {
            return new IOException("TLS handshake failed", original);
        }
        if (cause instanceof ConnectException || original instanceof ConnectException) {
            return new IOException("Connection refused", original);
        }
        // Surface the real error message for generic IOExceptions so
        // users see the actual problem (e.g. "Cleartext HTTP traffic not permitted")
        // instead of just "Network error".
        if (original.getMessage() != null
                && !"Download cancelled".equals(original.getMessage())) {
            return original;
        }
        return original;
    }

    private static String describeHttpStatus(int status) {
        String detail;
        switch (status) {
            case 400: detail = " (Bad Request)"; break;
            case 401: detail = " (Unauthorized)"; break;
            case 403: detail = " (Forbidden)"; break;
            case 404: detail = " (Not Found — the requested file was not found on the server)"; break;
            case 405: detail = " (Method Not Allowed)"; break;
            case 408: detail = " (Request Timeout)"; break;
            case 429: detail = " (Too Many Requests — try again later)"; break;
            case 500: detail = " (Internal Server Error)"; break;
            case 502: detail = " (Bad Gateway)"; break;
            case 503: detail = " (Service Unavailable)"; break;
            case 504: detail = " (Gateway Timeout)"; break;
            default: detail = status >= 500 ? " (Server Error)"
                       : status >= 400 ? " (Client Error)" : ""; break;
        }
        return "HTTP " + status + detail;
    }
}
