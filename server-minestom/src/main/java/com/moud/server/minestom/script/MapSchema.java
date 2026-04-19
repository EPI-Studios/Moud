package com.moud.server.minestom.script;

import com.moud.net.script.ScriptPayload;
import java.util.Map;

public final class MapSchema implements ScriptMessageSchema {
    private final Map<String, String> fields;

    public MapSchema(Map<String, String> fields) {
        this.fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    @Override
    public boolean validate(byte[] payload) {
        if (fields.isEmpty()) return true;
        Object decoded;
        try {
            decoded = ScriptPayload.decode(payload);
        } catch (Exception e) {
            return false;
        }
        if (!(decoded instanceof Map<?, ?> map)) return false;
        for (Map.Entry<String, String> field : fields.entrySet()) {
            Object value = map.get(field.getKey());
            if (value == null || !matches(value, field.getValue())) return false;
        }
        return true;
    }

    private static boolean matches(Object value, String expected) {
        if (expected == null) return true;
        return switch (expected) {
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Long || value instanceof Integer;
            case "string" -> value instanceof String;
            case "boolean" -> value instanceof Boolean;
            case "table" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof java.util.List<?>;
            default -> false;
        };
    }
}
