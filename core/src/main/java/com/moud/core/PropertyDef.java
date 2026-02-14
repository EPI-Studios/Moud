package com.moud.core;

import java.util.Map;
import java.util.Objects;

public record PropertyDef(
        String key,
        PropertyType type,
        String defaultValue,
        String displayName,
        String category,
        int order,
        Map<String, String> editorHints
) {
    public PropertyDef(String key, PropertyType type) {
        this(key, type, null);
    }

    public PropertyDef(String key, PropertyType type, String defaultValue) {
        this(key, type, defaultValue, null, null, 0, Map.of());
    }

    public PropertyDef {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        if (displayName == null) {
            displayName = "";
        }
        if (category == null) {
            category = "";
        }
        if (editorHints == null) {
            editorHints = Map.of();
        } else {
            editorHints = Map.copyOf(editorHints);
        }
    }

    public String uiLabel() {
        if (displayName == null || displayName.isBlank()) {
            return key;
        }
        return displayName;
    }
}
