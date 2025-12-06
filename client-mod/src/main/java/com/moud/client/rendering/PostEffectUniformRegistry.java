package com.moud.client.rendering;

import java.util.Collections;
import java.util.Map;

public final class PostEffectUniformRegistry {
    private PostEffectUniformRegistry() {}

    public static Map<String, Object> sanitize(String effectId, Map<String, Object> raw) {
        if (effectId == null) return raw;
        String norm = effectId.toLowerCase();
        if ("shaders:fog".equals(norm)) {
            return sanitizeFog(raw);
        }
        if ("shaders:height_fog".equals(norm)) {
            return sanitizeHeightFog(raw);
        }
        return raw;
    }

    private static Map<String, Object> sanitizeFog(Map<String, Object> raw) {
        if (raw == null) return defaultFog();
        return Map.of(
                "FogStart", clamp(raw.getOrDefault("FogStart", defaultFog().get("FogStart")), -256f, 1024f),
                "FogEnd", clamp(raw.getOrDefault("FogEnd", defaultFog().get("FogEnd")), -256f, 4096f),
                "FogColor", color(raw.getOrDefault("FogColor", defaultFog().get("FogColor"))),
                "FogShape", (int) clamp(raw.getOrDefault("FogShape", defaultFog().get("FogShape")), 0f, 1f)
        );
    }

    private static Map<String, Object> sanitizeHeightFog(Map<String, Object> raw) {
        if (raw == null) return defaultHeightFog();
        return Map.of(
                "FOG_Y", clamp(raw.getOrDefault("FOG_Y", defaultHeightFog().get("FOG_Y")), -512f, 1024f),
                "THICKNESS", clamp(raw.getOrDefault("THICKNESS", defaultHeightFog().get("THICKNESS")), 0.01f, 4f),
                "FogColor", color(raw.getOrDefault("FogColor", defaultHeightFog().get("FogColor")))
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
