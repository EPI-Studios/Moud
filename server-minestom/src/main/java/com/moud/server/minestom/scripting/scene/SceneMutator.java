package com.moud.server.minestom.scripting.scene;


import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpResult;
import com.moud.server.minestom.engine.SceneBatchIds;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.util.DebugLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class SceneMutator {
    private static final String LOG_TAG = "script-runtime";

    private final ArrayList<SceneOp> pendingOps = new ArrayList<>();
    private final HashMap<String, String> pendingProps = new HashMap<>();

    public void queueSet(long nodeId, String key, String value) {
        if (nodeId <= 0L || key == null || key.isBlank() || value == null) {
            return;
        }
        pendingOps.add(new SceneOp.SetProperty(nodeId, key, value));
        pendingProps.put(propKey(nodeId, key), value);
    }

    public void queueRemove(long nodeId, String key) {
        if (nodeId <= 0L || key == null || key.isBlank()) {
            return;
        }
        pendingOps.add(new SceneOp.RemoveProperty(nodeId, key));
        pendingProps.remove(propKey(nodeId, key));
    }

    public void queueRename(long nodeId, String name) {
        if (nodeId <= 0L || name == null || name.isBlank()) {
            return;
        }
        pendingOps.add(new SceneOp.Rename(nodeId, name));
    }

    public void queueReparent(long nodeId, long newParentId) {
        if (nodeId <= 0L || newParentId < 0L) {
            return;
        }
        pendingOps.add(new SceneOp.Reparent(nodeId, newParentId, Integer.MAX_VALUE));
    }

    public void queueFree(long nodeId) {
        if (nodeId <= 0L) {
            return;
        }
        pendingOps.add(new SceneOp.QueueFree(nodeId));
    }

    public String getPending(long nodeId, String key) {
        if (nodeId <= 0L || key == null || key.isBlank()) {
            return null;
        }
        return pendingProps.get(propKey(nodeId, key));
    }

    public void flush(ServerScene scene) {
        if (scene == null) {
            pendingOps.clear();
            pendingProps.clear();
            return;
        }
        if (pendingOps.isEmpty()) {
            pendingProps.clear();
            return;
        }
        if (DebugLog.enabled()) {
            int n = pendingOps.size();
            int show = Math.min(8, n);
            StringBuilder sb = new StringBuilder(256);
            sb.append("flush ops=").append(n).append(" preview=[");
            for (int i = 0; i < show; i++) {
                SceneOp op = pendingOps.get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(op == null ? "null" : op.getClass().getSimpleName());
            }
            if (show < n) {
                sb.append(", ...");
            }
            sb.append(']');
            DebugLog.debug(LOG_TAG, "scene=" + scene.sceneId() + " " + sb);
        }
        long batchId = SceneBatchIds.markRuntime((scene.engine().ticks() << 32) ^ System.nanoTime());
        SceneOpAck ack = scene.applier().apply(new SceneOpBatch(batchId, false, List.copyOf(pendingOps)));
        pendingOps.clear();
        pendingProps.clear();
        if (ack == null) {
            DebugLog.error(LOG_TAG, "scene=" + scene.sceneId() + " Scene apply failed (null ack)");
            return;
        }
        for (SceneOpResult r : ack.results()) {
            if (r != null && !r.ok()) {
                String msg = r.message();
                if (msg == null || msg.isBlank()) {
                    msg = r.error() == null ? "SceneOp failed" : r.error().name();
                }
                DebugLog.error(LOG_TAG, "scene=" + scene.sceneId() + " SceneOp failed: " + msg);
            }
        }
    }

    private static String propKey(long nodeId, String key) {
        return nodeId + "\u0000" + key;
    }
}
