package com.moud.core.interp;

public enum InterpMode {
    SNAP,
    LINEAR,
    SLERP,
    HERMITE;

    public static InterpMode parse(String raw, InterpMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String key = raw.trim().toLowerCase();
        return switch (key) {
            case "snap", "step", "off", "none" -> SNAP;
            case "linear", "lerp" -> LINEAR;
            case "slerp", "smooth" -> SLERP;
            case "hermite", "spline", "cubic" -> HERMITE;
            default -> fallback;
        };
    }
}
