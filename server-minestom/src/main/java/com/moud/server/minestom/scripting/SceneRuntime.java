package com.moud.server.minestom.scripting;

import com.moud.server.minestom.scripting.api.*;
import com.moud.server.minestom.scripting.api.modules.*;
import com.moud.server.minestom.scripting.engine.*;
import com.moud.server.minestom.scripting.input.*;
import com.moud.server.minestom.scripting.lang.*;
import com.moud.server.minestom.scripting.luau.*;
import com.moud.server.minestom.scripting.physics.*;
import com.moud.server.minestom.scripting.player.*;
import com.moud.server.minestom.scripting.runtime.*;
import com.moud.server.minestom.scripting.scene.*;
import com.moud.server.minestom.scripting.signal.*;

import com.moud.core.scene.Node;
import com.moud.net.protocol.MultiMeshData;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpResult;
import com.moud.server.minestom.engine.SceneBatchIds;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.net.PlayerMessageSink;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.util.DebugLog;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class SceneRuntime implements RuntimeFacade, ScriptLifecycleManager.DisableHandler, ScriptLifecycleManager.CameraCleanup {
    private static final String LOG_TAG = "script-runtime";

    private final Context ctx;
    private final LuauRuntimeBridge luau;
    private final CollisionSignalEmitter collisionEmitter = new CollisionSignalEmitter();
    private final SceneMutator sceneMutator = new SceneMutator();
    private final MultiMeshManager multiMeshManager = new MultiMeshManager();
    private final SignalBus signalBus = new SignalBus();
    private final TimerTweenScheduler scheduler = new TimerTweenScheduler();
    private final PlayerNetworkSink playerNetworkSink;
    private final PlayerStateManager playerState;
    private final CharacterBodySimulator characterBodySimulator;
    private final ScriptLoader scriptLoader;
    private final RuntimeSceneInstantiator sceneInstantiator;
    private final ScriptLifecycleManager lifecycleManager;
    private volatile ServerScene lastScene;
    private String pendingSceneTransition;

    SceneRuntime(ProjectService project,
                 Engine engine,
                 ConcurrentHashMap<String, PlayerInputState> inputsByPlayer,
                 ConcurrentHashMap<String, float[]> playerVelocities,
                 com.moud.server.minestom.scripting.typescript.TypeScriptContext tsContext,
                 PlayerMessageSink playerMessageSink) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(engine, "engine");
        this.ctx = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT).allowArrayAccess(true).build())
                .allowHostClassLookup(ignored -> false)
                .build();
        this.luau = LuauRuntimeBridge.isRuntimeLinked() ? new LuauRuntimeBridge() : null;
        this.scriptLoader = new ScriptLoader(ctx, tsContext);
        this.playerNetworkSink = new PlayerNetworkSink(Objects.requireNonNull(playerMessageSink, "playerMessageSink"));
        this.playerState = new PlayerStateManager(
                Objects.requireNonNull(inputsByPlayer, "inputsByPlayer"),
                Objects.requireNonNull(playerVelocities, "playerVelocities"),
                new InputMap(),
                this.playerNetworkSink
        );
        this.characterBodySimulator = new CharacterBodySimulator(playerState, sceneMutator, this.playerNetworkSink);
        this.sceneInstantiator = new RuntimeSceneInstantiator(project, sceneMutator);
        this.lifecycleManager = new ScriptLifecycleManager(
                project,
                ctx,
                scriptLoader,
                luau,
                playerState,
                signalBus,
                (scene, nodeId) -> new CoreScriptApi(scene, this, nodeId),
                this,
                this
        );
    }

    void close() {
        try {
            ctx.close(true);
        } catch (Exception ignored) {
        }
        try {
            if (luau != null) {
                luau.close();
            }
        } catch (Exception ignored) {
        }
    }

    void onUiEvent(long nodeId, String signal, float value) {
        signalBus.emit(nodeId, signal, instanceValueMap(), (double) value);
        RuntimeScriptInstance instance = lifecycleManager.getInstance(nodeId);
        if (instance == null || instance.disabled) {
            return;
        }
        String method = "_on_" + signal;
        if (instance.instance.hasMethod(method)) {
            try {
                instance.instance.invokeMethod(method, instance.api, (double) value);
            } catch (ScriptInvocationException e) {
                instance.disabled = true;
            }
        }
    }

    void tick(ServerScene scene, double dtSeconds) {
        Objects.requireNonNull(scene, "scene");
        lastScene = scene;

        scheduler.tickTimers(dtSeconds);
        scheduler.tickTweens(dtSeconds, sceneMutator);
        Set<Long> alive = lifecycleManager.tickScripts(scene, dtSeconds);
        characterBodySimulator.tick(scene, dtSeconds);
        collisionEmitter.emit(scene, lifecycleManager.instances(), signalBus, this::instanceValueMap);
        cleanupDead(scene, alive);
        sceneMutator.flush(scene);
    }

    void refreshEditor(ServerScene scene) {
        if (scene != null) {
            tick(scene, 0.0);
        }
    }

    List<MultiMeshData> getLatestMultiMesh() {
        return multiMeshManager.getLatestMultiMesh();
    }

    List<MultiMeshData> drainMultiMesh() {
        return multiMeshManager.drainMultiMesh();
    }

    void updatePlayerPositions(Map<String, float[]> positions) {
        playerState.updatePlayerPositions(positions);
    }

    void updatePlayerNames(Map<String, String> names) {
        playerState.updatePlayerNames(names);
    }

    Long getActiveCameraForPlayer(String playerUuid) {
        Long nodeId = playerState.getActiveCameraForPlayer(playerUuid);
        if (nodeId == null || nodeId <= 0L) {
            return null;
        }
        ServerScene scene = lastScene;
        if (scene == null) {
            return nodeId;
        }
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null || !"Camera3D".equals(scene.engine().nodeTypes().typeIdFor(node))) {
            playerState.resetCamera(playerUuid);
            return null;
        }
        return nodeId;
    }

    float[] getFollowCameraForPlayer(String playerUuid) {
        return playerState.getFollowCameraForPlayer(playerUuid);
    }

    float[] getScriptCameraForPlayer(String playerUuid) {
        float[] pose = playerState.getScriptCameraForPlayer(playerUuid);
        if (pose == null || pose.length < 6) {
            return null;
        }
        for (int i = 0; i < 6; i++) {
            if (!Float.isFinite(pose[i])) {
                return null;
            }
        }
        return pose;
    }

    String drainPendingSceneTransition() {
        String sceneId = pendingSceneTransition;
        pendingSceneTransition = null;
        return sceneId;
    }

    @Override
    public SceneMutator mutator() {
        return sceneMutator;
    }

    @Override
    public MultiMeshManager multiMeshManager() {
        return multiMeshManager;
    }

    @Override
    public PlayerStateManager playerState() {
        return playerState;
    }

    @Override
    public PlayerNetworkSink playerNetworkSink() {
        return playerNetworkSink;
    }

    @Override
    public TimerTweenScheduler scheduler() {
        return scheduler;
    }

    @Override
    public SignalBus signalBus() {
        return signalBus;
    }

    @Override
    public CharacterBodySimulator characterBodySimulator() {
        return characterBodySimulator;
    }

    @Override
    public Map<Long, ScriptObject> instanceValueMap() {
        return lifecycleManager.instanceValueMap();
    }

    @Override
    public long createRuntimeNode(ServerScene scene, long parentId, String name, String typeId) {
        if (scene == null || parentId < 0L || name == null || name.isBlank() || typeId == null || typeId.isBlank()) {
            return 0L;
        }

        flush(scene);
        long batchId = SceneBatchIds.markRuntime((scene.engine().ticks() << 32) ^ System.nanoTime());
        SceneOpAck ack = scene.applier().apply(
                new SceneOpBatch(batchId, true, List.of(new SceneOp.CreateNode(parentId, name, typeId))));
        if (ack == null || ack.results() == null || ack.results().isEmpty()) {
            return 0L;
        }
        SceneOpResult result = ack.results().getFirst();
        if (result == null || !result.ok() || result.createdId() <= 0L) {
            return 0L;
        }

        sceneMutator.queueSet(result.createdId(), RuntimeScriptKeys.PROP_RUNTIME, "true");
        return result.createdId();
    }

    @Override
    public void flush(ServerScene scene) {
        sceneMutator.flush(scene);
    }

    @Override
    public void queueSceneTransition(String sceneId) {
        if (sceneId != null && !sceneId.isBlank()) {
            pendingSceneTransition = sceneId.trim();
        }
    }

    @Override
    public String getPending(long nodeId, String key) {
        return sceneMutator.getPending(nodeId, key);
    }

    @Override
    public ScriptCallable toScriptCallable(Object callback) {
        if (callback == null) {
            return null;
        }
        if (callback instanceof ScriptCallable callable) {
            return callable;
        }
        if (callback instanceof Value value && value.canExecute()) {
            return JsScriptAdapters.callable(value);
        }
        return null;
    }

    @Override
    public long instantiateScene(ServerScene scene, String scenePath, long parentId) {
        return sceneInstantiator.instantiate(scene, scenePath, parentId);
    }

    @Override
    public ScriptInputApi inputApiForNode(long nodeId) {
        RuntimeScriptInstance instance = lifecycleManager.getInstance(nodeId);
        return instance == null ? null : instance.inputApi;
    }

    @Override
    public void disable(ServerScene scene, RuntimeScriptInstance instance, String stage, Throwable throwable) {
        long nodeId = instance == null ? 0L : instance.nodeId;
        Path scriptFile = instance == null ? null : instance.scriptFile;
        disable(scene, nodeId, scriptFile, stage, throwable);
    }

    @Override
    public void disable(ServerScene scene, long nodeId, Path scriptFile, String stage, Throwable throwable) {
        RuntimeScriptInstance existing = nodeId > 0L ? lifecycleManager.getInstance(nodeId) : null;
        if (existing != null) {
            existing.disabled = true;
        }
        clearCameraOverridesOwnedBy(nodeId);
        String sceneId = scene == null ? "?" : scene.sceneId();
        String file = scriptFile == null ? "?" : scriptFile.toString();
        String msg = throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? "Script error"
                : throwable.getMessage();
        String nodeInfo = "";
        if (scene != null && nodeId > 0L) {
            Node node = scene.engine().sceneTree().getNode(nodeId);
            if (node != null) {
                nodeInfo = " name='" + (node.name() == null ? "" : node.name()) + "' type=" + scene.engine().nodeTypes().typeIdFor(node);
            }
        }
        DebugLog.error(LOG_TAG, "scene=" + sceneId + " nodeId=" + nodeId + nodeInfo
                + " stage=" + (stage == null ? "" : stage) + " file=" + file + " error=" + msg, throwable);
        playerNetworkSink.publishEditorDiagnostic(scene, "ERROR", "Scripts",
                "scene=" + sceneId + " nodeId=" + nodeId + nodeInfo
                        + " stage=" + (stage == null ? "" : stage) + " file=" + file + " error=" + msg);
    }

    @Override
    public void clearCameraOverridesOwnedBy(long ownerNodeId) {
        playerState.clearOverridesOwnedBy(ownerNodeId);
    }

    private void cleanupDead(ServerScene scene, Set<Long> alive) {
        for (Long nodeId : Set.copyOf(lifecycleManager.instanceValueMap().keySet())) {
            if (!alive.contains(nodeId)) {
                multiMeshManager.removeNode(nodeId);
                characterBodySimulator.cleanupNode(nodeId);
            }
        }
        lifecycleManager.cleanupDead(scene, alive);
    }

    static float[] toFloatArray(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof float[] floats) {
            return Arrays.copyOf(floats, floats.length);
        }
        if (data instanceof double[] doubles) {
            float[] out = new float[doubles.length];
            for (int i = 0; i < doubles.length; i++) {
                out[i] = (float) doubles[i];
            }
            return out;
        }
        if (data instanceof int[] ints) {
            float[] out = new float[ints.length];
            for (int i = 0; i < ints.length; i++) {
                out[i] = ints[i];
            }
            return out;
        }
        if (data instanceof long[] longs) {
            float[] out = new float[longs.length];
            for (int i = 0; i < longs.length; i++) {
                out[i] = longs[i];
            }
            return out;
        }
        if (data instanceof Object[] objects) {
            float[] out = new float[objects.length];
            for (int i = 0; i < objects.length; i++) {
                if (!(objects[i] instanceof Number number)) {
                    return null;
                }
                out[i] = number.floatValue();
            }
            return out;
        }
        if (data instanceof List<?> list) {
            float[] out = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (!(item instanceof Number number)) {
                    return null;
                }
                out[i] = number.floatValue();
            }
            return out;
        }
        if (data instanceof Value value) {
            if (!value.hasArrayElements()) {
                return null;
            }
            int len = (int) value.getArraySize();
            float[] out = new float[len];
            for (int i = 0; i < len; i++) {
                out[i] = (float) value.getArrayElement(i).asDouble();
            }
            return out;
        }
        return null;
    }
}
