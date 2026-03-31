package com.moud.core.update;

import java.util.List;
import java.util.Map;

public record UpdateState(
        int schemaVersion,
        String channel,
        String currentVersion,
        String lastKnownGoodVersion,
        String lastManifestVersion,
        Map<String, String> installedVersions,
        List<String> failedVersions
) {
    public UpdateState {
        channel = normalize(channel);
        currentVersion = normalize(currentVersion);
        lastKnownGoodVersion = normalize(lastKnownGoodVersion);
        lastManifestVersion = normalize(lastManifestVersion);
        installedVersions = installedVersions == null ? Map.of() : Map.copyOf(installedVersions);
        failedVersions = failedVersions == null ? List.of() : List.copyOf(failedVersions);
    }

    public static UpdateState empty(String channel) {
        return new UpdateState(1, channel, "", "", "", Map.of(), List.of());
    }

    public void validate() {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be > 0");
        }
        if (channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
