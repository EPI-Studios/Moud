package com.moud.server.minestom;

import com.moud.net.protocol.Message;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.ProjectCreate;
import com.moud.net.protocol.ProjectCreateAck;
import com.moud.net.protocol.ProjectInfoRequest;
import com.moud.net.protocol.RequestRespawn;
import com.moud.net.protocol.SceneCreate;
import com.moud.net.protocol.SceneCreateAck;
import com.moud.net.protocol.SceneDelete;
import com.moud.net.protocol.SceneDeleteAck;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpError;
import com.moud.net.protocol.SceneOpResult;
import com.moud.net.protocol.SceneSave;
import com.moud.net.protocol.SceneSaveAck;
import com.moud.net.protocol.SceneSelect;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneSnapshotRequest;
import com.moud.net.protocol.EditorModeChanged;
import com.moud.net.protocol.ScriptActionInvoke;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListRequest;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.net.protocol.ScriptFileReadRequest;
import com.moud.net.protocol.ScriptFileWriteRequest;
import com.moud.net.protocol.UiNodeEvent;
import com.moud.net.session.Session;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.assets.AssetService;
import com.moud.server.minestom.engine.SceneInstancer;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.engine.ServerScenes;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.scripting.ScriptFileService;
import com.moud.server.minestom.scripting.ScriptService;
import com.moud.server.minestom.util.DebugLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

final class MessageRouter {
    private final boolean devMode;
    private final ProjectService project;
    private final ScriptService scripts;
    private final ScriptFileService scriptFiles;
    private final AssetService assets;
    private final ServerScenes scenes;
    private final ServerScene mainScene;
    private final SceneInstancer instancer;
    private final SceneStorage sceneStorage;
    private final PlayModeManager playModeManager;
    private final Map<UUID, PlayerState> playerStates;

    MessageRouter(boolean devMode,
                  ProjectService project,
                  ScriptService scripts,
                  ScriptFileService scriptFiles,
                  AssetService assets,
                  ServerScenes scenes,
                  ServerScene mainScene,
                  SceneInstancer instancer,
                  SceneStorage sceneStorage,
                  PlayModeManager playModeManager,
                  Map<UUID, PlayerState> playerStates) {
        this.devMode = devMode;
        this.project = Objects.requireNonNull(project, "project");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        this.scriptFiles = Objects.requireNonNull(scriptFiles, "scriptFiles");
        this.assets = assets;
        this.scenes = Objects.requireNonNull(scenes, "scenes");
        this.mainScene = Objects.requireNonNull(mainScene, "mainScene");
        this.instancer = Objects.requireNonNull(instancer, "instancer");
        this.sceneStorage = Objects.requireNonNull(sceneStorage, "sceneStorage");
        this.playModeManager = Objects.requireNonNull(playModeManager, "playModeManager");
        this.playerStates = Objects.requireNonNull(playerStates, "playerStates");
    }

    void onSessionMessage(Player player, PlayerState ps, Lane lane, Message message) {
        if (player == null || ps == null || lane == null || message == null) {
            return;
        }
        Session session = ps.session;
        if (session == null) {
            return;
        }

        if (lane == Lane.STATE && message instanceof EditorModeChanged mode) {
            if (devMode) {
                playModeManager.onEditorModeChanged(player, ps, session, mode.editorOpen());
            }
            return;
        }

        if (lane == Lane.STATE && message instanceof ProjectInfoRequest request) {
            session.send(Lane.STATE, project.info(request.requestId()));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ProjectCreate create) {
            if (!devMode) {
                session.send(Lane.EVENTS, new ProjectCreateAck(create.requestId(), false, "editor disabled (MOUD_MODE=player)", "", ""));
                return;
            }
            session.send(Lane.EVENTS, project.create(create));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ScriptActionListRequest request) {
            if (!devMode) {
                session.send(Lane.EVENTS, new ScriptActionListResponse(request.requestId(), request.nodeId(), false, "editor disabled (MOUD_MODE=player)", List.of()));
                return;
            }
            session.send(Lane.EVENTS, scripts.onListActions(playModeManager.resolvePlayerScene(ps), request));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ScriptActionInvoke request) {
            if (!devMode) {
                session.send(Lane.EVENTS, new ScriptActionInvokeAck(request.requestId(), request.nodeId(), false, "editor disabled (MOUD_MODE=player)"));
                return;
            }
            session.send(Lane.EVENTS, scripts.onInvokeAction(playModeManager.resolvePlayerScene(ps), request));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ScriptFileReadRequest request) {
            session.send(Lane.EVENTS, scriptFiles.read(player.getUsername(), devMode, request));
            return;
        }

        if (lane == Lane.EVENTS && message instanceof ScriptFileWriteRequest request) {
            session.send(Lane.EVENTS, scriptFiles.write(player.getUsername(), devMode, request));
            return;
        }

        if (lane == Lane.INPUT && message instanceof PlayerInput input) {
            scripts.onPlayerInput(player.getUuid(), input);
            return;
        }

        if (lane == Lane.INPUT && message instanceof UiNodeEvent event) {
            ServerScene scene = playModeManager.resolvePlayerScene(ps);
            if (scene == null) scene = mainScene;
            scripts.onUiEvent(scene, event.nodeId(), event.event(), event.value());
            return;
        }

        if (lane == Lane.EVENTS && message instanceof RequestRespawn) {
            playModeManager.requestRespawn(player, ps);
            return;
        }

        if (lane == Lane.EVENTS && message instanceof SceneSave save) {
            String sid = save.sceneId();
            if (sid == null || sid.isBlank()) {
                sid = ps.activeSceneId;
            }
            if (sid == null || sid.isBlank()) {
                sid = "main";
            }
            if (!devMode) {
                session.send(Lane.EVENTS, new SceneSaveAck(sid, false, "editor disabled (MOUD_MODE=player)"));
                return;
            }

            ServerScene scene = scenes.get(sid);
            if (scene == null) {
                session.send(Lane.EVENTS, new SceneSaveAck(sid, false, "Scene not found"));
                return;
            }

            try {
                sceneStorage.persistSceneToDisk(scene);
                session.send(Lane.EVENTS, new SceneSaveAck(scene.sceneId(), true, null));
            } catch (Exception e) {
                String msg = e.getMessage() == null || e.getMessage().isBlank() ? "Save failed" : e.getMessage();
                session.send(Lane.EVENTS, new SceneSaveAck(scene.sceneId(), false, msg));
            }
            return;
        }

        if (lane == Lane.EVENTS && message instanceof SceneCreate create) {
            String sid = normalizeSceneId(create.sceneId());
            String displayName = create.displayName();
            if (displayName != null) {
                displayName = displayName.trim();
            }

            if (!devMode) {
                session.send(Lane.EVENTS, new SceneCreateAck(sid, false, "editor disabled (MOUD_MODE=player)"));
                return;
            }
            if (!isValidSceneId(sid)) {
                session.send(Lane.EVENTS, new SceneCreateAck(sid, false, "Invalid scene id (use [a-z0-9_-], max 64 chars)"));
                return;
            }
            if (scenes.get(sid) != null) {
                session.send(Lane.EVENTS, new SceneCreateAck(sid, false, "Scene already exists"));
                return;
            }
            if (displayName == null || displayName.isBlank()) {
                displayName = sid;
            }

            ServerScene scene;
            try {
                scene = scenes.create(sid, displayName);
                sceneStorage.persistSceneToDisk(scene);
            } catch (Exception e) {
                scenes.delete(sid);
                session.send(Lane.EVENTS, new SceneCreateAck(sid, false, e.getMessage()));
                return;
            }

            session.send(Lane.EVENTS, new SceneCreateAck(sid, true, null));
            playModeManager.switchPlayerToScene(player, ps, session, scene);
            return;
        }

        if (lane == Lane.EVENTS && message instanceof SceneDelete delete) {
            String sid = normalizeSceneId(delete.sceneId());
            if (!devMode) {
                session.send(Lane.EVENTS, new SceneDeleteAck(sid, false, "editor disabled (MOUD_MODE=player)"));
                return;
            }
            if (sid == null || sid.isBlank()) {
                session.send(Lane.EVENTS, new SceneDeleteAck(sid, false, "Scene id required"));
                return;
            }
            if ("main".equals(sid)) {
                session.send(Lane.EVENTS, new SceneDeleteAck(sid, false, "Refusing to delete 'main'"));
                return;
            }

            ServerScene existing = scenes.get(sid);
            if (existing == null) {
                session.send(Lane.EVENTS, new SceneDeleteAck(sid, false, "Scene not found"));
                return;
            }

            try {
                sceneStorage.deleteSceneFile(sid);
            } catch (Exception e) {
                session.send(Lane.EVENTS, new SceneDeleteAck(sid, false, "Failed to delete scene file: " + e.getMessage()));
                return;
            }

            scenes.delete(sid);
            sceneStorage.onSceneDeleted(sid);

            for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                PlayerState other = playerStates.get(p.getUuid());
                if (other == null) {
                    continue;
                }
                if (!sid.equals(other.activeSceneId)) {
                    continue;
                }
                Session otherSession = other.session;
                if (otherSession == null) {
                    other.activeSceneId = "main";
                    playModeManager.onSceneChanged(p.getUuid(), "main");
                    continue;
                }
                playModeManager.switchPlayerToScene(p, other, otherSession, mainScene);
            }

            session.send(Lane.EVENTS, new SceneDeleteAck(sid, true, null));
            return;
        }

        if (lane == Lane.STATE && message instanceof SceneSelect(String sceneId)) {
            ServerScene next = scenes.get(sceneId);
            if (next == null && !"main".equals(sceneId)) {
                DebugLog.warn("scene", "unknown scene '" + sceneId + "', switching to main");
                next = scenes.get("main");
            }
            if (next == null) {
                next = mainScene;
            }
            if (next == null) {
                return;
            }

            if (next.sceneId().equals(ps.activeSceneId)) {
                return;
            }

            playModeManager.switchPlayerToScene(player, ps, session, next);
            return;
        }

        ServerScene scene = playModeManager.resolvePlayerScene(ps);
        if (scene == null) {
            scene = mainScene;
        }

        if (message instanceof SceneSnapshotRequest(long requestId)) {
            instancer.syncScene(scenes, scene);
            SceneSnapshot snapshot = scene.snapshot(requestId);
            session.send(Lane.STATE, snapshot);
            return;
        }

        if (lane == Lane.EVENTS && message instanceof SceneOpBatch batch) {
            if (!devMode) {
                ArrayList<SceneOpResult> results = new ArrayList<>(batch.ops().size());
                for (SceneOp op : batch.ops()) {
                    long target = switch (op) {
                        case SceneOp.CreateNode create -> create.parentId();
                        case SceneOp.QueueFree qf -> qf.nodeId();
                        case SceneOp.Rename rn -> rn.nodeId();
                        case SceneOp.SetProperty sp -> sp.nodeId();
                        case SceneOp.RemoveProperty rp -> rp.nodeId();
                        case SceneOp.Reparent rp -> rp.nodeId();
                    };
                    results.add(SceneOpResult.fail(target, SceneOpError.INVALID, "editor disabled (MOUD_MODE=player)"));
                }
                session.send(Lane.EVENTS, new SceneOpAck(batch.batchId(), scene.engine().sceneRevision(), List.copyOf(results)));
                return;
            }
            String user = player.getUsername();
            String sid = scene.sceneId();
            scene.applier().setLogSink(s -> DebugLog.debug("scene/" + sid, "[" + user + "] " + s));
            SceneOpAck ack = scene.apply(batch);
            instancer.syncScene(scenes, scene);
            if (ack != null && ack.sceneRevision() != scene.engine().sceneRevision()) {
                ack = new SceneOpAck(ack.batchId(), scene.engine().sceneRevision(), ack.results());
            }
            if (ack != null && ack.results() != null) {
                for (SceneOpResult r : ack.results()) {
                    if (r == null || r.ok()) {
                        continue;
                    }
                    String msg = r.message();
                    if (msg == null || msg.isBlank()) {
                        msg = r.error() == null ? "SceneOp failed" : r.error().name();
                    }
                    DebugLog.error("scene", "apply failed user=" + user + " scene=" + sid + " targetId=" + r.targetId() + " error=" + msg);
                }
            }
            session.send(Lane.EVENTS, ack);
            if (ps.editorOpen) {
                playModeManager.refreshEditorScene(session, scene);
            }
            return;
        }

        if (lane == Lane.ASSETS && assets != null) {
            assets.onMessage(player.getUuid(), session, message);
        }
    }

    private static String normalizeSceneId(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isValidSceneId(String sceneId) {
        if (sceneId == null) {
            return false;
        }
        String id = sceneId.trim();
        if (id.isEmpty() || id.length() > 64) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_' || c == '-') {
                continue;
            }
            if (c >= 'a' && c <= 'z') {
                continue;
            }
            if (c >= '0' && c <= '9') {
                continue;
            }
            return false;
        }
        return true;
    }
}
