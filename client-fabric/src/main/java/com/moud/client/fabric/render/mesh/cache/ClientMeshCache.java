package com.moud.client.fabric.render.mesh.cache;

import org.lwjgl.opengl.GL15;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ClientMeshCache {
    private static final int DEFAULT_CAPACITY = 512;

    private static final Object LOCK = new Object();
    private static int capacity = DEFAULT_CAPACITY;

    private static final LinkedHashMap<String, GpuMesh> ENTRIES =
            new LinkedHashMap<>(64, 0.75f, true);

    private static final Deque<GpuMesh> PENDING_DELETE = new ArrayDeque<>();

    private ClientMeshCache() {
    }

    public static void setCapacity(int cap) {
        synchronized (LOCK) {
            capacity = Math.max(1, cap);
        }
    }

    public static Optional<GpuMesh> get(String hash) {
        synchronized (LOCK) {
            return Optional.ofNullable(ENTRIES.get(hash));
        }
    }

    public static boolean contains(String hash) {
        synchronized (LOCK) {
            return ENTRIES.containsKey(hash);
        }
    }

    public static void put(GpuMesh mesh) {
        synchronized (LOCK) {
            GpuMesh previous = ENTRIES.put(mesh.hash(), mesh);
            if (previous != null && previous != mesh) {
                PENDING_DELETE.add(previous);
            }
            while (ENTRIES.size() > capacity) {
                evictOldestLocked();
            }
        }
    }

    public static void flushPendingDeletes() {
        Deque<GpuMesh> drained;
        synchronized (LOCK) {
            if (PENDING_DELETE.isEmpty()) {
                return;
            }
            drained = new ArrayDeque<>(PENDING_DELETE);
            PENDING_DELETE.clear();
        }
        for (GpuMesh mesh : drained) {
            deleteBuffers(mesh);
        }
    }

    public static void clear() {
        java.util.Collection<GpuMesh> drained;
        synchronized (LOCK) {
            drained = new java.util.ArrayList<>(ENTRIES.values());
            ENTRIES.clear();
            PENDING_DELETE.clear();
        }
        for (GpuMesh mesh : drained) {
            deleteBuffers(mesh);
        }
    }

    private static void evictOldestLocked() {
        var it = ENTRIES.entrySet().iterator();
        if (!it.hasNext()) {
            return;
        }
        Map.Entry<String, GpuMesh> entry = it.next();
        it.remove();
        PENDING_DELETE.add(entry.getValue());
    }

    public static void evictOldest() {
        synchronized (LOCK) {
            evictOldestLocked();
        }
    }

    private static void deleteBuffers(GpuMesh mesh) {
        for (GpuSurface surface : mesh.surfaces()) {
            if (surface.vbo() != 0) {
                GL15.glDeleteBuffers(surface.vbo());
            }
            if (surface.ebo() != 0) {
                GL15.glDeleteBuffers(surface.ebo());
            }
        }
    }
}
