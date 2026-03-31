package com.moud.core.update;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record ReleaseManifest(
        int schemaVersion,
        String version,
        String channel,
        String publishedAt,
        Map<String, ReleaseChannelArtifacts> artifacts
) {
    public ReleaseManifest {
        version = normalize(version);
        channel = normalize(channel).toLowerCase(Locale.ROOT);
        publishedAt = normalize(publishedAt);
        artifacts = artifacts == null ? Map.of() : Map.copyOf(artifacts);
    }

    public void validate() {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be > 0");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
        if (channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (publishedAt.isBlank()) {
            throw new IllegalArgumentException("publishedAt is required");
        }
        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("artifacts is required");
        }
        for (Map.Entry<String, ReleaseChannelArtifacts> entry : artifacts.entrySet()) {
            String key = normalize(entry.getKey());
            if (key.isBlank()) {
                throw new IllegalArgumentException("artifact target key is blank");
            }
            ReleaseChannelArtifacts value = Objects.requireNonNull(entry.getValue(), "artifacts[" + key + "]");
            value.validate("artifacts." + key);
        }
    }

    public ReleaseChannelArtifacts client() {
        return artifacts.get("client");
    }

    public ReleaseChannelArtifacts server() {
        return artifacts.get("server");
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
