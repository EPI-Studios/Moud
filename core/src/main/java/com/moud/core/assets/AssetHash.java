package com.moud.core.assets;

import com.moud.core.ValidationResult;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

public record AssetHash(String hex) {
    public static final int HEX_LEN = 64;

    public AssetHash {
        Objects.requireNonNull(hex, "hex");
        hex = hex.toLowerCase(Locale.ROOT);
        ValidationResult validation = validate(hex);
        if (!validation.ok()) {
            throw new IllegalArgumentException("Invalid asset hash: " + validation.message());
        }
    }

    public static AssetHash fromHex(String hex) {
        return new AssetHash(hex);
    }

    public static AssetHash sha256(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(bytes);
            return new AssetHash(toHex(out));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static ValidationResult validate(String hex) {
        if (hex == null) {
            return ValidationResult.failure("hex is null");
        }
        if (hex.length() != HEX_LEN) {
            return ValidationResult.failure("expected " + HEX_LEN + " hex chars");
        }
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!ok) {
                return ValidationResult.failure("invalid hex char at " + i + ": '" + c + "'");
            }
        }
        return ValidationResult.success();
    }

    private static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        int j = 0;
        for (byte b : bytes) {
            int v = b & 0xFF;
            chars[j++] = hexDigit(v >>> 4);
            chars[j++] = hexDigit(v & 0x0F);
        }
        return new String(chars);
    }

    private static char hexDigit(int v) {
        return (char) (v < 10 ? ('0' + v) : ('a' + (v - 10)));
    }
}

