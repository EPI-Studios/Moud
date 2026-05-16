package com.moud.client.fabric.scene;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class SceneStore {
    private static final Map<Long, Transform3DCell> CELLS = new ConcurrentHashMap<>();
    private static final AtomicLong STRUCTURE_EPOCH = new AtomicLong(1L);

    private SceneStore() {}

    public static Transform3DCell get(long nodeId) {
        if (nodeId == 0L) return null;
        return CELLS.get(nodeId);
    }

    public static Transform3DCell getOrCreate(long nodeId) {
        if (nodeId == 0L) return null;
        return CELLS.computeIfAbsent(nodeId, id -> new Transform3DCell());
    }

    public static void remove(long nodeId) {
        if (nodeId == 0L) return;
        if (CELLS.remove(nodeId) != null) bumpStructure();
    }

    public static void clear() {
        if (CELLS.isEmpty()) return;
        CELLS.clear();
        bumpStructure();
    }

    public static long structureEpoch() {
        return STRUCTURE_EPOCH.get();
    }

    private static void bumpStructure() {
        long next = STRUCTURE_EPOCH.incrementAndGet();
        if (next <= 0L) STRUCTURE_EPOCH.set(1L);
    }
}
