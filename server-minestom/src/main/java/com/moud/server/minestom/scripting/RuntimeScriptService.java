package com.moud.server.minestom.scripting;


import com.moud.net.protocol.PlayerInput;
import com.moud.server.minestom.engine.Engine;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.project.ProjectService;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class RuntimeScriptService {
    private final ProjectService project;
    private final Engine engine;
    private final ConcurrentHashMap<String, SceneRuntime> runtimeByScene = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PlayerInputState> inputsByPlayer = new ConcurrentHashMap<>();

    RuntimeScriptService(ProjectService project, Engine engine) {
        this.project = Objects.requireNonNull(project, "project");
        this.engine = Objects.requireNonNull(engine, "engine");
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

    void tick(ServerScene scene, double dtSeconds) {
        if (scene == null) {
            return;
        }
        if (!(Double.isFinite(dtSeconds)) || dtSeconds <= 0.0) {
            dtSeconds = 1.0 / 20.0;
        }

        SceneRuntime rt = runtimeByScene.computeIfAbsent(
                scene.sceneId(),
                ignored -> new SceneRuntime(project, engine, inputsByPlayer)
        );
        rt.tick(scene, dtSeconds);
    }
}
