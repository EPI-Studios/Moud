package com.moud.server.minestom.physics;

public final class PhysicsClock {
    public static final double FIXED_DT_SECONDS = 1.0 / 60.0;
    private static final int MAX_STEPS_PER_FRAME = 5;

    private double accumulator;

    public void accumulate(double dtSeconds) {
        if (!Double.isFinite(dtSeconds) || dtSeconds <= 0.0) {
            return;
        }
        accumulator += Math.min(dtSeconds, FIXED_DT_SECONDS * MAX_STEPS_PER_FRAME);
    }

    public int consumeSteps() {
        if (accumulator < FIXED_DT_SECONDS) {
            return 0;
        }
        int steps = (int) (accumulator / FIXED_DT_SECONDS);
        if (steps > MAX_STEPS_PER_FRAME) {
            steps = MAX_STEPS_PER_FRAME;
        }
        accumulator -= steps * FIXED_DT_SECONDS;
        return steps;
    }

    public double accumulator() {
        return accumulator;
    }

    public void reset() {
        accumulator = 0.0;
    }
}
