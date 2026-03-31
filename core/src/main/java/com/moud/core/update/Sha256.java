package com.moud.core.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Sha256 {
    private Sha256() {
    }

    public static String hex(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes is null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String hex(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean matches(Path path, String expectedSha256) throws IOException {
        String normalized = expectedSha256 == null ? "" : expectedSha256.trim().toLowerCase();
        return ReleaseManifestVerifier.isSha256Hex(normalized) && normalized.equals(hex(path));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            sb.append(Character.forDigit((value >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(value & 0xF, 16));
        }
        return sb.toString();
    }
}
