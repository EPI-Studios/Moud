package com.moud.server.minestom.collision;

import java.util.Locale;

public enum CollisionStrategy {
    AUTO,
    BOX,
    SPHERE,
    CAPSULE,
    CONVEX,
    VHACD,
    MESH;

    public static CollisionStrategy parse(String value, CollisionStrategy fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "box" -> BOX;
            case "sphere" -> SPHERE;
            case "capsule" -> CAPSULE;
            case "convex", "hull" -> CONVEX;
            case "vhacd", "compound" -> VHACD;
            case "mesh", "concave" -> MESH;
            case "auto" -> AUTO;
            default -> fallback;
        };
    }
}
