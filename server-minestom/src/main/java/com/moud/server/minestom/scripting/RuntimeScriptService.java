package com.moud.server.minestom.scripting;

import com.moud.core.NodeTypeProviders;
import com.moud.core.NodeTypeRegistry;
import com.moud.net.protocol.MultiMeshData;
import com.moud.net.protocol.PlayerClientState;
import com.moud.server.minestom.scripting.lang.RuntimeScriptKeys;
import com.moud.server.minestom.scripting.lang.ScriptLanguageRegistry;
import com.moud.server.minestom.scripting.lang.ScriptLanguageSupport;
import com.moud.server.minestom.scripting.lang.ScriptPaths;
import com.moud.server.minestom.scripting.player.PlayerInputState;
import com.moud.server.minestom.scripting.luau.ServerLuauTypeGenerator;
import com.moud.server.minestom.scripting.typescript.ScriptTypeGenerator;
import com.moud.net.protocol.PlayerInput;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.net.PlayerMessageSink;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.script.ScriptMessageRouter;
import com.moud.server.minestom.scripting.typescript.TypeScriptContext;
import com.moud.server.minestom.util.DebugLog;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.graalvm.polyglot.Engine;

final class RuntimeScriptService {
    private final ProjectService project;
    private final Engine engine;
    private final ScriptLanguageRegistry languages;
    private final TypeScriptContext tsContext;
    private final PlayerMessageSink playerMessageSink;
    private final ConcurrentHashMap<String, SceneRuntime> runtimeByScene = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PlayerInputState> inputsByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> clientStateByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, float[]> playerVelocities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, float[]> playerPositions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, float[]> previousPlayerPositions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> playerNames = new ConcurrentHashMap<>();
    private final Set<String> loggedUnsupportedScripts = ConcurrentHashMap.newKeySet();
    private ScriptMessageRouter scriptMessageRouter;
    private Supplier<Iterable<UUID>> connectedPlayersSupplier;
    private com.moud.server.minestom.persistence.PersistenceService persistenceService;

    public void setScriptMessaging(ScriptMessageRouter router, Supplier<Iterable<UUID>> connectedPlayers) {
        this.scriptMessageRouter = router;
        this.connectedPlayersSupplier = connectedPlayers;
        for (SceneRuntime rt : runtimeByScene.values()) {
            rt.setScriptMessageRouter(router);
            rt.setConnectedPlayersSupplier(connectedPlayers);
        }
    }

    public void setPersistenceService(com.moud.server.minestom.persistence.PersistenceService service) {
        this.persistenceService = service;
        for (SceneRuntime rt : runtimeByScene.values()) {
            rt.setPersistenceService(service);
        }
    }

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
        java.nio.file.Path typesDir = project.projectRoot().resolve("types");
        try {
            new ServerLuauTypeGenerator(registry).generate(
                    typesDir.resolve("moud-server.d.luau"),
                    typesDir.resolve("moud-client.d.luau"));
        } catch (Exception e) {
            DebugLog.error("script-runtime", "Failed to generate Luau type declarations: " + e.getMessage(), e);
        }
        try {
            new com.moud.server.minestom.scripting.java.JavaStubGenerator().generate(typesDir);
        } catch (Exception e) {
            DebugLog.error("script-runtime", "Failed to generate Java type stubs: " + e.getMessage(), e);
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
        String playerUuid = uuid.toString();
        inputsByPlayer.put(playerUuid, new PlayerInputState(playerUuid, input));
        PlayerClientState update = applyClientStateDelta(playerUuid, input.stateKey(), input.stateValue());
        if (update != null) {
            broadcastClientState(update);
        }
    }

    void sendFullClientStateTo(String targetPlayerUuid) {
        if (targetPlayerUuid == null || targetPlayerUuid.isBlank()) {
            return;
        }
        for (Map.Entry<String, ConcurrentHashMap<String, String>> playerEntry : clientStateByPlayer.entrySet()) {
            String sourcePlayerUuid = playerEntry.getKey();
            Map<String, String> state = playerEntry.getValue();
            if (sourcePlayerUuid == null || state == null || state.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, String> entry : state.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                playerMessageSink.send(UUID.fromString(targetPlayerUuid), com.moud.net.transport.Lane.EVENTS,
                        new PlayerClientState(sourcePlayerUuid, entry.getKey(), entry.getValue() == null ? "" : entry.getValue()));
            }
        }
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

    void replayReady(ServerScene scene) {
        if (scene == null) return;
        SceneRuntime rt = runtimeByScene.get(scene.sceneId());
        if (rt != null) rt.replayReady();
    }

    void refreshEditor(ServerScene scene) {
        if (scene == null) {
            return;
        }
        warnUnsupportedScripts(scene);
        SceneRuntime rt = runtimeByScene.computeIfAbsent(
                scene.sceneId(),
                ignored -> {
                    SceneRuntime created = new SceneRuntime(project, engine, inputsByPlayer, clientStateByPlayer, playerVelocities, tsContext, playerMessageSink);
                    created.setScriptMessageRouter(scriptMessageRouter);
                    created.setConnectedPlayersSupplier(connectedPlayersSupplier);
                    created.setPersistenceService(persistenceService);
                    return created;
                }
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
                ignored -> {
                    SceneRuntime created = new SceneRuntime(project, engine, inputsByPlayer, clientStateByPlayer, playerVelocities, tsContext, playerMessageSink);
                    created.setScriptMessageRouter(scriptMessageRouter);
                    created.setConnectedPlayersSupplier(connectedPlayersSupplier);
                    created.setPersistenceService(persistenceService);
                    return created;
                }
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

    private PlayerClientState applyClientStateDelta(String playerUuid, String key, String value) {
        String safeKey = sanitizeClientStateKey(key);
        if (playerUuid == null || safeKey == null) {
            return null;
        }
        String safeValue = sanitizeClientStateValue(value);
        ConcurrentHashMap<String, String> state = clientStateByPlayer.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());
        if (safeValue.isEmpty()) {
            state.remove(safeKey);
            if (state.isEmpty()) {
                clientStateByPlayer.remove(playerUuid, state);
            }
            return new PlayerClientState(playerUuid, safeKey, "");
        }
        if (state.size() >= 16 && !state.containsKey(safeKey)) {
            return null;
        }
        state.put(safeKey, safeValue);
        return new PlayerClientState(playerUuid, safeKey, safeValue);
    }

    private void broadcastClientState(PlayerClientState update) {
        if (update == null) {
            return;
        }
        for (String playerUuid : inputsByPlayer.keySet()) {
            if (playerUuid == null || playerUuid.isBlank()) {
                continue;
            }
            try {
                playerMessageSink.send(UUID.fromString(playerUuid), com.moud.net.transport.Lane.EVENTS, update);
            } catch (Exception ignored) {
            }
        }
    }

    private static String sanitizeClientStateKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim().toLowerCase();
        if (trimmed.isEmpty() || trimmed.length() > 32) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '.'
                    || c == '_'
                    || c == '-';
            if (!ok) {
                return null;
            }
        }
        return trimmed;
    }

    private static String sanitizeClientStateValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }
}
