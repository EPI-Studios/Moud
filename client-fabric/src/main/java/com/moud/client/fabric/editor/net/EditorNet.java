package com.moud.client.fabric.editor.net;

import com.moud.client.fabric.editor.state.EditorState;

import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneSnapshotRequest;
import com.moud.net.protocol.SceneSelect;
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
        session.send(Lane.EVENTS, new SceneOpBatch(state.nextBatchId++, false, List.copyOf(ops)));
        state.pendingSnapshot = true;
    }

    public void selectScene(Session session, EditorState state, String sceneId) {
        if (session == null || sceneId == null || sceneId.isBlank()) {
            return;
        }
        session.send(Lane.STATE, new SceneSelect(sceneId));
        state.pendingSnapshot = true;
    }
}
