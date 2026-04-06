package com.moud.client.fabric.editor.state;

import com.moud.client.fabric.scene.SceneState;
import com.moud.core.NodeTypeDef;
import com.moud.core.scene.Node;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.ProjectInfo;
import com.moud.net.protocol.SceneInfo;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.ScriptActionListResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class EditorState {
    public final SceneState scene = new SceneState();

    public List<String> typeIds = List.of("Node");
    public Map<String, NodeTypeDef> typesById = Map.of();
    public List<SceneInfo> scenes = List.of(new SceneInfo("main", "Main"));
    public String activeSceneId = "main";
    public final ArrayList<String> openSceneIds = new ArrayList<>(List.of("main"));
    public final ArrayList<String> openScriptPaths = new ArrayList<>();
    public String activeScriptPath = "";
    public final ArrayList<String> openTextAssetPaths = new ArrayList<>();
    public String activeTextAssetPath = "";
    public long selectedId;
    public final LinkedHashSet<Long> selectedIds = new LinkedHashSet<>();
    public long nextSnapshotRequestId = 1;
    public long nextBatchId = 1;
    public long nextProjectRequestId = 1;
    public long nextScriptActionRequestId = 1;
    public long nextScriptFileRequestId = 1;
    public long lastSavedRevision = -1;

    public boolean projectExists;
    public boolean projectInfoKnown;
    public String projectName = "";
    public String projectAuthor = "";

    public final ArrayList<AssetManifestResponse.Entry> manifestEntries = new ArrayList<>();

    public final HashMap<String, double[]> sceneCameraStates = new HashMap<>();
    public final HashMap<String, double[]> sceneCanvasStates = new HashMap<>();

    public final Map<Long, ScriptActions> scriptActionsByNode = new HashMap<>();

    public SceneOpAck lastAck;
    public boolean pendingSnapshot;

    public boolean isDirty() {
        return scene.revision() >= 0 && scene.revision() != lastSavedRevision;
    }

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
        boolean anyCreated = ack.results().stream().anyMatch(r -> r.createdId() != 0L);
        if (!anyFailed) {
            scene.setRevision(ack.sceneRevision());
        }
        if (anyFailed || anyCreated) {
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
                lastSavedRevision = -1;
            } else {
                activeSceneId = next;
            }
        }
        ensureSceneOpen(activeSceneId);
        pruneOpenScenes();
    }

    public void onProjectInfo(ProjectInfo info) {
        if (info == null) {
            return;
        }
        projectInfoKnown = true;
        projectExists = info.exists();
        projectName = info.name() == null ? "" : info.name();
        projectAuthor = info.author() == null ? "" : info.author();
    }

    public ScriptActions scriptActions(long nodeId) {
        if (nodeId <= 0L) {
            return null;
        }
        return scriptActionsByNode.computeIfAbsent(nodeId, ignored -> new ScriptActions());
    }

    public void onScriptActionListResponse(ScriptActionListResponse response, String scriptPath) {
        if (response == null) {
            return;
        }
        ScriptActions cache = scriptActions(response.nodeId());
        if (cache == null) {
            return;
        }
        cache.pending = false;
        cache.loaded = true;
        if (scriptPath != null) {
            cache.scriptPath = scriptPath;
        }
        cache.actions = response.actions() == null ? List.of() : response.actions();
        cache.error = response.success() ? null : response.error();
    }

    public static final class ScriptActions {
        public String scriptPath = "";
        public boolean pending;
        public boolean loaded;
        public String error;
        public List<String> actions = List.of();
    }

    public void ensureSceneOpen(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) {
            return;
        }
        if (!openSceneIds.contains(sceneId)) {
            openSceneIds.add(sceneId);
        }
    }

    public void ensureScriptOpen(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        if (!openScriptPaths.contains(path)) {
            openScriptPaths.add(path);
        }
        activeScriptPath = path;
    }

    public void closeScriptTab(String path) {
        if (path == null) {
            return;
        }
        openScriptPaths.remove(path);
        if (path.equals(activeScriptPath)) {
            activeScriptPath = openScriptPaths.isEmpty() ? "" : openScriptPaths.getLast();
        }
    }

    public void ensureTextAssetOpen(String path) {
        if (path == null || path.isBlank()) return;
        if (!openTextAssetPaths.contains(path)) {
            openTextAssetPaths.add(path);
        }
        activeTextAssetPath = path;
    }

    public void closeTextAssetTab(String path) {
        if (path == null) return;
        openTextAssetPaths.remove(path);
        if (path.equals(activeTextAssetPath)) {
            activeTextAssetPath = openTextAssetPaths.isEmpty() ? "" : openTextAssetPaths.getLast();
        }
    }

    private void pruneOpenScenes() {
        if (openSceneIds.isEmpty()) {
            openSceneIds.add("main");
            return;
        }

        HashSet<String> valid = new HashSet<>();
        if (scenes != null) {
            for (SceneInfo s : scenes) {
                if (s != null && s.sceneId() != null && !s.sceneId().isBlank()) {
                    valid.add(s.sceneId());
                }
            }
        }

        openSceneIds.removeIf(id -> id == null || id.isBlank() || (!"main".equals(id) && !valid.contains(id)));
        if (openSceneIds.isEmpty()) {
            openSceneIds.add("main");
        }
    }
}
