package com.moud.client.fabric.editor.net;


import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.scene.SceneState;
import com.moud.net.protocol.AssetPathOp;
import com.moud.net.protocol.ProjectCreate;
import com.moud.net.protocol.ProjectInfoRequest;
import com.moud.net.protocol.SceneCreate;
import com.moud.net.protocol.SceneDelete;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneSave;
import com.moud.net.protocol.SceneSelect;
import com.moud.net.protocol.SceneSnapshotRequest;
import com.moud.net.protocol.ScriptActionInvoke;
import com.moud.net.protocol.ScriptActionListRequest;
import com.moud.net.protocol.ScriptFileReadRequest;
import com.moud.net.protocol.ScriptFileWriteRequest;
import com.moud.net.session.Session;
import com.moud.net.transport.Lane;
import java.util.ArrayList;
import java.util.List;

public final class EditorNet {
    private static final String PROP_PREFAB_GENERATED = "@prefab_generated";
    private static final String PROP_PREFAB_INSTANCE_ROOT = "@prefab_instance_root";
    private static final String TYPE_SCENE_INSTANCE = "SceneInstance3D";

    public void requestSnapshot(Session session, EditorState state) {
        if (session == null) {
            return;
        }
        session.send(Lane.STATE, new SceneSnapshotRequest(state.nextSnapshotRequestId++));
    }

    public void sendOps(Session session, EditorState state, List<SceneOp> ops) {
        sendOpsWithBatchId(session, state, ops);
    }

    public long sendOpsWithBatchId(Session session, EditorState state, List<SceneOp> ops) {
        if (session == null) {
            return 0L;
        }
        List<SceneOp> rewritten = rewritePrefabOps(state != null ? state.scene : null, ops);
        if (state != null && state.scene != null) {
            state.scene.applyOps(rewritten);
            ClientSceneBus.applyOps(rewritten);
        }
        long batchId = state.nextBatchId++;
        session.send(Lane.EVENTS, new SceneOpBatch(batchId, false, List.copyOf(rewritten)));
        if (needsSnapshotAfterOps(rewritten)) {
            state.pendingSnapshot = true;
        }
        return batchId;
    }

    public void selectScene(Session session, EditorState state, String sceneId) {
        if (session == null || sceneId == null || sceneId.isBlank()) {
            return;
        }
        session.send(Lane.STATE, new SceneSelect(sceneId));
        state.pendingSnapshot = true;
    }

    public void saveScene(Session session, String sceneId) {
        if (session == null || sceneId == null || sceneId.isBlank()) {
            return;
        }
        session.send(Lane.EVENTS, new SceneSave(sceneId));
    }

    public void createScene(Session session, String sceneId, String displayName) {
        if (session == null || sceneId == null || sceneId.isBlank()) {
            return;
        }
        session.send(Lane.EVENTS, new SceneCreate(sceneId, displayName));
    }

    public void deleteScene(Session session, String sceneId) {
        if (session == null || sceneId == null || sceneId.isBlank()) {
            return;
        }
        session.send(Lane.EVENTS, new SceneDelete(sceneId));
    }

    public void requestProjectInfo(Session session, EditorState state) {
        if (session == null || state == null) {
            return;
        }
        session.send(Lane.STATE, new ProjectInfoRequest(state.nextProjectRequestId++));
    }

    public void createProject(Session session, EditorState state, String name, String author) {
        if (session == null || state == null) {
            return;
        }
        String n = name == null ? "" : name.trim();
        String a = author == null ? "" : author.trim();
        session.send(Lane.EVENTS, new ProjectCreate(state.nextProjectRequestId++, n, a));
    }

    public void requestScriptActions(Session session, EditorState state, long nodeId) {
        if (session == null || state == null || nodeId <= 0L) {
            return;
        }
        session.send(Lane.EVENTS, new ScriptActionListRequest(state.nextScriptActionRequestId++, nodeId));
    }

    public void invokeScriptAction(Session session, EditorState state, long nodeId, String action) {
        if (session == null || state == null || nodeId <= 0L) {
            return;
        }
        if (action == null || action.isBlank()) {
            return;
        }
        session.send(Lane.EVENTS, new ScriptActionInvoke(state.nextScriptActionRequestId++, nodeId, action));
    }

    public long requestScriptFile(Session session, EditorState state, String path) {
        if (session == null || state == null) {
            return 0L;
        }
        String p = path == null ? "" : path.trim();
        long requestId = state.nextScriptFileRequestId++;
        session.send(Lane.EVENTS, new ScriptFileReadRequest(requestId, p));
        return requestId;
    }

    public long writeScriptFile(Session session, EditorState state, String path, String content) {
        if (session == null || state == null) {
            return 0L;
        }
        String p = path == null ? "" : path.trim();
        String c = content == null ? "" : content;
        long requestId = state.nextScriptFileRequestId++;
        session.send(Lane.EVENTS, new ScriptFileWriteRequest(requestId, p, c));
        return requestId;
    }

    public long createFolder(Session session, EditorState state, String path) {
        return sendAssetPathOp(session, state, AssetPathOp.Kind.CREATE_FOLDER, path, "");
    }

    public long renameAsset(Session session, EditorState state, String oldPath, String newPath) {
        return sendAssetPathOp(session, state, AssetPathOp.Kind.RENAME, oldPath, newPath);
    }

    public long deleteAssetRecursive(Session session, EditorState state, String path) {
        return sendAssetPathOp(session, state, AssetPathOp.Kind.DELETE_RECURSIVE, path, "");
    }

    private long sendAssetPathOp(Session session, EditorState state, AssetPathOp.Kind kind,
                                 String path, String newPath) {
        if (session == null || state == null || path == null || path.isBlank()) return 0L;
        long requestId = state.nextScriptFileRequestId++;
        session.send(Lane.EVENTS, new AssetPathOp(requestId, kind, path.trim(), newPath == null ? "" : newPath.trim()));
        return requestId;
    }

    private static boolean needsSnapshotAfterOps(List<SceneOp> ops) {
        if (ops == null || ops.isEmpty()) {
            return false;
        }
        for (SceneOp op : ops) {
            if (op instanceof SceneOp.CreateNode) {
                return true;
            }
            if (op instanceof SceneOp.SetProperty sp && "scene_id".equals(sp.key())) {
                return true;
            }
            if (op instanceof SceneOp.RemoveProperty rp && "scene_id".equals(rp.key())) {
                return true;
            }
        }
        return false;
    }

    private static List<SceneOp> rewritePrefabOps(SceneState scene, List<SceneOp> ops) {
        if (scene == null || ops == null || ops.isEmpty()) {
            return ops == null ? List.of() : ops;
        }
        ArrayList<SceneOp> out = null;
        for (int i = 0; i < ops.size(); i++) {
            SceneOp op = ops.get(i);
            SceneOp rewritten = rewritePrefabOp(scene, op);
            if (rewritten != op && out == null) {
                out = new ArrayList<>(ops.size());
                for (int j = 0; j < i; j++) {
                    out.add(ops.get(j));
                }
            }
            if (out != null) {
                out.add(rewritten);
            }
        }
        return out == null ? ops : List.copyOf(out);
    }

    private static SceneOp rewritePrefabOp(SceneState scene, SceneOp op) {
        if (scene == null || op == null) {
            return op;
        }

        return switch (op) {
            case SceneOp.QueueFree qf -> {
                long id = resolveInstanceRootIfPrefab(scene, qf.nodeId());
                yield id == qf.nodeId() ? op : new SceneOp.QueueFree(id);
            }
            case SceneOp.Rename rn -> {
                long id = resolveInstanceRootIfPrefab(scene, rn.nodeId());
                yield id == rn.nodeId() ? op : new SceneOp.Rename(id, rn.newName());
            }
            case SceneOp.Reparent rp -> {
                long nodeId = resolveInstanceRootIfPrefab(scene, rp.nodeId());
                long newParentId = resolveSafeParent(scene, rp.newParentId());
                if (nodeId == rp.nodeId() && newParentId == rp.newParentId()) {
                    yield op;
                }
                if (nodeId == newParentId) {
                    yield op;
                }
                yield new SceneOp.Reparent(nodeId, newParentId, rp.index());
            }
            case SceneOp.SetProperty sp -> {
                if (!isTransformKey(sp.key())) {
                    yield op;
                }
                long id = resolveInstanceRootIfPrefab(scene, sp.nodeId());
                yield id == sp.nodeId() ? op : new SceneOp.SetProperty(id, sp.key(), sp.value());
            }
            case SceneOp.RemoveProperty remove -> {
                if (!isTransformKey(remove.key())) {
                    yield op;
                }
                long id = resolveInstanceRootIfPrefab(scene, remove.nodeId());
                yield id == remove.nodeId() ? op : new SceneOp.RemoveProperty(id, remove.key());
            }
            default -> op;
        };
    }

    private static boolean isTransformKey(String key) {
        if (key == null) {
            return false;
        }
        return "x".equals(key) || "y".equals(key) || "z".equals(key)
                || "rx".equals(key) || "ry".equals(key) || "rz".equals(key)
                || "sx".equals(key) || "sy".equals(key) || "sz".equals(key);
    }

    private static long resolveInstanceRootIfPrefab(SceneState scene, long nodeId) {
        if (scene == null || nodeId <= 0L || !isPrefabGenerated(scene, nodeId)) {
            return nodeId;
        }
        long rootId = parseLong(scene.getPropertyValue(nodeId, PROP_PREFAB_INSTANCE_ROOT), 0L);
        return rootId > 0L ? rootId : nodeId;
    }

    private static long resolveSafeParent(SceneState scene, long parentId) {
        if (scene == null || parentId <= 0L) {
            return parentId;
        }

        if (isPrefabGenerated(scene, parentId)) {
            long rootId = parseLong(scene.getPropertyValue(parentId, PROP_PREFAB_INSTANCE_ROOT), 0L);
            parentId = rootId > 0L ? rootId : parentId;
        }

        var parent = scene.getNode(parentId);
        if (parent != null && TYPE_SCENE_INSTANCE.equals(parent.type())) {
            return parent.parentId();
        }
        return parentId;
    }

    private static boolean isPrefabGenerated(SceneState scene, long nodeId) {
        return isTrue(scene.getPropertyValue(nodeId, PROP_PREFAB_GENERATED));
    }

    private static boolean isTrue(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    private static long parseLong(String s, long fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
