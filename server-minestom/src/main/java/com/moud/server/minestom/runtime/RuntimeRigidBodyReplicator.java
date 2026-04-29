package com.moud.server.minestom.runtime;

import com.moud.net.protocol.RigidBodyEntry;
import com.moud.net.protocol.RigidBodySnapshot;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.physics.rapier.RapierScenePhysicsWorld;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RuntimeRigidBodyReplicator {

    private final Map<String, SceneSync> bySceneId = new HashMap<>();

    public void send(ServerScene scene, Session session) {
        if (scene == null || session == null) {
            return;
        }
        RapierScenePhysicsWorld physics = scene.physics();
        if (physics == null) {
            return;
        }
        if (session.state() != SessionState.CONNECTED) {
            return;
        }

        SceneSync sync = bySceneId.computeIfAbsent(scene.sceneId(), ignored -> new SceneSync());
        long tick = physics.physicsTick();
        if (tick == sync.lastTick) {
            return;
        }
        sync.lastTick = tick;

        List<RigidBodyEntry> entries = physics.snapshotDynamicBodies();
        if (entries.isEmpty()) {
            return;
        }
        session.send(Lane.STATE, new RigidBodySnapshot(tick, System.currentTimeMillis(), entries));
    }

    private static final class SceneSync {
        long lastTick = Long.MIN_VALUE;
    }
}
