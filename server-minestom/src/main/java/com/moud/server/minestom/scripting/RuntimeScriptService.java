package com.moud.server.minestom.scripting;


import com.moud.core.NodeTypeProviders;
import com.moud.core.NodeTypeRegistry;
import com.moud.net.protocol.MultiMeshData;
import com.moud.server.minestom.scripting.typescript.ScriptTypeGenerator;
import com.moud.net.protocol.PlayerInput;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.net.PlayerMessageSink;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.scripting.typescript.TypeScriptContext;
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
    private final TypeScriptContext tsContext;
    private final PlayerMessageSink playerMessageSink;
    private final ConcurrentHashMap<String, SceneRuntime> runtimeByScene = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PlayerInputState> inputsByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, float[]> playerVelocities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, float[]> playerPositions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, float[]> previousPlayerPositions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> playerNames = new ConcurrentHashMap<>();
    private final Set<String> loggedUnsupportedScripts = ConcurrentHashMap.newKeySet();

    RuntimeScriptService(ProjectService project, Engine engine, ScriptLanguageRegistry languages,
                         PlayerMessageSink playerMessageSink) {
        this.project = Objects.requireNonNull(project, "project");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.playerMessageSink = Objects.requireNonNull(playerMessageSink, "playerMessageSink");
        NodeTypeRegistry registry = buildRegistry();
        this.tsContext = buildTypeScriptContext(registry, engine);
        generateTypeDeclarations(registry, project);
    }

    private static NodeTypeRegistry buildRegistry() {
        NodeTypeRegistry registry = new NodeTypeRegistry()
                .setAllowUnknownTypes(true)
                .setAllowUnknownProperties(true);
        NodeTypeProviders.loadInto(registry);
        return registry;
    }

    private static TypeScriptContext buildTypeScriptContext(NodeTypeRegistry registry, Engine engine) {
        try {
            return new TypeScriptContext(registry, engine);
        } catch (Exception e) {
            DebugLog.error("script-runtime", "Failed to initialize TypeScript support: " + e.getMessage(), e);
            return null;
        }
    }

    private static void generateTypeDeclarations(NodeTypeRegistry registry, ProjectService project) {
        try {
            java.nio.file.Path outputPath = project.projectRoot().resolve("scripts/types/moud.d.ts");
            new ScriptTypeGenerator(registry).generate(outputPath);
        } catch (Exception e) {
            DebugLog.error("script-runtime", "Failed to generate moud.d.ts: " + e.getMessage(), e);
        }
    }

    void updatePlayerPositions(Map<UUID, float[]> positions, double dtSeconds) {
        double safeDt = Double.isFinite(dtSeconds) && dtSeconds > 0.0 ? dtSeconds : 1.0 / 20.0;
        playerPositions.clear();
        Set<String> seenPlayers = ConcurrentHashMap.newKeySet();
        if (positions != null) {
            for (Map.Entry<UUID, float[]> e : positions.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String uuid = e.getKey().toString();
                float[] pos = e.getValue();
                if (pos == null || pos.length < 3) {
                    continue;
                }
                float[] current = pos.clone();
                playerPositions.put(uuid, current);
                seenPlayers.add(uuid);

                float[] previous = previousPlayerPositions.put(uuid, current.clone());
                if (previous == null || previous.length < 3) {
                    playerVelocities.put(uuid, new float[]{0f, 0f, 0f});
                    continue;
                }

                float vx = (float) ((current[0] - previous[0]) / safeDt);
                float vy = (float) ((current[1] - previous[1]) / safeDt);
                float vz = (float) ((current[2] - previous[2]) / safeDt);
                playerVelocities.put(uuid, new float[]{vx, vy, vz});
            }
        }
        previousPlayerPositions.keySet().removeIf(uuid -> !seenPlayers.contains(uuid));
        playerVelocities.keySet().removeIf(uuid -> !seenPlayers.contains(uuid));
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
                ignored -> new SceneRuntime(project, engine, inputsByPlayer, playerVelocities, tsContext, playerMessageSink)
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
                ignored -> new SceneRuntime(project, engine, inputsByPlayer, playerVelocities, tsContext, playerMessageSink)
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
            if (script == null
                    || script.language() == ScriptLanguage.JAVASCRIPT
                    || script.language() == ScriptLanguage.TYPESCRIPT) {
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
