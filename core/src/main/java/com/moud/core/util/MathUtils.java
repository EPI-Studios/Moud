package com.moud.core.util;

public final class MathUtils {
    private MathUtils() {
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float clamp01(float v) {
        return clamp(v, 0.0f, 1.0f);
    }

    public static float normalizeYaw(float yaw) {
        if (!Float.isFinite(yaw)) {
            return 0.0f;
        }
        float wrapped = (float) (yaw % 360.0);
        if (wrapped > 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    public static float clampPitch(float pitch, float min, float max) {
        if (!Float.isFinite(pitch)) {
            return 0.0f;
        }
        return clamp(pitch, min, max);
    }
}
