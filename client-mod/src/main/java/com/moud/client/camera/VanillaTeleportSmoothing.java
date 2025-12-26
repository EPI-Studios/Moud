package com.moud.client.camera;

import net.minecraft.util.math.Vec3d;

public final class VanillaTeleportSmoothing {
    private static volatile boolean userEnabled = false;
    private static volatile long userDurationMs = 120L;
    private static volatile double userThreshold = 0.25;

    private static volatile boolean serverEnabled = false;
    private static volatile long serverDurationMs = 45L;
    private static volatile double serverThreshold = 0.15;

    private static volatile Vec3d startOffset = Vec3d.ZERO;
    private static volatile long startTimeMs = 0L;

    private VanillaTeleportSmoothing() {
    }

    public static boolean isEnabled() {
        return serverEnabled || userEnabled;
    }

    public static boolean isInProgress() {
        return startTimeMs != 0L && isEnabled();
    }

    public static void configureUser(Boolean enabled, Long durationMs, Double threshold) {
        if (enabled != null) {
            userEnabled = enabled;
        }
        if (durationMs != null) {
            userDurationMs = Math.max(1L, Math.min(10_000L, durationMs));
        }
        if (threshold != null) {
            userThreshold = Math.max(0.0, threshold);
        }
        if (!isEnabled()) {
            stop();
        }
    }

    public static void setServerMode(boolean enabled) {
        serverEnabled = enabled;
        if (!isEnabled()) {
            stop();
        }
    }

    public static void configureServer(Long durationMs, Double threshold) {
        if (durationMs != null) {
            serverDurationMs = Math.max(1L, Math.min(10_000L, durationMs));
        }
        if (threshold != null) {
            serverThreshold = Math.max(0.0, threshold);
        }
    }

    public static void onCameraDelta(Vec3d delta) {
        if (!isEnabled() || delta == null) {
            return;
        }

        if (delta.lengthSquared() < 1.0e-10) {
            return;
        }

        double threshold = effectiveThreshold();
        if (threshold > 0.0 && delta.lengthSquared() < threshold * threshold) {
            return;
        }

        startOffset = delta;
        startTimeMs = System.currentTimeMillis();
    }

    public static Vec3d currentOffset() {
        if (!isEnabled() || startTimeMs == 0L) {
            return Vec3d.ZERO;
        }

        long duration = effectiveDurationMs();
        long now = System.currentTimeMillis();
        double t = duration <= 0 ? 1.0 : ((double) (now - startTimeMs) / (double) duration);
        if (t >= 1.0) {
            stop();
            return Vec3d.ZERO;
        }

        double eased = easeOutCubic(t);
        double remaining = 1.0 - eased;
        return startOffset.multiply(remaining);
    }

    private static long effectiveDurationMs() {
        return serverEnabled ? serverDurationMs : userDurationMs;
    }

    private static double effectiveThreshold() {
        return serverEnabled ? serverThreshold : userThreshold;
    }

    private static double easeOutCubic(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return 1.0 - Math.pow(1.0 - clamped, 3.0);
    }

    private static void stop() {
        startTimeMs = 0L;
        startOffset = Vec3d.ZERO;
    }
}
