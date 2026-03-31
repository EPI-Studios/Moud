package com.moud.core.update;

public final class ReleaseSelector {
    private ReleaseSelector() {
    }

    public static ReleaseSelection select(ReleaseManifest manifest, String target, String currentVersion) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest is required");
        }
        String normalizedTarget = normalize(target);
        if (normalizedTarget.isBlank()) {
            throw new IllegalArgumentException("target is required");
        }
        ReleaseChannelArtifacts channelArtifacts = manifest.artifacts().get(normalizedTarget);
        if (channelArtifacts == null) {
            throw new IllegalArgumentException("manifest has no artifact target '" + normalizedTarget + "'");
        }

        String fromVersion = normalize(currentVersion);
        if (!fromVersion.isBlank()) {
            for (ReleasePatchArtifact patch : channelArtifacts.patches()) {
                if (patch != null
                        && fromVersion.equals(patch.fromVersion())
                        && manifest.version().equals(patch.toVersion())) {
                    return new ReleaseSelection(normalizedTarget, manifest.version(), true, patch.artifact(), patch.fromVersion());
                }
            }
        }

        return new ReleaseSelection(normalizedTarget, manifest.version(), false, channelArtifacts.full(), null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
