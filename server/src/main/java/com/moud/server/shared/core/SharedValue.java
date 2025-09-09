package com.moud.server.shared.core;

public class SharedValue {
    public enum Permission {
        SERVER_ONLY,
        HYBRID,
        CLIENT_READONLY
    }

    public enum SyncMode {
        IMMEDIATE,
        BATCHED
    }

    private final String key;
    private Object value;
    private final Permission permission;
    private final SyncMode syncMode;
    private long lastModified;
    private boolean dirty;

    public SharedValue(String key, Object value, Permission permission, SyncMode syncMode) {
        this.key = key;
        this.value = value;
        this.permission = permission;
        this.syncMode = syncMode;
        this.lastModified = System.currentTimeMillis();
        this.dirty = false;
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        if (!isValidValue(value)) {
            throw new IllegalArgumentException("Invalid value type for shared value: " + value.getClass());
        }
        this.value = value;
        this.lastModified = System.currentTimeMillis();
        this.dirty = true;
    }

    public Permission getPermission() {
        return permission;
    }

    public SyncMode getSyncMode() {
        return syncMode;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        this.dirty = false;
    }

    public boolean canClientModify() {
        return permission == Permission.HYBRID;
    }

    public boolean shouldSync() {
        return permission != Permission.SERVER_ONLY;
    }

    private boolean isValidValue(Object value) {
        if (value == null) return true;

        if (value instanceof String ||
                value instanceof Number ||
                value instanceof Boolean) {
            return true;
        }

        if (value instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
            return map.keySet().stream().allMatch(k -> k instanceof String) &&
                    map.values().stream().allMatch(this::isValidValue);
        }

        if (value instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) value;
            return list.stream().allMatch(this::isValidValue);
        }

        return false;
    }

    public Object getClonedValue() {
        if (value == null) return null;

        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(value);
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone shared value", e);
        }
    }
}