package cafe.bluearchive.installer;

import org.json.JSONException;
import org.json.JSONObject;

final class ReleaseManifest {
    final String packageName;
    final String versionName;
    final long versionCode;
    final String apksUrl;
    final long apksSize;
    final String apksSha256;
    final String signerSha256;
    final String signedPayload;
    final String signature;

    private ReleaseManifest(String packageName, String versionName, long versionCode,
                            String apksUrl, long apksSize, String apksSha256,
                            String signerSha256, String signedPayload, String signature) {
        this.packageName = packageName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.apksUrl = apksUrl;
        this.apksSize = apksSize;
        this.apksSha256 = apksSha256;
        this.signerSha256 = signerSha256;
        this.signedPayload = signedPayload;
        this.signature = signature;
    }

    static ReleaseManifest parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        String payloadText = root.getString("payload");
        String signature = root.getString("signature");
        JSONObject payload = new JSONObject(payloadText);
        return new ReleaseManifest(
                payload.getString("packageName"),
                payload.optString("versionName", ""),
                payload.optLong("versionCode", -1),
                payload.getString("apksUrl"),
                payload.getLong("apksSize"),
                payload.getString("apksSha256").toLowerCase(java.util.Locale.ROOT),
                payload.optString("signerSha256", "").toLowerCase(java.util.Locale.ROOT),
                payloadText,
                signature);
    }
}
