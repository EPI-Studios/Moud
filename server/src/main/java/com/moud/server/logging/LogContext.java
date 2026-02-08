package com.moud.server.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable container for structured logging metadata.
 */
public final class LogContext {
    private static final LogContext EMPTY = new LogContext(Collections.emptyMap());

    private final Map<String, Object> attributes;

    private LogContext(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public static LogContext empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> asMap() {
        return attributes;
    }

    public LogContext merge(LogContext other) {
        if (this == EMPTY) {
            return other;
        }
        if (other == EMPTY) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(this.attributes.size() + other.attributes.size());
        merged.putAll(this.attributes);
        merged.putAll(other.attributes);
        return new LogContext(Collections.unmodifiableMap(merged));
    }

    public static final class Builder {
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        public Builder put(String key, Object value) {
            if (key == null || key.isEmpty() || value == null) {
                return this;
            }
            attributes.put(key, value);
            return this;
        }

        public Builder putAll(Map<String, ?> values) {
            if (values == null || values.isEmpty()) {
                return this;
            }
            values.forEach((key, value) -> {
                if (key != null && !key.isEmpty() && value != null) {
                    attributes.put(key, value);
                }
            });
            return this;
        }

        public LogContext build() {
            if (attributes.isEmpty()) {
                return LogContext.empty();
            }
            return new LogContext(Collections.unmodifiableMap(new LinkedHashMap<>(attributes)));
        }
    }
}

