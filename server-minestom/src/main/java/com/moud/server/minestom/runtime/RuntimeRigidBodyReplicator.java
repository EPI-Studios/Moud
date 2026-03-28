package com.moud.server.minestom.runtime;

import com.moud.core.scene.Node;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.engine.SceneBatchIds;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.physics.JoltPhysicsWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RuntimeRigidBodyReplicator {
    private static final String[] TRANSFORM_KEYS = {"x", "y", "z", "rx", "ry", "rz"};

    private final Map<String, SceneSync> bySceneId = new HashMap<>();

    public void send(ServerScene scene, Session session) {
        if (scene == null || session == null) {
            return;
        }
        JoltPhysicsWorld physics = scene.physics();
        if (physics == null) {
            return;
        }
        if (session.state() != SessionState.CONNECTED) {
            return;
        }

        long tick = scene.engine().ticks();
        SceneSync sync = bySceneId.computeIfAbsent(scene.sceneId(), ignored -> new SceneSync());
        List<SceneOp> ops = sync.opsForTick(scene, physics, tick);
        if (ops.isEmpty()) {
            return;
        }

        long batchId = SceneBatchIds.markRuntime((tick << 32) ^ System.nanoTime());
        session.send(Lane.STATE, new SceneOpBatch(batchId, false, ops));
    }

    private static final class SceneSync {
        private long lastTick = Long.MIN_VALUE;
        private List<SceneOp> cachedOps = List.of();
        private final Map<Long, String[]> lastSentByNodeId = new HashMap<>();

        List<SceneOp> opsForTick(ServerScene scene, JoltPhysicsWorld physics, long tick) {
            if (tick == lastTick) {
                return cachedOps;
            }
            lastTick = tick;
            cachedOps = compute(scene, physics);
            return cachedOps;
        }

        private List<SceneOp> compute(ServerScene scene, JoltPhysicsWorld physics) {
            List<Long> dynamicNodes = physics.dynamicNodeIds();
            if (dynamicNodes.isEmpty()) {
                return List.of();
            }

            ArrayList<SceneOp> ops = new ArrayList<>(dynamicNodes.size() * 6);
            for (long nodeId : dynamicNodes) {
                Node node = scene.engine().sceneTree().getNode(nodeId);
                if (node == null) {
                    continue;
                }

                String[] prev = lastSentByNodeId.computeIfAbsent(nodeId, ignored -> new String[TRANSFORM_KEYS.length]);
                for (int i = 0; i < TRANSFORM_KEYS.length; i++) {
                    String key = TRANSFORM_KEYS[i];
                    String next = node.getProperty(key);
                    if (next == null) {
                        next = "0";
                    }
                    String before = prev[i];
                    if (next.equals(before)) {
                        continue;
                    }
                    prev[i] = next;
                    ops.add(new SceneOp.SetProperty(nodeId, key, next));
                }
            }

            return ops.isEmpty() ? List.of() : List.copyOf(ops);
        }
    }
}
