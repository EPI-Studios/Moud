package com.moud.client.fabric.editor.state;

import com.moud.client.fabric.editor.net.EditorNet;

import com.moud.core.NodeTypeDef;
import com.moud.client.fabric.scene.SceneState;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;

public final class EditorState {
    public final SceneState scene = new SceneState();

    public List<String> typeIds = List.of("Node");
    public Map<String, NodeTypeDef> typesById = Map.of();
    public List<SceneInfo> scenes = List.of(new SceneInfo("main", "Main"));
    public String activeSceneId = "main";
    public long selectedId;
    public long nextSnapshotRequestId = 1;
    public long nextBatchId = 1;

    public SceneOpAck lastAck;
    public boolean pendingSnapshot;

    public void onSnapshot(SceneSnapshot snapshot) {
        scene.applySnapshot(snapshot);
        if (selectedId == 0L || scene.getNode(selectedId) == null) {
            SceneSnapshot.NodeSnapshot firstCsg = null;
            if (snapshot != null && snapshot.nodes() != null) {
                for (SceneSnapshot.NodeSnapshot node : snapshot.nodes()) {
                    if (node != null && "CSGBlock".equals(node.type())) {
                        firstCsg = node;
                        break;
                    }
                }
            }
            if (firstCsg != null) {
                selectedId = firstCsg.nodeId();
            } else {
                List<SceneSnapshot.NodeSnapshot> roots = scene.childrenOf(0L);
                if (!roots.isEmpty()) {
                    selectedId = roots.getFirst().nodeId();
                }
            }
        }
    }

    public void onAck(SceneOpAck ack) {
        lastAck = ack;
        if (ack == null) {
            return;
        }
        boolean anyFailed = ack.results().stream().anyMatch(r -> !r.ok());
        if (anyFailed || ack.sceneRevision() != scene.revision()) {
            pendingSnapshot = true;
        }
    }

    public void onSchema(SchemaSnapshot schema) {
        if (schema == null || schema.types() == null || schema.types().isEmpty()) {
            return;
        }
        LinkedHashMap<String, NodeTypeDef> next = new LinkedHashMap<>();
        for (NodeTypeDef def : schema.types()) {
            if (def == null || def.typeId() == null || def.typeId().isBlank()) {
                continue;
            }
            next.put(def.typeId(), def);
        }
        typesById = Map.copyOf(next);

        ArrayList<NodeTypeDef> defs = new ArrayList<>(typesById.values());
        defs.sort(Comparator
                .comparingInt(NodeTypeDef::order)
                .thenComparing(NodeTypeDef::uiLabel)
                .thenComparing(NodeTypeDef::typeId));
        ArrayList<String> ids = new ArrayList<>(defs.size());
        for (NodeTypeDef def : defs) {
            ids.add(def.typeId());
        }
        typeIds = List.copyOf(ids);
    }

    public void onSceneList(SceneList list) {
        if (list == null || list.scenes() == null || list.scenes().isEmpty()) {
            return;
        }
        scenes = List.copyOf(list.scenes());
        if (list.activeSceneId() != null && !list.activeSceneId().isBlank()) {
            String next = list.activeSceneId();
            if (!next.equals(activeSceneId)) {
                activeSceneId = next;
                selectedId = 0L;
            } else {
                activeSceneId = next;
            }
        }
    }
}
