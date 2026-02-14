package com.moud.client.fabric.scene;

import com.moud.net.protocol.SceneSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SceneState {
    private long revision = -1;
    private final Map<Long, SceneSnapshot.NodeSnapshot> nodesById = new HashMap<>();
    private final Map<Long, List<SceneSnapshot.NodeSnapshot>> childrenByParent = new HashMap<>();

    public void applySnapshot(SceneSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        revision = snapshot.revision();
        nodesById.clear();
        childrenByParent.clear();
        for (SceneSnapshot.NodeSnapshot node : snapshot.nodes()) {
            nodesById.put(node.nodeId(), node);
            childrenByParent.computeIfAbsent(node.parentId(), k -> new ArrayList<>()).add(node);
        }
    }

    public long revision() {
        return revision;
    }

    public SceneSnapshot.NodeSnapshot getNode(long nodeId) {
        return nodesById.get(nodeId);
    }

    public List<SceneSnapshot.NodeSnapshot> childrenOf(long parentId) {
        return childrenByParent.getOrDefault(parentId, List.of());
    }
}
