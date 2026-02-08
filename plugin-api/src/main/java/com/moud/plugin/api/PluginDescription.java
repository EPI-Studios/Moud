package com.moud.plugin.api;

import java.util.Locale;
import java.util.Objects;

/**
 * Simple metadata container describing a plugin.
 */
public record PluginDescription(String id, String name, String version, String description) {

    public PluginDescription {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        description = description == null ? "" : description;

        id = sanitize(id);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Plugin id cannot be empty");
        }
        name = name.trim().isEmpty() ? id : name.trim();
        version = version.trim().isEmpty() ? "0.0.0" : version.trim();
    }

    private static String sanitize(String raw) {
        String normalized = raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "-");
        while (normalized.contains("--")) {
            normalized = normalized.replace("--", "-");
        }
        return normalized;
    }
}
