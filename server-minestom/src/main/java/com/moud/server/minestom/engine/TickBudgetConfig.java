package com.moud.server.minestom.engine;

public record TickBudgetConfig(
        long budgetNanos,
        int degradeAfterConsecutiveOverruns,
        int recoverAfterConsecutiveUnderruns
) {
    public TickBudgetConfig {
        if (budgetNanos <= 0) {
            throw new IllegalArgumentException("budgetNanos must be positive");
        }
        if (degradeAfterConsecutiveOverruns <= 0) {
            throw new IllegalArgumentException("degradeAfterConsecutiveOverruns must be positive");
        }
        if (recoverAfterConsecutiveUnderruns <= 0) {
            throw new IllegalArgumentException("recoverAfterConsecutiveUnderruns must be positive");
        }
    }

    public static TickBudgetConfig defaults() {
        return new TickBudgetConfig(40L * 1_000_000L, 5, 3);
    }
}
