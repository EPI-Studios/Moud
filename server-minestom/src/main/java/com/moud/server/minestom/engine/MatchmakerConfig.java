package com.moud.server.minestom.engine;

public record MatchmakerConfig(
        int playersPerInstance,
        int maxInstances,
        long emptyShutdownGraceMillis,
        long housekeepingIntervalMillis
) {
    public MatchmakerConfig {
        if (playersPerInstance <= 0) {
            throw new IllegalArgumentException("playersPerInstance must be positive");
        }
        if (maxInstances <= 0) {
            throw new IllegalArgumentException("maxInstances must be positive");
        }
        if (emptyShutdownGraceMillis < 0) {
            throw new IllegalArgumentException("emptyShutdownGraceMillis must be non-negative");
        }
        if (housekeepingIntervalMillis <= 0) {
            throw new IllegalArgumentException("housekeepingIntervalMillis must be positive");
        }
    }

    public static MatchmakerConfig defaults() {
        return new MatchmakerConfig(8, 16, 30_000L, 1_000L);
    }
}
