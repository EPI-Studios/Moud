package com.moud.core;

import java.util.Map;
import java.util.Objects;

public record NodeTypeDef(
        String typeId,
        String displayName,
        String category,
        int order,
        Map<String, PropertyDef> properties
) {
    public NodeTypeDef(String typeId, Map<String, PropertyDef> properties) {
        this(typeId, null, null, 0, properties);
    }

    public NodeTypeDef {
        Objects.requireNonNull(typeId, "typeId");
        if (displayName == null) {
            displayName = "";
        }
        if (category == null) {
            category = "";
        }
        if (properties == null) {
            properties = Map.of();
        } else {
            properties = Map.copyOf(properties);
        }
    }

    public String uiLabel() {
        if (displayName == null || displayName.isBlank()) {
            return typeId;
        }
        return displayName;
    }
}
