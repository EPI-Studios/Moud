package com.moud.client.fabric.scene;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientPropertyOverrides {
    private static final Map<Long, Map<String, String>> OVERRIDES = new ConcurrentHashMap<>();
    private static final AtomicLong EPOCH = new AtomicLong(1L);

    private ClientPropertyOverrides() {
    }

    public static long epoch() {
        return EPOCH.get();
    }

    private static void bumpEpoch() {
        long next = EPOCH.incrementAndGet();
        if (next <= 0L) EPOCH.set(1L);
    }

    public static void put(long nodeId, String key, String value) {
        if (nodeId <= 0L || key == null || key.isEmpty()) return;
        Map<String, String> perNode = OVERRIDES.computeIfAbsent(nodeId, id -> new ConcurrentHashMap<>());
        if (value == null) {
            if (perNode.remove(key) != null) {
                if (perNode.isEmpty()) OVERRIDES.remove(nodeId);
                bumpEpoch();
                if (Transform3DMirror.isTransformKey(key)) Transform3DMirror.apply(nodeId, key, null);
            }
        } else {
            String prev = perNode.put(key, value);
            if (!value.equals(prev)) {
                bumpEpoch();
                if (Transform3DMirror.isTransformKey(key)) Transform3DMirror.apply(nodeId, key, value);
            }
        }
    }

    public static String get(long nodeId, String key) {
        if (nodeId <= 0L || key == null) return null;
        Map<String, String> perNode = OVERRIDES.get(nodeId);
        return perNode == null ? null : perNode.get(key);
    }

    public static boolean hasAny(long nodeId) {
        if (nodeId <= 0L) return false;
        Map<String, String> perNode = OVERRIDES.get(nodeId);
        return perNode != null && !perNode.isEmpty();
    }

    public static void clearNode(long nodeId) {
        if (OVERRIDES.remove(nodeId) != null) bumpEpoch();
    }

    public static void clearAll() {
        if (!OVERRIDES.isEmpty()) {
            OVERRIDES.clear();
            bumpEpoch();
        }
    }
}
