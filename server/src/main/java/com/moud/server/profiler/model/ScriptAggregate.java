package com.moud.server.profiler.model;

public record ScriptAggregate(
        String functionName,
        String scriptName,
        int line,
        ScriptExecutionType type,
        String label,
        long invocationCount,
        long totalDurationNanos,
        long maxDurationNanos
) implements Comparable<ScriptAggregate> {

    public double averageDurationMillis() {
        return invocationCount == 0 ? 0.0 : (totalDurationNanos / 1_000_000.0) / invocationCount;
    }

    public double totalDurationMillis() {
        return totalDurationNanos / 1_000_000.0;
    }

    public double maxDurationMillis() {
        return maxDurationNanos / 1_000_000.0;
    }

    @Override
    public int compareTo(ScriptAggregate other) {
        return Long.compare(other.totalDurationNanos, this.totalDurationNanos);
    }
}

