package com.moud.server.profiler.model;

public record ScriptSample(
        long spanId,
        long parentSpanId,
        String functionName,
        String scriptName,
        int line,
        long durationNanos,
        long startEpochMillis,
        ScriptExecutionType type,
        String label,
        String detail,
        boolean success,
        String errorMessage
) {
    public double durationMillis() {
        return durationNanos / 1_000_000.0;
    }
}

