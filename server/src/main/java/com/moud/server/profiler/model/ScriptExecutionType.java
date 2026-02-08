package com.moud.server.profiler.model;

public enum ScriptExecutionType {
    EVENT,
    ASYNC_TASK,
    TIMEOUT,
    INTERVAL,
    RUNTIME_TICK,
    COMMAND,
    OTHER;

    public String displayName() {
        return switch (this) {
            case EVENT -> "Event";
            case ASYNC_TASK -> "Async Task";
            case TIMEOUT -> "Timeout";
            case INTERVAL -> "Interval";
            case RUNTIME_TICK -> "Tick";
            case COMMAND -> "Command";
            case OTHER -> "Other";
        };
    }
}

