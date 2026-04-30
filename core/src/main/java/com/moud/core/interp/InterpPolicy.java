package com.moud.core.interp;

public record InterpPolicy(InterpMode mode, int lagMs) {

    public static final int MIN_LAG_MS = 0;
    public static final int MAX_LAG_MS = 500;

    public InterpPolicy {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (lagMs < MIN_LAG_MS || lagMs > MAX_LAG_MS) {
            throw new IllegalArgumentException("lagMs out of range: " + lagMs);
        }
    }

    public boolean smooths() {
        return mode != InterpMode.SNAP && lagMs > 0;
    }

    public static int clampLag(int value) {
        if (value < MIN_LAG_MS) {
            return MIN_LAG_MS;
        }
        if (value > MAX_LAG_MS) {
            return MAX_LAG_MS;
        }
        return value;
    }
}
