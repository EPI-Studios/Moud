package com.moud.core.update;

import java.util.Objects;

public record ReleasePatchArtifact(
        String fromVersion,
        String toVersion,
        ReleaseArtifact artifact
) {
    public ReleasePatchArtifact {
        fromVersion = normalize(fromVersion);
        toVersion = normalize(toVersion);
    }

    public void validate(String context) {
        String prefix = context == null || context.isBlank() ? "patch" : context;
        if (fromVersion.isBlank()) {
            throw new IllegalArgumentException(prefix + ": fromVersion is required");
        }
        if (toVersion.isBlank()) {
            throw new IllegalArgumentException(prefix + ": toVersion is required");
        }
        if (artifact == null) {
            throw new IllegalArgumentException(prefix + ": artifact is required");
        }
        artifact.validate(prefix + ".artifact");
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
