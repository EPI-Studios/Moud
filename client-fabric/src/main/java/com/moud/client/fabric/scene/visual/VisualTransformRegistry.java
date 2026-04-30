package com.moud.client.fabric.scene.visual;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class VisualTransformRegistry {

    private static final VisualTransformRegistry INSTANCE = new VisualTransformRegistry();

    private final Long2ObjectOpenHashMap<VisualTransform> transforms = new Long2ObjectOpenHashMap<>();
    private final Object lock = new Object();

    private VisualTransformRegistry() {
    }

    public static VisualTransformRegistry get() {
        return INSTANCE;
    }

    public void setOffset(long nodeId, float dx, float dy, float dz) {
        if (nodeId <= 0L) {
            return;
        }
        synchronized (lock) {
            VisualTransform t = transforms.computeIfAbsent(nodeId, k -> new VisualTransform());
            t.dx = dx;
            t.dy = dy;
            t.dz = dz;
            removeIfIdentity(nodeId, t);
        }
    }

    public void setRotation(long nodeId, float rx, float ry, float rz) {
        if (nodeId <= 0L) {
            return;
        }
        synchronized (lock) {
            VisualTransform t = transforms.computeIfAbsent(nodeId, k -> new VisualTransform());
            t.rxOff = rx;
            t.ryOff = ry;
            t.rzOff = rz;
            removeIfIdentity(nodeId, t);
        }
    }

    public void setScale(long nodeId, float sx, float sy, float sz) {
        if (nodeId <= 0L) {
            return;
        }
        synchronized (lock) {
            VisualTransform t = transforms.computeIfAbsent(nodeId, k -> new VisualTransform());
            t.sxMul = clampPositive(sx);
            t.syMul = clampPositive(sy);
            t.szMul = clampPositive(sz);
            removeIfIdentity(nodeId, t);
        }
    }

    public boolean fill(long nodeId, VisualTransform out) {
        if (out == null || nodeId <= 0L) {
            return false;
        }
        synchronized (lock) {
            VisualTransform stored = transforms.get(nodeId);
            if (stored == null) {
                return false;
            }
            out.copyFrom(stored);
            return true;
        }
    }

    public void clearNode(long nodeId) {
        synchronized (lock) {
            transforms.remove(nodeId);
        }
    }

    public void clearAll() {
        synchronized (lock) {
            transforms.clear();
        }
    }

    private void removeIfIdentity(long nodeId, VisualTransform t) {
        if (t.isIdentity()) {
            transforms.remove(nodeId);
        }
    }

    private static float clampPositive(float v) {
        if (Float.isNaN(v) || v <= 1.0e-4f) {
            return 1.0e-4f;
        }
        return v;
    }
}
