package com.moud.core.update;

public record ReleaseSelection(
        String target,
        String version,
        boolean patch,
        ReleaseArtifact artifact,
        String fromVersion
) {
    public ReleaseSelection {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("target is required");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
        if (artifact == null) {
            throw new IllegalArgumentException("artifact is required");
        }
        if (patch && (fromVersion == null || fromVersion.isBlank())) {
            throw new IllegalArgumentException("fromVersion is required for patch selections");
        }
    }
}
