package com.moud.server.minestom;

import com.moud.core.NodeTypeDef;
import com.moud.core.scene.SceneTreeMutator;
import com.moud.net.protocol.MultiMeshData;
import com.moud.net.protocol.PlayReady;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.engine.SceneInstancer;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.engine.ServerScenes;
import com.moud.server.minestom.net.PlayerMessageSink;
import com.moud.server.minestom.runtime.PlayerBodyManager;
import com.moud.server.minestom.runtime.PlayRuntime;
import com.moud.server.minestom.runtime.RuntimeRigidBodyReplicator;
import com.moud.server.minestom.scripting.ScriptService;
import com.moud.server.minestom.util.DebugLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Collections;
import java.util.function.BiConsumer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

final class PlayModeManager {
    private final ServerScenes scenes;
    private final ServerScene mainScene;
    private final ScriptService scripts;
    private final SceneInstancer instancer;
    private final PlayRuntime playRuntime;
    private final PlayerBodyManager bodyManager;
    private final BiConsumer<Session, ServerScene> collisionGeometrySender;
    private final RuntimeRigidBodyReplicator rigidBodyReplicator = new RuntimeRigidBodyReplicator();
    private final Map<String, SceneBaseline> baselineBySceneId = new HashMap<>();

    private volatile SchemaSnapshot cachedSchema;
    private final Map<String, List<MultiMeshData>> pendingMultiMeshByScene = new HashMap<>();

    boolean isPausedForEditor(Map<UUID, PlayerState> playerStates) {
        if (playerStates == null || playerStates.isEmpty()) {
            return false;
        }
        for (PlayerState ps : playerStates.values()) {
            if (ps != null && ps.editorOpen) {
                return true;
            }
        }
        return false;
    }

    PlayModeManager(ServerScenes scenes,
                    ServerScene mainScene,
                    ScriptService scripts,
                    SceneInstancer instancer,
                    PlayRuntime playRuntime,
                    PlayerMessageSink playerMessageSink,
                    BiConsumer<Session, ServerScene> collisionGeometrySender) {
        this.scenes = Objects.requireNonNull(scenes, "scenes");
        this.mainScene = Objects.requireNonNull(mainScene, "mainScene");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        this.instancer = Objects.requireNonNull(instancer, "instancer");
        this.playRuntime = Objects.requireNonNull(playRuntime, "playRuntime");
        this.bodyManager = new PlayerBodyManager(Objects.requireNonNull(playerMessageSink, "playerMessageSink"));
        this.collisionGeometrySender = Objects.requireNonNull(collisionGeometrySender, "collisionGeometrySender");
    }

    void onEditorModeChanged(Player player, PlayerState ps, Session session, boolean editorOpen) {
        if (ps == null) {
            return;
        }
        boolean wasOpen = ps.editorOpen;
        ps.editorOpen = editorOpen;

        ServerScene scene = resolvePlayerScene(ps);
        if (scene == null) {
            return;
        }

        if (!wasOpen && editorOpen) {
            restoreBaseline(scene);
            ps.sceneSnapshotSentRevision = Long.MIN_VALUE;
            if (session != null && session.state() == SessionState.CONNECTED) {
                instancer.syncScene(scenes, scene);
                SceneSnapshot snapshot = scene.snapshot(0L);
                session.send(Lane.STATE, snapshot);
                ps.sceneSnapshotSentRevision = snapshot.revision();
                scripts.refreshEditorRuntime(scene);
                sendLatestMultiMesh(session, snapshot, scene.sceneId());
                sendLatestCollisionGeometry(session, scene);
            }
        } else if (wasOpen && !editorOpen) {
            captureBaseline(scene);
            scripts.replayReady(scene);
            ps.multiMeshSent = false;
            ps.collisionGeometrySentSceneId = null;
            Pos startPos = PlayRuntime.findPlayerStartPos(scene);
            if (startPos != null && player != null) {
                player.teleport(startPos);
                playRuntime.syncControllableBodyToPlayer(player, scene, startPos);
            }
            if (player != null) {
                applyPlayerMovementFallback(player, scene);
            }
            if (session != null && session.state() == SessionState.CONNECTED) {
                session.send(Lane.STATE, new PlayReady(scene.sceneId()));
            }
        }
    }

    void initBaselineFromDisk(ServerScene scene) {
        if (scene == null) return;
        if (baselineBySceneId.containsKey(scene.sceneId())) return;
        captureBaseline(scene);
    }

    private void captureBaseline(ServerScene scene) {
        if (scene == null) {
            return;
        }
        SceneSnapshot snapshot = scene.snapshot(0L);
        long rootId = scene.engine().sceneTree().root().nodeId();
        ArrayList<SceneTreeMutator.NodeSpec> specs = new ArrayList<>();
        for (SceneSnapshot.NodeSnapshot ns : snapshot.nodes()) {
            if (ns == null || ns.nodeId() <= 0L) {
                continue;
            }
            if (ns.nodeId() == rootId) {
                continue;
            }
            if (isRuntimeOnlyNode(ns)) {
                continue;
            }

            LinkedHashMap<String, String> props = new LinkedHashMap<>();
            if (ns.properties() != null) {
                for (SceneSnapshot.Property p : ns.properties()) {
                    if (p == null || p.key() == null || p.key().isBlank() || p.value() == null) {
                        continue;
                    }
                    props.put(p.key(), p.value());
                }
            }
            specs.add(new SceneTreeMutator.NodeSpec(
                    ns.nodeId(),
                    ns.parentId(),
                    ns.name(),
                    ns.type(),
                    props
            ));
        }
        baselineBySceneId.put(scene.sceneId(), new SceneBaseline(List.copyOf(specs)));
    }

    private static boolean isRuntimeOnlyNode(SceneSnapshot.NodeSnapshot ns) {
        if (ns == null) {
            return false;
        }
        String type = ns.type();
        String name = ns.name();
        if ("Ticker".equals(type)) {
            return true;
        }
        return "ticker".equalsIgnoreCase(name);
    }

    private void restoreBaseline(ServerScene scene) {
        if (scene == null) {
            return;
        }
        SceneBaseline baseline = baselineBySceneId.get(scene.sceneId());
        if (baseline == null) {
            return;
        }

        SceneTreeMutator.replaceRootChildren(
                scene.engine().sceneTree(),
                baseline.specs,
                scene.engine().nodeTypes()
        );
        scene.engine().bumpSceneRevision();
        scene.engine().bumpCsgRevision();
        scene.engine().bumpPhysicsRevision();
        scene.engine().bumpCollisionFilterRevision();
    }

    private record SceneBaseline(List<SceneTreeMutator.NodeSpec> specs) {
    }

    void tickScenes(double dtSeconds, Map<UUID, PlayerState> playerStates) {
        if (isPausedForEditor(playerStates)) {
            for (ServerScene scene : scenes.allScenes()) {
                playRuntime.applyEditorWorldEnvironment(scene);
            }
            return;
        }

        pendingMultiMeshByScene.clear();
        for (ServerScene scene : scenes.allScenes()) {
            playRuntime.applyEditorWorldEnvironment(scene);
            String pendingTransition = scripts.tickRuntime(scene, dtSeconds);
            if (pendingTransition != null) {
                applyScriptSceneTransition(scene.sceneId(), pendingTransition, playerStates);
            }
            List<MultiMeshData> mmData = scripts.drainMultiMesh(scene.sceneId());
            if (!mmData.isEmpty()) {
                pendingMultiMeshByScene.put(scene.sceneId(), mmData);
            }
        }
    }

    void tickPlayer(Player player, PlayerState ps) {
        if (player == null || ps == null) {
            return;
        }
        Session session = ps.session;
        if (session == null) {
            return;
        }
        if (session.state() != SessionState.CONNECTED) {
            return;
        }

        if (!ps.schemaSent) {
            session.send(Lane.STATE, schemaSnapshot());
            ps.schemaSent = true;
        }

        sendSceneListIfNeeded(ps, session);
        ServerScene scene = resolvePlayerScene(ps);
        if (scene == null) {
            return;
        }

        sendLatestCollisionGeometryIfNeeded(ps, session, scene);

        if (ps.editorOpen) {
            return;
        }

        float[] followCam = scripts.getFollowCameraForPlayer(scene.sceneId(), player.getUuid());
        Long playerCamId = followCam == null
                ? scripts.getActiveCameraForPlayer(scene.sceneId(), player.getUuid())
                : null;
        float[] scriptCam = (followCam == null && playerCamId == null)
                ? scripts.getScriptCameraForPlayer(scene.sceneId(), player.getUuid())
                : null;
        bodyManager.tick(player);
        playRuntime.tick(player.getUuid(), session, scene, playerCamId, followCam, scriptCam);
        rigidBodyReplicator.send(scene, session);
        if (!ps.multiMeshSent) {
            for (MultiMeshData mm : scripts.getLatestMultiMesh(scene.sceneId())) {
                session.send(Lane.STATE, mm);
            }
            ps.multiMeshSent = true;
        }
        for (MultiMeshData msg : pendingMultiMeshByScene.getOrDefault(scene.sceneId(), Collections.emptyList())) {
            session.send(Lane.STATE, msg);
        }
        long sceneRevision = scene.engine().sceneRevision();
        if (ps.sceneSnapshotSentRevision != sceneRevision) {
            SceneSnapshot snapshot = scene.snapshot(0L);
            session.send(Lane.STATE, snapshot);
            ps.sceneSnapshotSentRevision = snapshot.revision();
        }
    }

    void onPlayerSpawn(Player player, PlayerState ps, ServerScene spawnScene) {
        if (player == null || spawnScene == null) {
            return;
        }
        playRuntime.onPlayerSpawn(player, spawnScene);
        bodyManager.onPlayerSpawn(player, spawnScene);
        scripts.sendFullClientStateTo(player.getUuid());
        if (ps != null && ps.session != null && ps.session.state() == SessionState.CONNECTED) {
            ps.session.send(Lane.STATE, new PlayReady(spawnScene.sceneId()));
        }
    }


    void onDisconnect(UUID uuid) {
        playRuntime.onDisconnect(uuid);
        bodyManager.onPlayerLeave(uuid);
    }

    void onSceneChanged(UUID uuid, String sceneId) {
        playRuntime.onSceneChanged(uuid, sceneId);
    }

    void refreshEditorScene(Session session, ServerScene scene) {
        if (session == null || scene == null) {
            return;
        }
        if (session.state() != SessionState.CONNECTED) {
            return;
        }
        scripts.refreshEditorRuntime(scene);
        for (MultiMeshData mm : scripts.drainMultiMesh(scene.sceneId())) {
            session.send(Lane.STATE, mm);
        }
    }

    void requestRespawn(Player player, PlayerState ps) {
        if (player == null || ps == null) {
            return;
        }
        ServerScene scene = resolvePlayerScene(ps);
        if (scene == null) {
            scene = mainScene;
        }
        Pos startPos = PlayRuntime.findPlayerStartPos(scene);
        if (startPos != null) {
            player.teleport(startPos);
        }
    }

    void switchPlayerToScene(Player player, PlayerState ps, Session session, ServerScene target) {
        if (player == null || ps == null || session == null || target == null) {
            return;
        }
        String targetId = target.sceneId();
        ps.activeSceneId = targetId;
        ps.collisionGeometrySentSceneId = null;
        ps.sceneSnapshotSentRevision = Long.MIN_VALUE;
        playRuntime.onSceneChanged(player.getUuid(), targetId);

        Pos targetStartPos = PlayRuntime.findPlayerStartPos(target);
        Pos spawnPos = targetStartPos != null ? targetStartPos : new Pos(0, 64, 0);
        player.setInstance(target.instance(), spawnPos)
                .thenRun(() -> MinecraftServer.getSchedulerManager().buildTask(() -> {
                    if (session.state() != SessionState.CONNECTED) {
                        return;
                    }
                    playRuntime.syncControllableBodyToPlayer(player, target, spawnPos);
                    session.send(Lane.STATE, new SceneList(scenes.snapshotInfo(), targetId));
                    instancer.syncScene(scenes, target);
                    SceneSnapshot snapshot = target.snapshot(0L);
                    session.send(Lane.STATE, snapshot);
                    ps.sceneSnapshotSentRevision = snapshot.revision();
                    sendLatestCollisionGeometry(session, target);
                    for (MultiMeshData mm : scripts.getLatestMultiMesh(targetId)) {
                        session.send(Lane.STATE, mm);
                    }
                    scripts.sendFullClientStateTo(player.getUuid());
                }).schedule())
                .exceptionally(ex -> {
                    DebugLog.error("scene", "failed to switch to '" + targetId + "': " + ex.getMessage());
                    return null;
                });
    }

    ServerScene resolvePlayerScene(PlayerState ps) {
        String sceneId = ps != null ? ps.activeSceneId : "main";
        if (sceneId == null || sceneId.isBlank()) {
            sceneId = "main";
        }
        ServerScene scene = scenes.get(sceneId);
        if (scene == null) {
            scene = mainScene;
        }
        return scene;
    }

    private void applyScriptSceneTransition(String fromSceneId, String toSceneId, Map<UUID, PlayerState> playerStates) {
        if (fromSceneId == null || toSceneId == null || toSceneId.isBlank() || playerStates == null) {
            return;
        }
        ServerScene target = scenes.get(toSceneId);
        if (target == null) {
            DebugLog.warn("scene", "script requested unknown scene '" + toSceneId + "'");
            return;
        }
        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            PlayerState ps = playerStates.get(p.getUuid());
            if (ps == null) {
                continue;
            }
            if (!fromSceneId.equals(ps.activeSceneId)) {
                continue;
            }
            Session session = ps.session;
            if (session != null) {
                switchPlayerToScene(p, ps, session, target);
            }
        }
    }

    private void sendSceneListIfNeeded(PlayerState ps, Session session) {
        long rev = scenes.scenesRevision();
        long last = ps.scenesSentRevision;
        if (last == rev) {
            return;
        }
        ps.scenesSentRevision = rev;
        String active = ps.activeSceneId;
        if (active == null || active.isBlank() || scenes.get(active) == null) {
            active = "main";
            ps.activeSceneId = active;
        }
        session.send(Lane.STATE, new SceneList(scenes.snapshotInfo(), active));
    }

    private void sendLatestMultiMesh(Session session, SceneSnapshot snapshot, String sceneId) {
        if (session == null || snapshot == null || sceneId == null || sceneId.isBlank()) {
            return;
        }
        List<MultiMeshData> latest = scripts.getLatestMultiMesh(sceneId);
        if (latest == null || latest.isEmpty() || snapshot.nodes() == null || snapshot.nodes().isEmpty()) {
            return;
        }
        HashSet<Long> nodeIds = new HashSet<>(Math.max(16, snapshot.nodes().size() * 2));
        for (SceneSnapshot.NodeSnapshot ns : snapshot.nodes()) {
            if (ns != null && ns.nodeId() > 0L) {
                nodeIds.add(ns.nodeId());
            }
        }
        for (MultiMeshData mm : latest) {
            if (mm != null && nodeIds.contains(mm.nodeId())) {
                session.send(Lane.STATE, mm);
            }
        }
    }

    private void sendLatestCollisionGeometryIfNeeded(PlayerState ps, Session session, ServerScene scene) {
        if (ps == null || session == null || scene == null) {
            return;
        }
        String sceneId = scene.sceneId();
        if (sceneId == null || sceneId.isBlank() || sceneId.equals(ps.collisionGeometrySentSceneId)) {
            return;
        }
        sendLatestCollisionGeometry(session, scene);
        ps.collisionGeometrySentSceneId = sceneId;
    }

    private void sendLatestCollisionGeometry(Session session, ServerScene scene) {
        if (session == null || scene == null) {
            return;
        }
        collisionGeometrySender.accept(session, scene);
    }

    private SchemaSnapshot schemaSnapshot() {
        SchemaSnapshot cached = cachedSchema;
        if (cached != null) {
            return cached;
        }

        ArrayList<NodeTypeDef> types = new ArrayList<>(mainScene.engine().nodeTypes().types().values());
        types.sort(Comparator
                .comparingInt(NodeTypeDef::order)
                .thenComparing(NodeTypeDef::uiLabel)
                .thenComparing(NodeTypeDef::typeId));
        SchemaSnapshot snapshot = new SchemaSnapshot(1L, List.copyOf(types));
        cachedSchema = snapshot;
        return snapshot;
    }
}
