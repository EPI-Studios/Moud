package com.moud.server.minestom.scripting.engine;

public final class ScriptTimingBudget {
    private static final double EMA_ALPHA = 0.2;
    private static final double WARN_MS = 80.0;
    private static final double KILL_MS = 100.0;
    private static final int STRIKE_LIMIT = 3;
    private static final int RECOVERY_RUNS = 10;

    private double emaMs;
    private int strikes;
    private int cleanStreak;

    public boolean record(double elapsedMs) {
        emaMs = EMA_ALPHA * elapsedMs + (1 - EMA_ALPHA) * emaMs;

        if (emaMs > KILL_MS) {
            strikes++;
            cleanStreak = 0;
            return strikes >= STRIKE_LIMIT;
        }

        if (emaMs < WARN_MS) {
            cleanStreak++;
            if (cleanStreak >= RECOVERY_RUNS) {
                strikes = Math.max(0, strikes - 1);
                cleanStreak = 0;
            }
        }
        return false;
    }

    public double emaMs() {
        return emaMs;
    }

    public double killMs() {
        return KILL_MS;
    }

    public int strikeLimit() {
        return STRIKE_LIMIT;
    }
}
