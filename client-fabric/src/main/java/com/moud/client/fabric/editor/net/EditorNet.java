package com.moud.client.fabric.editor.net;


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
import java.util.List;

public final class EditorNet {
    public void requestSnapshot(Session session, EditorState state) {
        if (session == null) {
            return;
        }
        session.send(Lane.STATE, new SceneSnapshotRequest(state.nextSnapshotRequestId++));
    }

    public void sendOps(Session session, EditorState state, List<SceneOp> ops) {
        if (session == null) {
            return;
        }
        if (state != null && state.scene != null) {
            state.scene.applyOps(ops);
            ClientSceneBus.applyOps(ops);
        }
        session.send(Lane.EVENTS, new SceneOpBatch(state.nextBatchId++, false, List.copyOf(ops)));
        if (needsSnapshotAfterOps(ops)) {
            state.pendingSnapshot = true;
        }
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
}
