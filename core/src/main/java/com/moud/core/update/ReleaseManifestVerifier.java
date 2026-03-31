package com.moud.core.update;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class ReleaseManifestVerifier {
    private ReleaseManifestVerifier() {
    }

    public static ReleaseManifest verifyAndParse(String manifestJson, String base64Signature, String publicKeyPem) {
        if (!verifySignature(manifestJson, base64Signature, publicKeyPem)) {
            throw new IllegalArgumentException("release manifest signature verification failed");
        }
        return ReleaseManifestParser.parse(manifestJson);
    }

    public static boolean verifySignature(String manifestJson, String base64Signature, String publicKeyPem) {
        if (manifestJson == null || manifestJson.isBlank()) {
            throw new IllegalArgumentException("manifestJson is empty");
        }
        if (base64Signature == null || base64Signature.isBlank()) {
            throw new IllegalArgumentException("base64Signature is empty");
        }
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalArgumentException("publicKeyPem is empty");
        }
        try {
            PublicKey key = parseEd25519PublicKey(publicKeyPem);
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature.trim());
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update(manifestJson.getBytes(StandardCharsets.UTF_8));
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("signature verification setup failed: " + e.getMessage(), e);
        }
    }

    public static PublicKey parseEd25519PublicKey(String publicKeyPem) {
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalArgumentException("publicKeyPem is empty");
        }
        try {
            String normalized = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("invalid Ed25519 public key: " + e.getMessage(), e);
        }
    }

    public static boolean isSha256Hex(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lowerHex = c >= 'a' && c <= 'f';
            if (!digit && !lowerHex) {
                return false;
            }
        }
        return true;
    }
}
