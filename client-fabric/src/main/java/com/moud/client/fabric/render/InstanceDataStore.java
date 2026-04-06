package com.moud.client.fabric.render;

import java.util.concurrent.ConcurrentHashMap;

public final class InstanceDataStore {
    private static final ConcurrentHashMap<Long, float[]> store   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, float[]> partial = new ConcurrentHashMap<>();

    private InstanceDataStore() {}

    public static void accumulate(long nodeId, int offset, int total, float[] data) {
        if (data == null) data = new float[0];
        if (total <= 0 || total == data.length) {
            partial.remove(nodeId);
            put(nodeId, data);
            return;
        }
        float[] buf = partial.get(nodeId);
        if (buf == null || buf.length != total) {
            buf = new float[total];
            partial.put(nodeId, buf);
        }
        System.arraycopy(data, 0, buf, offset, data.length);
        if (offset + data.length >= total) {
            partial.remove(nodeId);
            put(nodeId, buf);
        }
    }

    public static void put(long nodeId, float[] data) {
        if (data == null || data.length == 0) {
            store.remove(nodeId);
        } else {
            store.put(nodeId, data);
        }
    }

    public static float[] get(long nodeId) {
        return store.get(nodeId);
    }

    public static void remove(long nodeId) {
        store.remove(nodeId);
        partial.remove(nodeId);
    }

    public static void clear() {
        store.clear();
        partial.clear();
    }
}
