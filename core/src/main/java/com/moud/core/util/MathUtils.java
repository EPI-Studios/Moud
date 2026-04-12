package com.moud.core.util;

public final class MathUtils {
    private MathUtils() {
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static double clamp(double v, double min, double max) {
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

    public static double approach(double current, double target, double maxDelta) {
        if (current < target) return Math.min(current + maxDelta, target);
        return Math.max(current - maxDelta, target);
    }
    public static double[] yawInputToDirection(float yawDeg, float moveX, float moveZ) {
        double lenSq = moveX * moveX + moveZ * moveZ;
        if (lenSq <= 1.0e-8) {
            return new double[]{0.0, 0.0};
        }
        double invLen = lenSq > 1.0 ? 1.0 / Math.sqrt(lenSq) : 1.0;
        double nx = moveX * invLen;
        double nz = moveZ * invLen;

        double yaw      = Math.toRadians(yawDeg);
        double forwardX = -Math.sin(yaw);
        double forwardZ =  Math.cos(yaw);
        double rightX   = -Math.cos(yaw);
        double rightZ   = -Math.sin(yaw);

        return new double[]{rightX * nx + forwardX * nz, rightZ * nx + forwardZ * nz};
    }
}
