package com.moud.client.fabric.scene;

public final class MoudTickClock {
    private static final long DEFAULT_INTERVAL_NANOS = 50_000_000L;
    private static final long MIN_INTERVAL_NANOS = 10_000_000L;
    private static final long MAX_INTERVAL_NANOS = 250_000_000L;
    private static final double EMA_ALPHA = 0.15;

    private static volatile long lastTickWallNanos = 0L;
    private static volatile long intervalNanos = DEFAULT_INTERVAL_NANOS;

    private MoudTickClock() {
    }

    public static void onPhysicsBatchArrived() {
        long now = System.nanoTime();
        long prev = lastTickWallNanos;
        lastTickWallNanos = now;
        if (prev != 0L) {
            long delta = now - prev;
            if (delta >= MIN_INTERVAL_NANOS && delta <= MAX_INTERVAL_NANOS) {
                long current = intervalNanos;
                long updated = (long) (current * (1.0 - EMA_ALPHA) + delta * EMA_ALPHA);
                if (updated < MIN_INTERVAL_NANOS) updated = MIN_INTERVAL_NANOS;
                if (updated > MAX_INTERVAL_NANOS) updated = MAX_INTERVAL_NANOS;
                intervalNanos = updated;
            }
        }
    }

    public static float currentTickDelta() {
        long last = lastTickWallNanos;
        if (last == 0L) {
            return 1.0f;
        }
        long interval = intervalNanos;
        if (interval <= 0L) {
            return 1.0f;
        }
        long elapsed = System.nanoTime() - last;
        if (elapsed <= 0L) {
            return 0.0f;
        }
        double t = (double) elapsed / (double) interval;
        if (t < 0.0) return 0.0f;
        if (t > 1.0) return 1.0f;
        return (float) t;
    }

    public static long expectedIntervalNanos() {
        return intervalNanos;
    }

    public static void reset() {
        lastTickWallNanos = 0L;
        intervalNanos = DEFAULT_INTERVAL_NANOS;
    }
}
