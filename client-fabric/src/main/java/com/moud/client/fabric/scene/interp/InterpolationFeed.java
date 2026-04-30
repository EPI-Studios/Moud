package com.moud.client.fabric.scene.interp;

import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneSnapshotDelta;
import java.util.List;

public final class InterpolationFeed {

    private InterpolationFeed() {
    }

    public static void onSnapshot(SceneSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        long now = System.nanoTime();
        List<SceneSnapshot.NodeSnapshot> nodes = snapshot.nodes();
        if (nodes == null) {
            return;
        }
        NodeInterpolatorRegistry registry = NodeInterpolatorRegistry.get();
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            registry.onNodeUpdated(node, now);
        }
    }

    public static void onDelta(SceneSnapshotDelta delta) {
        if (delta == null) {
            return;
        }
        long now = System.nanoTime();
        NodeInterpolatorRegistry registry = NodeInterpolatorRegistry.get();
        List<SceneSnapshot.NodeSnapshot> upserts = delta.upserts();
        if (upserts != null) {
            for (SceneSnapshot.NodeSnapshot node : upserts) {
                registry.onNodeUpdated(node, now);
            }
        }
        List<Long> removed = delta.removed();
        if (removed != null) {
            for (Long nodeId : removed) {
                if (nodeId != null) {
                    registry.onNodeRemoved(nodeId);
                }
            }
        }
    }

    public static void onNodeRefreshed(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return;
        }
        NodeInterpolatorRegistry.get().onNodeUpdated(node, System.nanoTime());
    }

    public static void onNodeRemoved(long nodeId) {
        NodeInterpolatorRegistry.get().onNodeRemoved(nodeId);
    }

    public static void onClear() {
        NodeInterpolatorRegistry.get().clear();
    }
}
