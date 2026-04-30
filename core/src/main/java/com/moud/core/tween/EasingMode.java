package com.moud.core.tween;

public enum EasingMode {
    LINEAR,
    SINE_IN, SINE_OUT, SINE_IN_OUT,
    QUAD_IN, QUAD_OUT, QUAD_IN_OUT,
    CUBIC_IN, CUBIC_OUT, CUBIC_IN_OUT,
    EXPO_IN, EXPO_OUT, EXPO_IN_OUT,
    BACK_IN, BACK_OUT, BACK_IN_OUT,
    ELASTIC_IN, ELASTIC_OUT, ELASTIC_IN_OUT,
    BOUNCE_IN, BOUNCE_OUT, BOUNCE_IN_OUT;

    public static EasingMode parse(String raw, EasingMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String key = raw.trim().replace('-', '_').toUpperCase();
        for (EasingMode mode : values()) {
            if (mode.name().equals(key)) {
                return mode;
            }
        }
        return switch (key) {
            case "SINEINOUT" -> SINE_IN_OUT;
            case "QUADINOUT" -> QUAD_IN_OUT;
            case "CUBICINOUT" -> CUBIC_IN_OUT;
            case "EXPOINOUT" -> EXPO_IN_OUT;
            case "BACKINOUT" -> BACK_IN_OUT;
            case "ELASTICINOUT" -> ELASTIC_IN_OUT;
            case "BOUNCEINOUT" -> BOUNCE_IN_OUT;
            case "EASEIN", "EASE_IN" -> SINE_IN;
            case "EASEOUT", "EASE_OUT" -> SINE_OUT;
            case "EASEINOUT", "EASE_IN_OUT" -> SINE_IN_OUT;
            default -> fallback;
        };
    }
}
