package com.moud.core.update;

import java.util.Objects;

public record ReleaseArtifact(
        String file,
        String url,
        String format,
        long size,
        String sha256
) {
    public ReleaseArtifact {
        file = normalize(file);
        url = normalize(url);
        format = normalize(format);
        sha256 = normalize(sha256).toLowerCase();
    }

    public void validate(String context) {
        String prefix = context == null || context.isBlank() ? "artifact" : context;
        if (file.isBlank()) {
            throw new IllegalArgumentException(prefix + ": file is required");
        }
        if (url.isBlank()) {
            throw new IllegalArgumentException(prefix + ": url is required");
        }
        if (format.isBlank()) {
            throw new IllegalArgumentException(prefix + ": format is required");
        }
        if (size < 0L) {
            throw new IllegalArgumentException(prefix + ": size must be >= 0");
        }
        if (!ReleaseManifestVerifier.isSha256Hex(sha256)) {
            throw new IllegalArgumentException(prefix + ": sha256 must be 64 lowercase hex chars");
        }
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
