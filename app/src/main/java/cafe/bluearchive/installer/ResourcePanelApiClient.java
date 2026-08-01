package cafe.bluearchive.installer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

final class ResourcePanelApiClient {
    private static final String BASE_URL = "https://api.bluearchive.cafe";
    private static final int TIMEOUT_MS = 10_000;
    private static final int RETRIES = 2;
    private static final long RETRY_DELAY_MS = 800L;

    Map<ResourcePanelModels.ResourceCode, ResourcePanelModels.VersionPair> fetchStatus()
            throws IOException, JSONException {
        JSONObject json = new JSONObject(get(BASE_URL + "/status/list", false));
        Map<ResourcePanelModels.ResourceCode, ResourcePanelModels.VersionPair> result = new EnumMap<>(ResourcePanelModels.ResourceCode.class);
        for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
            JSONObject item = json.optJSONObject(code.wireName);
            if (item == null) {
                result.put(code, new ResourcePanelModels.VersionPair(null, null));
                continue;
            }
            result.put(code, new ResourcePanelModels.VersionPair(
                    versionFrom(item.optJSONObject("official")),
                    versionFrom(item.optJSONObject("localized"))));
        }
        return result;
    }

    Map<ResourcePanelModels.ResourceCode, String> fetchConfig(String uid)
            throws IOException, JSONException {
        String body = get(BASE_URL + "/config/get?uid=" + encode(uid), true);
        Map<ResourcePanelModels.ResourceCode, String> result = defaultConfig();
        if (body == null || body.trim().isEmpty()) {
            return result;
        }
        JSONObject json = new JSONObject(body);
        for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
            String value = json.optString(code.wireName, ResourcePanelModels.MODE_JP);
            result.put(code, ResourcePanelModels.MODE_CN.equals(value)
                    ? ResourcePanelModels.MODE_CN
                    : ResourcePanelModels.MODE_JP);
        }
        return result;
    }

    void saveConfig(String uid, Map<ResourcePanelModels.ResourceCode, String> modes) throws IOException {
        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/config/set?uid=").append(encode(uid));
        for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
            String mode = modes.get(code);
            url.append('&').append(code.wireName).append('=').append(encode(
                    ResourcePanelModels.MODE_CN.equals(mode)
                            ? ResourcePanelModels.MODE_CN
                            : ResourcePanelModels.MODE_JP));
        }
        get(url.toString(), false);
    }

    private static Map<ResourcePanelModels.ResourceCode, String> defaultConfig() {
        Map<ResourcePanelModels.ResourceCode, String> result = new EnumMap<>(ResourcePanelModels.ResourceCode.class);
        for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
            result.put(code, ResourcePanelModels.MODE_JP);
        }
        return result;
    }

    private static String versionFrom(JSONObject object) {
        return object == null ? null : object.optString("version", null);
    }

    private static String get(String url, boolean notFoundIsEmpty) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= RETRIES; attempt++) {
            try {
                return getOnce(url, notFoundIsEmpty);
            } catch (IOException e) {
                if (!isRetryable(e) || attempt == RETRIES) {
                    throw e;
                }
                last = e;
                sleepBeforeRetry(attempt + 1);
            }
        }
        throw last != null ? last : new IOException("Request failed");
    }

    private static String getOnce(String url, boolean notFoundIsEmpty) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(true);
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND && notFoundIsEmpty) {
                return "{}";
            }
            if (status < 200 || status >= 300) {
                throw new HttpStatusException(status, readStream(connection.getErrorStream()));
            }
            return readStream(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static boolean isRetryable(IOException e) {
        return e instanceof SocketTimeoutException || e instanceof SocketException;
    }

    private static void sleepBeforeRetry(int retryNumber) throws IOException {
        try {
            Thread.sleep(RETRY_DELAY_MS * retryNumber);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    static final class HttpStatusException extends IOException {
        final int statusCode;
        final String body;

        HttpStatusException(int statusCode, String body) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
