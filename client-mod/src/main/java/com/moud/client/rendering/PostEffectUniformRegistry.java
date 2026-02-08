package com.moud.client.rendering;

import java.util.Collections;
import java.util.Map;

public final class PostEffectUniformRegistry {
    private PostEffectUniformRegistry() {}

    public static Map<String, Object> sanitize(String effectId, Map<String, Object> raw) {
        if (effectId == null) return raw;
        String norm = effectId.toLowerCase();
        if ("veil:fog".equals(norm) || "shaders:fog".equals(norm)) {
            return sanitizeFog(raw);
        }
        if ("veil:height_fog".equals(norm) || "shaders:height_fog".equals(norm)) {
            return sanitizeHeightFog(raw);
        }
        if ("moud:ssr".equals(norm)) {
            return sanitizeSsr(raw);
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

    private static Map<String, Object> sanitizeSsr(Map<String, Object> raw) {
        if (raw == null) return defaultSsr();
        return Map.of(
                "SsrStrength", clamp(raw.getOrDefault("SsrStrength", defaultSsr().get("SsrStrength")), 0.0f, 2.0f),
                "SsrMaxDistance", clamp(raw.getOrDefault("SsrMaxDistance", defaultSsr().get("SsrMaxDistance")), 1.0f, 256.0f),
                "SsrStepSize", clamp(raw.getOrDefault("SsrStepSize", defaultSsr().get("SsrStepSize")), 0.02f, 4.0f),
                "SsrThickness", clamp(raw.getOrDefault("SsrThickness", defaultSsr().get("SsrThickness")), 0.0002f, 0.05f),
                "SsrEdgeFade", clamp(raw.getOrDefault("SsrEdgeFade", defaultSsr().get("SsrEdgeFade")), 0.01f, 0.5f)
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

    public static Map<String, Object> defaultSsr() {
        return Map.of(
                "SsrStrength", 0.35f,
                "SsrMaxDistance", 32.0f,
                "SsrStepSize", 0.2f,
                "SsrThickness", 0.002f,
                "SsrEdgeFade", 0.12f
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
