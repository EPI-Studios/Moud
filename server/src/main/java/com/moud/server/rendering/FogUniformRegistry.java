package com.moud.server.rendering;

import java.util.Map;

public final class FogUniformRegistry {
    private FogUniformRegistry() {
    }

    public static Map<String, Object> sanitizeFog(Map<String, Object> raw) {
        Map<String, Object> base = defaultFog();
        if (raw == null) return base;
        return Map.of(
                "FogStart", clamp(raw.getOrDefault("FogStart", base.get("FogStart")), -256f, 1024f),
                "FogEnd", clamp(raw.getOrDefault("FogEnd", base.get("FogEnd")), -256f, 4096f),
                "FogColor", color(raw.getOrDefault("FogColor", base.get("FogColor"))),
                "FogShape", (int) clamp(raw.getOrDefault("FogShape", base.get("FogShape")), 0f, 1f)
        );
    }

    public static Map<String, Object> sanitizeHeightFog(Map<String, Object> raw) {
        Map<String, Object> base = defaultHeightFog();
        if (raw == null) return base;
        return Map.of(
                "FOG_Y", clamp(raw.getOrDefault("FOG_Y", base.get("FOG_Y")), -512f, 1024f),
                "THICKNESS", clamp(raw.getOrDefault("THICKNESS", base.get("THICKNESS")), 0.01f, 4f),
                "FogColor", color(raw.getOrDefault("FogColor", base.get("FogColor")))
        );
    }

    public static Map<String, Object> defaultFog() {
        return Map.of(
                "FogStart", -10f,
                "FogEnd", 100f,
                "FogColor", Map.of("r", 0.2f, "g", 0.2f, "b", 0.2f, "a", 1.0f),
                "FogShape", 0
        );
    }

    public static Map<String, Object> defaultHeightFog() {
        return Map.of(
                "FOG_Y", 64.99f,
                "THICKNESS", 0.1f,
                "FogColor", Map.of("r", 0.2f, "g", 0.2f, "b", 0.2f, "a", 1.0f)
        );
    }

    private static Map<String, Object> color(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            float r = clamp(map.get("r"), 0f, 1f);
            float g = clamp(map.get("g"), 0f, 1f);
            float b = clamp(map.get("b"), 0f, 1f);
            float a = clamp(map.get("a"), 0f, 1f);
            return Map.of("r", r, "g", g, "b", b, "a", a);
        }
        return Map.of("r", 1f, "g", 1f, "b", 1f, "a", 1f);
    }

    private static float clamp(Object raw, float min, float max) {
        float v;
        if (raw instanceof Number n) {
            v = n.floatValue();
        } else {
            try {
                v = Float.parseFloat(String.valueOf(raw));
            } catch (Exception e) {
                v = min;
            }
        }
        return Math.min(max, Math.max(min, v));
    }
}
