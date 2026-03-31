package com.moud.server.minestom.scripting;


import com.moud.net.protocol.MultiMeshData;
import com.moud.net.protocol.PlayerInput;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.util.DebugLog;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.polyglot.Engine;

final class RuntimeScriptService {
    private final ProjectService project;
    private final Engine engine;
    private final ScriptLanguageRegistry languages;
    private final ConcurrentHashMap<String, SceneRuntime> runtimeByScene = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PlayerInputState> inputsByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, float[]> playerPositions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> playerNames = new ConcurrentHashMap<>();
    private final Set<String> loggedUnsupportedScripts = ConcurrentHashMap.newKeySet();

    RuntimeScriptService(ProjectService project, Engine engine, ScriptLanguageRegistry languages) {
        this.project = Objects.requireNonNull(project, "project");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    void updatePlayerPositions(Map<UUID, float[]> positions) {
        playerPositions.clear();
        if (positions != null) {
            for (Map.Entry<UUID, float[]> e : positions.entrySet()) {
                playerPositions.put(e.getKey().toString(), e.getValue());
            }
        }
    }

    void updatePlayerNames(Map<UUID, String> names) {
        playerNames.clear();
        if (names != null) {
            for (Map.Entry<UUID, String> e : names.entrySet()) {
                playerNames.put(e.getKey().toString(), e.getValue());
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

    List<MultiMeshData> getLatestMultiMesh(String sceneId) {
        if (sceneId == null) return List.of();
        SceneRuntime rt = runtimeByScene.get(sceneId);
        return rt == null ? List.of() : rt.getLatestMultiMesh();
    }

    List<MultiMeshData> drainMultiMesh(String sceneId) {
        if (sceneId == null) return List.of();
        SceneRuntime rt = runtimeByScene.get(sceneId);
        return rt == null ? List.of() : rt.drainMultiMesh();
    }

    void refreshEditor(ServerScene scene) {
        if (scene == null) {
            return;
        }
        warnUnsupportedScripts(scene);
        SceneRuntime rt = runtimeByScene.computeIfAbsent(
                scene.sceneId(),
                ignored -> new SceneRuntime(project, engine, inputsByPlayer)
        );
        rt.updatePlayerPositions(playerPositions);
        rt.updatePlayerNames(playerNames);
        rt.refreshEditor(scene);
    }

    void onUiEvent(ServerScene scene, long nodeId, String event, float value) {
        if (scene == null || nodeId <= 0 || event == null || event.isBlank()) return;
        SceneRuntime rt = runtimeByScene.get(scene.sceneId());
        if (rt != null) rt.onUiEvent(nodeId, event, value);
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
        warnUnsupportedScripts(scene);
        if (!(Double.isFinite(dtSeconds)) || dtSeconds <= 0.0) {
            dtSeconds = 1.0 / 20.0;
        }

        SceneRuntime rt = runtimeByScene.computeIfAbsent(
                scene.sceneId(),
                ignored -> new SceneRuntime(project, engine, inputsByPlayer)
        );
        rt.updatePlayerPositions(playerPositions);
        rt.updatePlayerNames(playerNames);
        rt.tick(scene, dtSeconds);
        return rt.drainPendingSceneTransition();
    }

    private void warnUnsupportedScripts(ServerScene scene) {
        if (scene == null) {
            return;
        }
        ArrayDeque<com.moud.core.scene.Node> queue = new ArrayDeque<>();
        queue.add(scene.engine().sceneTree().root());
        while (!queue.isEmpty()) {
            var node = queue.removeFirst();
            if (node == null) {
                continue;
            }
            queue.addAll(node.children());
            ScriptReference script = ScriptPaths.parseScript(node.getProperty(RuntimeScriptKeys.SCRIPT_KEY));
            if (script == null || script.language() == ScriptLanguage.JAVASCRIPT) {
                continue;
            }
            ScriptLanguageSupport support = languages.supportFor(script.language());
            if (support.available()) {
                continue;
            }
            String key = scene.sceneId() + ":" + node.nodeId() + ":" + script.path();
            if (!loggedUnsupportedScripts.add(key)) {
                continue;
            }
            DebugLog.error("script-runtime",
                    "scene=" + scene.sceneId()
                            + " nodeId=" + node.nodeId()
                            + " file=" + script.path()
                            + " error=" + support.messageForPath(script.path()),
                    null);
        }
    }
}
