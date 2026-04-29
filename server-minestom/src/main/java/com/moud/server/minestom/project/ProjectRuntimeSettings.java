package com.moud.server.minestom.project;

public record ProjectRuntimeSettings(
        Integer playersPerInstance,
        Integer maxInstances,
        Long idleKickTimeoutMillis,
        Long emptyShutdownGraceMillis,
        Long tickBudgetMillis
) {
}
