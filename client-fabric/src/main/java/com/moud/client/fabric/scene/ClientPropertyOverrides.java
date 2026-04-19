package com.moud.client.fabric.scene;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPropertyOverrides {
    private static final Map<Long, Map<String, String>> OVERRIDES = new ConcurrentHashMap<>();

    private ClientPropertyOverrides() {
    }

    public static void put(long nodeId, String key, String value) {
        if (nodeId <= 0L || key == null || key.isEmpty()) return;
        Map<String, String> perNode = OVERRIDES.computeIfAbsent(nodeId, id -> new ConcurrentHashMap<>());
        if (value == null) {
            perNode.remove(key);
            if (perNode.isEmpty()) OVERRIDES.remove(nodeId);
        } else {
            perNode.put(key, value);
        }
    }

    public static String get(long nodeId, String key) {
        if (nodeId <= 0L || key == null) return null;
        Map<String, String> perNode = OVERRIDES.get(nodeId);
        return perNode == null ? null : perNode.get(key);
    }

    public static void clearNode(long nodeId) {
        OVERRIDES.remove(nodeId);
    }

    public static void clearAll() {
        OVERRIDES.clear();
    }
}
