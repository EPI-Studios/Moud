package com.moud.core.mesh;

import java.util.Locale;

public enum MeshPrimitive {
    TRIANGLES;

    public static MeshPrimitive parse(String value) {
        if (value == null || value.isBlank()) {
            return TRIANGLES;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "triangles", "tris", "triangle_list" -> TRIANGLES;
            default -> throw new IllegalArgumentException("unsupported mesh primitive: " + value);
        };
    }
}
