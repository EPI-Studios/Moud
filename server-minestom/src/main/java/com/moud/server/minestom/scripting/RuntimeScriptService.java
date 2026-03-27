package com.moud.server.minestom.scripting;


import com.moud.net.protocol.PlayerInput;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.project.ProjectService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.polyglot.Engine;

final class RuntimeScriptService {
    private final ProjectService project;
    private final Engine engine;
    private final ConcurrentHashMap<String, SceneRuntime> runtimeByScene = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PlayerInputState> inputsByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, float[]> playerPositions = new ConcurrentHashMap<>();

    RuntimeScriptService(ProjectService project, Engine engine) {
        this.project = Objects.requireNonNull(project, "project");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    void updatePlayerPositions(Map<UUID, float[]> positions) {
        playerPositions.clear();
        if (positions != null) {
            for (Map.Entry<UUID, float[]> e : positions.entrySet()) {
                playerPositions.put(e.getKey().toString(), e.getValue());
            }
        }
    }

    Long getActiveCameraForPlayer(String sceneId, String playerUuid) {
        SceneRuntime rt = runtimeByScene.get(sceneId);
        return rt == null ? null : rt.getActiveCameraForPlayer(playerUuid);
    }

    float[] getFollowCameraForPlayer(String sceneId, String playerUuid) {
        SceneRuntime rt = runtimeByScene.get(sceneId);
        return rt == null ? null : rt.getFollowCameraForPlayer(playerUuid);
    }

    float[] getScriptCameraForPlayer(String sceneId, String playerUuid) {
        SceneRuntime rt = runtimeByScene.get(sceneId);
        return rt == null ? null : rt.getScriptCameraForPlayer(playerUuid);
    }

    void onPlayerInput(UUID uuid, PlayerInput input) {
        if (uuid == null || input == null) {
            return;
        }
        inputsByPlayer.put(uuid.toString(), new PlayerInputState(uuid.toString(), input));
    }

    void onSceneDeleted(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) {
            return;
        }
        SceneRuntime rt = runtimeByScene.remove(sceneId);
        if (rt != null) {
            rt.close();
        }
    }

    /** @return a pending scene-transition ID, or {@code null} if none was requested. */
    String tick(ServerScene scene, double dtSeconds) {
        if (scene == null) {
            return null;
        }
        if (!(Double.isFinite(dtSeconds)) || dtSeconds <= 0.0) {
            dtSeconds = 1.0 / 20.0;
        }

        SceneRuntime rt = runtimeByScene.computeIfAbsent(
                scene.sceneId(),
                ignored -> new SceneRuntime(project, engine, inputsByPlayer)
        );
        rt.updatePlayerPositions(playerPositions);
        rt.tick(scene, dtSeconds);
        return rt.drainPendingSceneTransition();
    }
}
