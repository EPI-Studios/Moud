package com.moud.client.fabric.scene;


import com.moud.client.fabric.scene.interp.InterpolationFeed;
import com.moud.client.fabric.scene.tween.ClientTweenPlayer;
import com.moud.client.fabric.scene.visual.VisualTransformRegistry;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneSnapshotDelta;
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
        List<SceneSnapshot.NodeSnapshot> server;
        synchronized (SCENE) {
            server = new ArrayList<>(SCENE.nodes());
        }
        List<SceneSnapshot.NodeSnapshot> local = ClientLocalNodes.snapshot();
        if (local.isEmpty()) {
            return List.copyOf(server);
        }
        ArrayList<SceneSnapshot.NodeSnapshot> merged = new ArrayList<>(server.size() + local.size());
        merged.addAll(server);
        merged.addAll(local);
        return List.copyOf(merged);
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
        InterpolationFeed.onSnapshot(snapshot);
        VERSION.incrementAndGet();
        SNAPSHOT_VERSION.incrementAndGet();
    }

    public static void applyDelta(SceneSnapshotDelta delta) {
        synchronized (SCENE) {
            SCENE.applyDelta(delta);
        }
        InterpolationFeed.onDelta(delta);
        VERSION.incrementAndGet();
        SNAPSHOT_VERSION.incrementAndGet();
    }

    public static void applyOps(List<SceneOp> ops) {
        synchronized (SCENE) {
            SCENE.applyOps(ops);
        }
        feedTouchedNodes(ops);
        VERSION.incrementAndGet();
    }

    public static void applyPhysicsOps(List<SceneOp> ops) {
        synchronized (SCENE) {
            SCENE.applyOps(ops);
        }
        feedTouchedNodes(ops);
        VERSION.incrementAndGet();
        PHYSICS_VERSION.incrementAndGet();
        MoudTickClock.onPhysicsBatchArrived();
    }

    private static void feedTouchedNodes(List<SceneOp> ops) {
        if (ops == null || ops.isEmpty()) {
            return;
        }
        for (SceneOp op : ops) {
            long nodeId = touchedNodeId(op);
            if (nodeId <= 0L) {
                continue;
            }
            SceneSnapshot.NodeSnapshot node;
            synchronized (SCENE) {
                node = SCENE.getNode(nodeId);
            }
            if (node != null) {
                InterpolationFeed.onNodeRefreshed(node);
            } else {
                InterpolationFeed.onNodeRemoved(nodeId);
            }
        }
    }

    private static long touchedNodeId(SceneOp op) {
        return switch (op) {
            case null -> 0L;
            case SceneOp.CreateNode ignored -> 0L;
            case SceneOp.QueueFree free -> free.nodeId();
            case SceneOp.Rename rename -> rename.nodeId();
            case SceneOp.SetProperty set -> set.nodeId();
            case SceneOp.RemoveProperty remove -> remove.nodeId();
            case SceneOp.Reparent reparent -> reparent.nodeId();
        };
    }

    public static void markRestorePending() {
        RESET_VERSION.incrementAndGet();
    }

    public static void clear() {
        synchronized (SCENE) {
            SCENE.clear();
        }
        ClientLocalNodes.clearAll();
        ClientPropertyOverrides.clearAll();
        SceneStore.clear();
        SceneTransforms.clearCache();
        InterpolationFeed.onClear();
        ClientTweenPlayer.get().clear();
        VisualTransformRegistry.get().clearAll();
        VERSION.incrementAndGet();
        SNAPSHOT_VERSION.incrementAndGet();
        MoudTickClock.reset();
    }
}
