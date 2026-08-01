package cafe.bluearchive.installer;

import android.util.Base64;

import org.json.JSONException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

final class ReleaseManifestVerifier {
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private final String expectedPackageName;
    private final PublicKey publicKey;

    ReleaseManifestVerifier(String expectedPackageName, String base64PublicKey)
            throws GeneralSecurityException {
        this.expectedPackageName = expectedPackageName;
        this.publicKey = parsePublicKey(base64PublicKey);
    }

    ReleaseManifest verify(String json) throws GeneralSecurityException, JSONException {
        ReleaseManifest manifest = ReleaseManifest.parse(json);
        Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
        verifier.initVerify(publicKey);
        verifier.update(manifest.signedPayload.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = Base64.decode(manifest.signature, Base64.DEFAULT);
        if (!verifier.verify(signatureBytes)) {
            throw new GeneralSecurityException("Release manifest signature is invalid");
        }
        if (!expectedPackageName.equals(manifest.packageName)) {
            throw new GeneralSecurityException("Release manifest package does not match target package");
        }
        if (!manifest.apksSha256.matches("[0-9a-f]{64}")) {
            throw new GeneralSecurityException("Release manifest APKS digest is invalid");
        }
        if (!manifest.signerSha256.isEmpty()
                && !manifest.signerSha256.matches("[0-9a-f]{64}")) {
            throw new GeneralSecurityException("Release manifest signer digest is invalid");
        }
        if (manifest.apksSize <= 0) {
            throw new GeneralSecurityException("Release manifest APKS size is invalid");
        }
        URI uri;
        try {
            uri = URI.create(manifest.apksUrl);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Release manifest APKS URL is invalid", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new GeneralSecurityException("Release manifest APKS URL must use HTTPS");
        }
        return manifest;
    }

    private static PublicKey parsePublicKey(String base64PublicKey) throws GeneralSecurityException {
        if (base64PublicKey == null || base64PublicKey.trim().isEmpty()) {
            throw new GeneralSecurityException("Release manifest public key is not configured");
        }
        byte[] encoded = Base64.decode(base64PublicKey.trim(), Base64.DEFAULT);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    }
}
