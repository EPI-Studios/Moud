package com.moud.core.mesh.source;

import java.util.Locale;

public enum MeshAuthority {
    SERVER,
    CLIENT;

    public static MeshAuthority parse(String value, MeshAuthority fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "server" -> SERVER;
            case "client" -> CLIENT;
            default -> fallback;
        };
    }
}
