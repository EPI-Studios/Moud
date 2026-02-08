package com.moud.server.profiler.model;

import java.util.Objects;

public record ScriptExecutionMetadata(
        ScriptExecutionType type,
        String label,
        String detail
) {
    public ScriptExecutionMetadata {
        Objects.requireNonNull(type, "type");
        label = label == null || label.isBlank() ? type.displayName() : label;
        detail = detail == null ? "" : detail;
    }

    public static ScriptExecutionMetadata of(ScriptExecutionType type, String label, String detail) {
        return new ScriptExecutionMetadata(type, label, detail);
    }

    public static ScriptExecutionMetadata unnamed(ScriptExecutionType type) {
        return new ScriptExecutionMetadata(type, type.displayName(), "");
    }
}

