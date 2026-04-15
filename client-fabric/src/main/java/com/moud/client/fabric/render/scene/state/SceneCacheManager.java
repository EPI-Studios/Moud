package com.moud.client.fabric.render.scene.state;

import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.net.protocol.SceneSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class SceneCacheManager {
    private long cachedVersion = Long.MIN_VALUE;
    private long cachedSnapshotVersion = Long.MIN_VALUE;
    private long cachedOverrideVersion = Long.MIN_VALUE;
    private long cachedPhysicsVersion = Long.MIN_VALUE;
    private long cachedResetVersion = Long.MIN_VALUE;

    private List<SceneSnapshot.NodeSnapshot> cachedNodes = List.of();
    private Map<Long, SceneSnapshot.NodeSnapshot> cachedNodesById = Map.of();

    private boolean sceneChangedThisRefresh;
    private boolean shiftPrevPoseState;

    public void refreshSceneCache(AtomicLong runtimeOverrideVersion,
                                  Consumer<List<SceneSnapshot.NodeSnapshot>> snapshotUpdateListener) {
        long version = ClientSceneBus.version();
        long snapshotVersion = ClientSceneBus.snapshotVersion();
        long physicsVersion = ClientSceneBus.physicsVersion();
        long resetVersion = ClientSceneBus.resetVersion();
        long overrideVersion = runtimeOverrideVersion.get();
        boolean sceneChanged = version != cachedVersion;
        boolean overrideChanged = overrideVersion != cachedOverrideVersion;

        sceneChangedThisRefresh = false;
        shiftPrevPoseState = false;

        if (!sceneChanged && !overrideChanged) {
            return;
        }

        cachedOverrideVersion = overrideVersion;
        if (!sceneChanged) {
            return;
        }

        boolean physicsChanged = physicsVersion != cachedPhysicsVersion;
        boolean isReset = resetVersion != cachedResetVersion;

        cachedVersion = version;
        cachedPhysicsVersion = physicsVersion;
        cachedResetVersion = resetVersion;
        cachedNodes = ClientSceneBus.copyNodes();

        HashMap<Long, SceneSnapshot.NodeSnapshot> nextById = new HashMap<>(Math.max(16, cachedNodes.size() * 2));
        for (SceneSnapshot.NodeSnapshot node : cachedNodes) {
            if (node != null) {
                nextById.put(node.nodeId(), node);
            }
        }
        cachedNodesById = nextById;

        sceneChangedThisRefresh = true;
        shiftPrevPoseState = physicsChanged && !isReset;

        if (snapshotVersion != cachedSnapshotVersion && snapshotUpdateListener != null) {
            snapshotUpdateListener.accept(cachedNodes);
        }
        cachedSnapshotVersion = snapshotVersion;
    }

    public boolean sceneChangedThisRefresh() {
        return sceneChangedThisRefresh;
    }

    public boolean shiftPrevPoseState() {
        return shiftPrevPoseState;
    }

    public List<SceneSnapshot.NodeSnapshot> cachedNodes() {
        return cachedNodes;
    }

    public Map<Long, SceneSnapshot.NodeSnapshot> cachedNodesById() {
        return cachedNodesById;
    }

    public List<SceneSnapshot.NodeSnapshot> filteredCachedNodes() {
        return cachedNodes;
    }

    public boolean isEmpty() {
        return cachedNodes.isEmpty();
    }
}
