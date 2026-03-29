package com.moud.client.fabric.scene;


import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientSceneBus {
    private static final SceneState SCENE = new SceneState();
    private static final AtomicLong VERSION = new AtomicLong();
    private static final AtomicLong SNAPSHOT_VERSION = new AtomicLong();
    private static final AtomicLong PHYSICS_VERSION = new AtomicLong();
    private static final AtomicLong RESET_VERSION = new AtomicLong();

    private ClientSceneBus() {
    }

    public static long version() {
        return VERSION.get();
    }

    public static long snapshotVersion() {
        return SNAPSHOT_VERSION.get();
    }

    public static long physicsVersion() {
        return PHYSICS_VERSION.get();
    }

    public static long resetVersion() {
        return RESET_VERSION.get();
    }

    public static List<SceneSnapshot.NodeSnapshot> copyNodes() {
        synchronized (SCENE) {
            return List.copyOf(new ArrayList<>(SCENE.nodes()));
        }
    }

    public static SceneSnapshot.NodeSnapshot getNode(long nodeId) {
        synchronized (SCENE) {
            return SCENE.getNode(nodeId);
        }
    }

    public static void applySnapshot(SceneSnapshot snapshot) {
        synchronized (SCENE) {
            SCENE.applySnapshot(snapshot);
        }
        VERSION.incrementAndGet();
        SNAPSHOT_VERSION.incrementAndGet();
    }

    public static void applyOps(List<SceneOp> ops) {
        synchronized (SCENE) {
            SCENE.applyOps(ops);
        }
        VERSION.incrementAndGet();
    }

    public static void applyPhysicsOps(List<SceneOp> ops) {
        synchronized (SCENE) {
            SCENE.applyOps(ops);
        }
        VERSION.incrementAndGet();
        PHYSICS_VERSION.incrementAndGet();
    }

    public static void markRestorePending() {
        RESET_VERSION.incrementAndGet();
    }

    public static void clear() {
        synchronized (SCENE) {
            SCENE.clear();
        }
        VERSION.incrementAndGet();
        SNAPSHOT_VERSION.incrementAndGet();
    }
}
