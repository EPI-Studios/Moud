package com.moud.server.minestom.scripting;

import com.moud.core.physics.BodyHandle;
import com.moud.core.scene.Node;
import com.moud.core.scene.SceneFile;
import com.moud.core.scene.SceneTreeMutator;
import com.moud.server.minestom.scene.SceneFileIO;
import com.moud.net.protocol.MultiMeshData;
import com.moud.net.protocol.CursorState;
import com.moud.net.protocol.PlayerMotion;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpResult;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.net.PlayerMessageSink;
import com.moud.server.minestom.physics.CollisionEvent;
import com.moud.server.minestom.physics.JoltPhysicsWorld;
import com.moud.server.minestom.engine.SceneBatchIds;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.util.DebugLog;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SceneRuntime {
    private static final String LOG_TAG = "script-runtime";

    private final ProjectService project;
    private final PlayerMessageSink playerMessageSink;
    private final ConcurrentHashMap<String, PlayerInputState> inputsByPlayer;
    private final ConcurrentHashMap<String, float[]> playerVelocities;
    private final ConcurrentHashMap<String, float[]> playerPositions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> playerNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OwnedValue<Long>> activeCameraByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OwnedValue<float[]>> followCameraByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OwnedValue<float[]>> scriptCameraByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OwnedValue<boolean[]>> cursorStateByPlayer = new ConcurrentHashMap<>();
    private final Context ctx;
    private final ScriptLoader scriptLoader;
    private final LuauRuntimeBridge luau;
    private final CollisionSignalEmitter collisionEmitter = new CollisionSignalEmitter();
    private final Map<Long, NodeInstance> instances = new HashMap<>();
    private final ArrayList<SceneOp> pendingOps = new ArrayList<>();
    private final HashMap<String, String> pendingProps = new HashMap<>();
    private final HashMap<Long, float[]> pendingMultiMesh = new HashMap<>();
    private final HashMap<Long, float[]> latestMultiMesh  = new HashMap<>();
    private long cachedTargetsGraphRevision = Long.MIN_VALUE;
    private final ArrayList<Target> cachedTargets = new ArrayList<>();
    private volatile ServerScene lastScene;
    private final ArrayList<PendingTimer> pendingTimers = new ArrayList<>();
    private final ArrayList<PendingTween> pendingTweens = new ArrayList<>();
    private String pendingSceneTransition = null;
    private final InputMap inputMap = new InputMap();
    private final SignalBus signalBus = new SignalBus();
    private final ConcurrentHashMap<String, ScriptInputApi> inputApiByPlayer = new ConcurrentHashMap<>();

    private record OwnedValue<T>(long ownerNodeId, T value) {}

    SceneRuntime(ProjectService project, Engine engine,
                 ConcurrentHashMap<String, PlayerInputState> inputsByPlayer,
                 ConcurrentHashMap<String, float[]> playerVelocities,
                 com.moud.server.minestom.scripting.typescript.TypeScriptContext tsContext,
                 PlayerMessageSink playerMessageSink) {
        this.project = Objects.requireNonNull(project, "project");
        this.playerMessageSink = Objects.requireNonNull(playerMessageSink, "playerMessageSink");
        Objects.requireNonNull(engine, "engine");
        this.inputsByPlayer = Objects.requireNonNull(inputsByPlayer, "inputsByPlayer");
        this.playerVelocities = Objects.requireNonNull(playerVelocities, "playerVelocities");
        this.ctx = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT).allowArrayAccess(true).build())
                .allowHostClassLookup(ignored -> false)
                .build();
        this.scriptLoader = new ScriptLoader(ctx, tsContext);
        this.luau = LuauRuntimeBridge.isRuntimeLinked() ? new LuauRuntimeBridge() : null;
    }

    void close() {
        try { ctx.close(true); } catch (Exception ignored) {}
        try { if (luau != null) luau.close(); } catch (Exception ignored) {}
    }

    void onUiEvent(long nodeId, String signal, float value) {
        signalBus.emit(nodeId, signal, instanceValueMap(), (double) value);
        NodeInstance inst = instances.get(nodeId);
        if (inst == null || inst.disabled) return;
        String method = "_on_" + signal;
        if (inst.instance.hasMethod(method)) {
            try {
                inst.instance.invokeMethod(method, inst.api, (double) value);
            } catch (ScriptInvocationException e) {
                inst.disabled = true;
            }
        }
    }

    void tick(ServerScene scene, double dtSeconds) {
        Objects.requireNonNull(scene, "scene");
        lastScene = scene;

        tickTimers(dtSeconds);
        tickTweens(dtSeconds);

        ArrayList<Target> targets = targetsFor(scene);
        HashSet<Long> alive = new HashSet<>(targets.size());

        for (Target target : targets) {
            if (target == null || target.nodeId <= 0L || target.scriptPath == null || target.language == null) continue;
            long nodeId = target.nodeId;
            alive.add(nodeId);

            Node node = scene.engine().sceneTree().getNode(nodeId);
            if (node == null) continue;

            Path scriptFile;
            try {
                scriptFile = project.resolveProjectPath(target.scriptPath);
            } catch (Exception e) {
                disableInstance(scene, nodeId, null, "resolvePath", e);
                continue;
            }

            NodeInstance inst = instances.get(nodeId);
            long programModifiedMs;
            if (target.language == ScriptLanguage.JAVASCRIPT || target.language == ScriptLanguage.TYPESCRIPT) {
                ScriptLoader.Program program = scriptLoader.programFor(scriptFile, target.language);
                if (program == null) {
                    disableInstance(scene, nodeId, scriptFile, "loadProgram",
                            new IllegalStateException("Script load failed: " + scriptFile.toAbsolutePath()));
                    continue;
                }
                programModifiedMs = program.modifiedMs();
            } else if (target.language == ScriptLanguage.LUAU) {
                LuauRuntimeBridge.Program program = luau == null ? null : luau.programFor(scriptFile);
                if (program == null) {
                    disableInstance(scene, nodeId, scriptFile, "loadProgram",
                            new IllegalStateException("Script load failed: " + scriptFile.toAbsolutePath()));
                    continue;
                }
                programModifiedMs = program.modifiedMs();
            } else {
                continue;
            }

            if (inst == null || inst.language != target.language || !scriptFile.equals(inst.scriptFile) || inst.programModifiedMs != programModifiedMs) {
                if (inst != null) {
                    invokeLifecycle(scene, inst, "_exit_tree", "_exitTree");
                    clearCameraOverridesOwnedBy(nodeId);
                    try {
                        inst.instance.close();
                    } catch (Exception ignored) {
                    }
                }
                try {
                    RuntimeApi api = new RuntimeApi(scene, nodeId);
                    ScriptObject scriptInstance;
                    if (target.language == ScriptLanguage.JAVASCRIPT || target.language == ScriptLanguage.TYPESCRIPT) {
                        ScriptLoader.Program program = scriptLoader.programFor(scriptFile, target.language);
                        Value jsInstance = program == null ? null : scriptLoader.createNodeInstance(program.exports());
                        scriptInstance = jsInstance == null ? null : JsScriptAdapters.object(jsInstance);
                    } else {
                        LuauRuntimeBridge.Program program = luau == null ? null : luau.programFor(scriptFile);
                        scriptInstance = program == null ? null : luau.createNodeInstance(program, api);
                    }
                    if (scriptInstance == null) {
                        disableInstance(scene, nodeId, scriptFile, "createInstance",
                                new IllegalStateException("Script did not return an instance"));
                        continue;
                    }
                    inst = new NodeInstance(nodeId, scriptFile, target.language, programModifiedMs, scriptInstance, api);
                } catch (Exception e) {
                    disableInstance(scene, nodeId, scriptFile, "createInstance", e);
                    continue;
                }
                instances.put(nodeId, inst);
                invokeLifecycle(scene, inst, "_enter_tree", "_enterTree");
                inst.readyCalled = false;
                inst.disabled = false;
                inst.lastInputClientTick = -1L;
            }

            if (inst.disabled) continue;

            if (!inst.readyCalled) {
                invokeLifecycle(scene, inst, "_ready", null);
                inst.readyCalled = true;
            }

            maybeDispatchInput(scene, node, inst);
            updateInputApi(node, inst);

            invokeProcess(scene, inst, "_physics_process", "_physicsProcess", dtSeconds);
            invokeProcess(scene, inst, "_process", null, dtSeconds);
        }

        collisionEmitter.emit(scene, instances, signalBus, this::instanceValueMap);
        cleanupDead(scene, alive);
        flush(scene);
    }

    void refreshEditor(ServerScene scene) {
        if (scene == null) {
            return;
        }
        tick(scene, 0.0);
    }

    private ArrayList<Target> targetsFor(ServerScene scene) {
        long graphRev = scene.engine().sceneRevision();
        if (cachedTargetsGraphRevision == graphRev) return cachedTargets;
        cachedTargetsGraphRevision = graphRev;
        cachedTargets.clear();

        ArrayList<Node> stack = new ArrayList<>();
        stack.add(scene.engine().sceneTree().root());
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            if (node == null) continue;

            ScriptReference script = ScriptPaths.parseScript(node.getProperty(RuntimeScriptKeys.SCRIPT_KEY));
            if (script != null && (script.language() == ScriptLanguage.JAVASCRIPT
                    || script.language() == ScriptLanguage.TYPESCRIPT
                    || (script.language() == ScriptLanguage.LUAU && luau != null))) {
                cachedTargets.add(new Target(node.nodeId(), script.path(), script.language()));
            }

            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) stack.add(children.get(i));
        }
        return cachedTargets;
    }

    private void updateInputApi(Node node, NodeInstance inst) {
        if (node == null || inst == null) return;
        PlayerInputState state = null;
        String ownerUuid = RuntimeScriptUtil.resolveOwnerUuid(node);
        if (ownerUuid != null) {
            state = inputsByPlayer.get(ownerUuid);
        } else if (inputsByPlayer.size() == 1) {
            state = inputsByPlayer.values().iterator().next();
            ownerUuid = state.playerUuid();
        }
        if (state == null) return;
        ScriptInputApi api = inputApiByPlayer.computeIfAbsent(ownerUuid, k -> new ScriptInputApi(inputMap));
        api.update(state);
        inst.inputApi = api;
    }

    Map<Long, ScriptObject> instanceValueMap() {
        HashMap<Long, ScriptObject> map = new HashMap<>(instances.size());
        for (Map.Entry<Long, NodeInstance> e : instances.entrySet()) {
            NodeInstance ni = e.getValue();
            if (ni != null && !ni.disabled && ni.instance != null) map.put(e.getKey(), ni.instance);
        }
        return map;
    }

    private void cleanupDead(ServerScene scene, Set<Long> alive) {
        Iterator<Map.Entry<Long, NodeInstance>> it = instances.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, NodeInstance> entry = it.next();
            long nodeId = entry.getKey();
            if (alive.contains(nodeId)) continue;
            NodeInstance inst = entry.getValue();
            if (inst != null) invokeLifecycle(scene, inst, "_exit_tree", "_exitTree");
            clearCameraOverridesOwnedBy(nodeId);
            signalBus.removeNode(nodeId);
            if (inst != null && inst.instance != null) {
                try {
                    inst.instance.close();
                } catch (Exception ignored) {
                }
            }
            boolean hadMultiMesh = pendingMultiMesh.remove(nodeId) != null;
            hadMultiMesh |= latestMultiMesh.remove(nodeId) != null;
            if (hadMultiMesh) {
                pendingMultiMesh.put(nodeId, new float[0]);
            }
            it.remove();
        }
    }

    private void clearCameraOverridesOwnedBy(long ownerNodeId) {
        if (ownerNodeId <= 0L) return;
        activeCameraByPlayer.entrySet().removeIf(e -> {
            OwnedValue<Long> ov = e.getValue();
            return ov != null && ov.ownerNodeId() == ownerNodeId;
        });
        followCameraByPlayer.entrySet().removeIf(e -> {
            OwnedValue<float[]> ov = e.getValue();
            return ov != null && ov.ownerNodeId() == ownerNodeId;
        });
        scriptCameraByPlayer.entrySet().removeIf(e -> {
            OwnedValue<float[]> ov = e.getValue();
            return ov != null && ov.ownerNodeId() == ownerNodeId;
        });
        ArrayList<String> clearedCursorPlayers = new ArrayList<>();
        cursorStateByPlayer.entrySet().removeIf(e -> {
            OwnedValue<boolean[]> ov = e.getValue();
            boolean owned = ov != null && ov.ownerNodeId() == ownerNodeId;
            if (owned && e.getKey() != null) {
                clearedCursorPlayers.add(e.getKey());
            }
            return owned;
        });
        for (String playerUuid : clearedCursorPlayers) {
            try {
                playerMessageSink.send(UUID.fromString(playerUuid), Lane.EVENTS, new CursorState(false, true));
            } catch (Exception ignored) {
            }
        }
    }

    private void maybeDispatchInput(ServerScene scene, Node node, NodeInstance inst) {
        if (scene == null || node == null || inst == null) return;
        PlayerInputState state = null;
        String ownerUuid = RuntimeScriptUtil.resolveOwnerUuid(node);
        if (ownerUuid != null) {
            state = inputsByPlayer.get(ownerUuid);
        } else if (inputsByPlayer.size() == 1) {
            state = inputsByPlayer.values().iterator().next();
        }
        if (state == null) return;
        if (!inst.instance.hasMethod("_input")) return;
        if (state.clientTick() == inst.lastInputClientTick) return;
        inst.lastInputClientTick = state.clientTick();
        try {
            inst.instance.invokeMethod("_input", inst.api, new InputEvent(state));
        } catch (ScriptInvocationException e) {
            disableInstance(scene, inst, "_input", e);
        }
    }

    private void invokeLifecycle(ServerScene scene, NodeInstance inst, String primary, String fallback) {
        if (scene == null || inst == null || primary == null) return;
        String member = resolveMember(inst.instance, primary, fallback);
        if (member == null) return;
        try {
            inst.instance.invokeMethod(member, inst.api);
        } catch (ScriptInvocationException e) {
            disableInstance(scene, inst, member, e);
        }
    }

    private static final double EMA_ALPHA    = 0.2;
    private static final double WARN_MS      = 80.0;
    private static final double KILL_MS      = 100.0;
    private static final int    STRIKE_LIMIT = 3;
    private static final int    RECOVERY_RUNS = 10;

    static final class TimingBudget {
        double emaMs;
        int strikes;
        int cleanStreak;

        boolean record(double elapsedMs) {
            emaMs = EMA_ALPHA * elapsedMs + (1 - EMA_ALPHA) * emaMs;

            if (emaMs > KILL_MS) {
                strikes++;
                cleanStreak = 0;
                return strikes >= STRIKE_LIMIT;
            }

            if (emaMs < WARN_MS) {
                cleanStreak++;
                if (cleanStreak >= RECOVERY_RUNS) {
                    strikes = Math.max(0, strikes - 1);
                    cleanStreak = 0;
                }
            }
            return false;
        }
    }

    private void invokeProcess(ServerScene scene, NodeInstance inst, String primary, String fallback, double dtSeconds) {
        if (scene == null || inst == null || primary == null) return;
        String member = resolveMember(inst.instance, primary, fallback);
        if (member == null) return;

        if (inst.language == ScriptLanguage.JAVASCRIPT) {
            ctx.getBindings("js").putMember("Input", inst.inputApi);
        }

        long start = System.nanoTime();
        try {
            inst.instance.invokeMethod(member, inst.api, dtSeconds);
        } catch (ScriptInvocationException e) {
            disableInstance(scene, inst, member, e);
            return;
        }

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        if (inst.budget.record(elapsedMs)) {
            disableInstance(scene, inst.nodeId, inst.scriptFile, member,
                    new IllegalStateException(
                            "Script disabled: %s averaged %.1fms over threshold (%dms) %d times"
                                    .formatted(member, inst.budget.emaMs, (long) KILL_MS, STRIKE_LIMIT)));
        }
    }

    private static String resolveMember(ScriptObject obj, String primary, String fallback) {
        if (obj == null || primary == null) return null;
        if (obj.hasMethod(primary)) return primary;
        if (fallback != null && obj.hasMethod(fallback)) return fallback;
        return null;
    }

    private void disableInstance(ServerScene scene, NodeInstance inst, String stage, Throwable t) {
        long nodeId = inst == null ? 0L : inst.nodeId;
        Path scriptFile = inst == null ? null : inst.scriptFile;
        disableInstance(scene, nodeId, scriptFile, stage, t);
    }

    private void disableInstance(long nodeId, String message) {
        disableInstance(lastScene, nodeId, null, "error",
                new IllegalStateException(message == null ? "Script error" : message));
    }

    private void disableInstance(ServerScene scene, long nodeId, Path scriptFile, String stage, Throwable t) {
        NodeInstance existing = nodeId > 0L ? instances.get(nodeId) : null;
        if (existing != null) existing.disabled = true;
        clearCameraOverridesOwnedBy(nodeId);
        String sceneId = scene == null ? "?" : scene.sceneId();
        String file = scriptFile == null ? "?" : scriptFile.toString();
        String msg = (t == null || t.getMessage() == null || t.getMessage().isBlank()) ? "Script error" : t.getMessage();
        String st = stage == null ? "" : stage;

        String nodeInfo = "";
        if (scene != null && nodeId > 0L) {
            Node n = scene.engine().sceneTree().getNode(nodeId);
            if (n != null) {
                String name = n.name() == null ? "" : n.name();
                String type = scene.engine().nodeTypes().typeIdFor(n);
                nodeInfo = " name='" + name + "' type=" + type;
            }
        }
        DebugLog.error(LOG_TAG, "scene=" + sceneId + " nodeId=" + nodeId + nodeInfo
                + " stage=" + st + " file=" + file + " error=" + msg, t);
    }

    void queueSet(long nodeId, String key, String value) {
        if (nodeId <= 0L || key == null || key.isBlank() || value == null) return;
        pendingOps.add(new SceneOp.SetProperty(nodeId, key, value));
        pendingProps.put(propKey(nodeId, key), value);
    }

    void queueRemove(long nodeId, String key) {
        if (nodeId <= 0L || key == null || key.isBlank()) return;
        pendingOps.add(new SceneOp.RemoveProperty(nodeId, key));
        pendingProps.remove(propKey(nodeId, key));
    }

    void queueRename(long nodeId, String name) {
        if (nodeId <= 0L || name == null || name.isBlank()) return;
        pendingOps.add(new SceneOp.Rename(nodeId, name));
    }

    void queueReparent(long nodeId, long newParentId) {
        if (nodeId <= 0L || newParentId < 0L) return;
        pendingOps.add(new SceneOp.Reparent(nodeId, newParentId, Integer.MAX_VALUE));
    }

    void queueFree(long nodeId) {
        if (nodeId <= 0L) return;
        pendingOps.add(new SceneOp.QueueFree(nodeId));
    }

    long createRuntimeNode(ServerScene scene, long parentId, String name, String typeId) {
        if (scene == null || parentId < 0L || name == null || name.isBlank() || typeId == null || typeId.isBlank()) {
            return 0L;
        }

        flush(scene);

        long batchId = SceneBatchIds.markRuntime((scene.engine().ticks() << 32) ^ System.nanoTime());
        SceneOpAck ack = scene.applier().apply(
                new SceneOpBatch(batchId, true, List.of(new SceneOp.CreateNode(parentId, name, typeId))));
        if (ack == null || ack.results() == null || ack.results().isEmpty()) return 0L;
        SceneOpResult r = ack.results().getFirst();
        if (r == null || !r.ok() || r.createdId() <= 0L) return 0L;

        queueSet(r.createdId(), RuntimeScriptKeys.PROP_RUNTIME, "true");
        return r.createdId();
    }

    String getPending(long nodeId, String key) {
        if (nodeId <= 0L || key == null || key.isBlank()) return null;
        return pendingProps.get(propKey(nodeId, key));
    }

    void flush(ServerScene scene) {
        if (scene == null) {
            pendingOps.clear();
            pendingProps.clear();
            return;
        }
        if (pendingOps.isEmpty()) {
            pendingProps.clear();
            return;
        }
        if (DebugLog.enabled()) {
            int n = pendingOps.size();
            int show = Math.min(8, n);
            StringBuilder sb = new StringBuilder(256);
            sb.append("flush ops=").append(n).append(" preview=[");
            for (int i = 0; i < show; i++) {
                SceneOp op = pendingOps.get(i);
                if (i > 0) sb.append(", ");
                sb.append(op == null ? "null" : op.getClass().getSimpleName());
            }
            if (show < n) sb.append(", ...");
            sb.append(']');
            DebugLog.debug(LOG_TAG, "scene=" + scene.sceneId() + " " + sb);
        }
        long batchId = SceneBatchIds.markRuntime((scene.engine().ticks() << 32) ^ System.nanoTime());
        SceneOpAck ack = scene.applier().apply(new SceneOpBatch(batchId, false, List.copyOf(pendingOps)));
        pendingOps.clear();
        pendingProps.clear();
        if (ack == null) {
            DebugLog.error(LOG_TAG, "scene=" + scene.sceneId() + " Scene apply failed (null ack)");
            return;
        }
        for (SceneOpResult r : ack.results()) {
            if (r != null && !r.ok()) {
                String msg = r.message();
                if (msg == null || msg.isBlank()) msg = r.error() == null ? "SceneOp failed" : r.error().name();
                DebugLog.error(LOG_TAG, "scene=" + scene.sceneId() + " SceneOp failed: " + msg);
            }
        }
    }

    List<MultiMeshData> getLatestMultiMesh() {
        if (latestMultiMesh.isEmpty()) return List.of();
        List<MultiMeshData> out = new ArrayList<>(latestMultiMesh.size());
        for (Map.Entry<Long, float[]> e : latestMultiMesh.entrySet()) {
            float[] v = e.getValue();
            out.add(new MultiMeshData(e.getKey(), 0, v == null ? 0 : v.length, v));
        }
        return out;
    }

    List<MultiMeshData> drainMultiMesh() {
        if (pendingMultiMesh.isEmpty()) return List.of();
        List<MultiMeshData> out = new ArrayList<>(pendingMultiMesh.size());
        for (Map.Entry<Long, float[]> e : pendingMultiMesh.entrySet()) {
            float[] v = e.getValue();
            out.add(new MultiMeshData(e.getKey(), 0, v == null ? 0 : v.length, v));
        }
        pendingMultiMesh.clear();
        return out;
    }

    void updatePlayerPositions(Map<String, float[]> positions) {
        playerPositions.clear();
        if (positions != null) playerPositions.putAll(positions);
    }

    void updatePlayerNames(Map<String, String> names) {
        playerNames.clear();
        if (names != null) playerNames.putAll(names);
    }

    Long getActiveCameraForPlayer(String playerUuid) {
        if (playerUuid == null) return null;
        OwnedValue<Long> ov = activeCameraByPlayer.get(playerUuid);
        if (ov == null) return null;
        Long nodeId = ov.value();
        if (nodeId == null || nodeId <= 0L) {
            activeCameraByPlayer.remove(playerUuid, ov);
            return null;
        }
        ServerScene scene = lastScene;
        if (scene == null) return nodeId;
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null || !"Camera3D".equals(scene.engine().nodeTypes().typeIdFor(node))) {
            activeCameraByPlayer.remove(playerUuid, ov);
            return null;
        }
        return nodeId;
    }

    float[] getFollowCameraForPlayer(String playerUuid) {
        if (playerUuid == null) return null;
        OwnedValue<float[]> ov = followCameraByPlayer.get(playerUuid);
        return ov == null ? null : ov.value();
    }

    float[] getScriptCameraForPlayer(String playerUuid) {
        if (playerUuid == null) return null;
        OwnedValue<float[]> ov = scriptCameraByPlayer.get(playerUuid);
        if (ov == null) return null;
        float[] pose = ov.value();
        if (pose == null || pose.length < 6) {
            scriptCameraByPlayer.remove(playerUuid, ov);
            return null;
        }
        for (int i = 0; i < 6; i++) {
            if (!Float.isFinite(pose[i])) {
                scriptCameraByPlayer.remove(playerUuid, ov);
                return null;
            }
        }
        return pose;
    }

    void queueSceneTransition(String sceneId) {
        if (sceneId != null && !sceneId.isBlank()) pendingSceneTransition = sceneId.trim();
    }

    String drainPendingSceneTransition() {
        String s = pendingSceneTransition;
        pendingSceneTransition = null;
        return s;
    }

    void scheduleTimer(double seconds, ScriptCallable callback) {
        if (callback == null) return;
        pendingTimers.add(new PendingTimer(Math.max(0.0, seconds), callback));
    }

    private void tickTimers(double dtSeconds) {
        if (pendingTimers.isEmpty()) return;
        Iterator<PendingTimer> it = pendingTimers.iterator();
        while (it.hasNext()) {
            PendingTimer t = it.next();
            t.timeLeft -= dtSeconds;
            if (t.timeLeft <= 0.0) {
                it.remove();
                try {
                    t.callback.invoke();
                } catch (ScriptInvocationException e) {
                    DebugLog.error(LOG_TAG, "timer callback error: " + e.getMessage(), e);
                }
            }
        }
    }

    private void tickTweens(double dtSeconds) {
        if (pendingTweens.isEmpty()) return;
        Iterator<PendingTween> it = pendingTweens.iterator();
        while (it.hasNext()) {
            PendingTween t = it.next();
            t.elapsed += dtSeconds;
            float factor = (float) Math.min(1.0, t.elapsed / t.duration);
            float value = t.fromValue + (t.toValue - t.fromValue) * factor;
            queueSet(t.nodeId, t.prop, RuntimeScriptUtil.trimFloat(value));
            if (factor >= 1.0f) {
                it.remove();
            }
        }
    }

    long instantiateScene(ServerScene scene, String scenePath, long parentId) {
        if (scene == null || scenePath == null || scenePath.isBlank() || parentId < 0) return 0L;
        flush(scene);

        Path file;
        try {
            file = project.resolveProjectPath(scenePath);
        } catch (Exception e) {
            DebugLog.error(LOG_TAG, "instantiate: cannot resolve path '" + scenePath + "': " + e.getMessage());
            return 0L;
        }

        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            DebugLog.error(LOG_TAG, "instantiate: cannot read file '" + file + "': " + e.getMessage());
            return 0L;
        }

        SceneFile sceneFile;
        try {
            sceneFile = SceneFileIO.parse(json);
        } catch (Exception e) {
            DebugLog.error(LOG_TAG, "instantiate: parse error: " + e.getMessage());
            return 0L;
        }

        List<SceneTreeMutator.NodeSpec> specs = SceneFileIO.toNodeSpecs(sceneFile);
        if (specs.isEmpty()) return 0L;

        HashMap<Long, SceneTreeMutator.NodeSpec> specById = new HashMap<>(specs.size());
        for (SceneTreeMutator.NodeSpec s : specs) {
            if (s != null && s.nodeId() > 0) specById.put(s.nodeId(), s);
        }

        HashMap<Long, Long> idMap = new HashMap<>(specs.size());
        Deque<Long> queue = new ArrayDeque<>();
        long firstRootId = 0L;

        for (SceneTreeMutator.NodeSpec s : specs) {
            if (s != null && (s.parentId() <= 0 || !specById.containsKey(s.parentId()))) {
                queue.add(s.nodeId());
            }
        }

        while (!queue.isEmpty()) {
            long fileId = queue.poll();
            SceneTreeMutator.NodeSpec spec = specById.get(fileId);
            if (spec == null) continue;

            long targetParent;
            if (spec.parentId() <= 0 || !specById.containsKey(spec.parentId())) {
                targetParent = parentId;
            } else {
                Long mapped = idMap.get(spec.parentId());
                if (mapped == null) continue;
                targetParent = mapped;
            }

            long batchId = SceneBatchIds.markRuntime((scene.engine().ticks() << 32) ^ System.nanoTime());
            SceneOpAck ack = scene.applier().apply(
                    new SceneOpBatch(batchId, true, List.of(new SceneOp.CreateNode(targetParent, spec.name(), spec.typeId()))));
            if (ack == null || ack.results() == null || ack.results().isEmpty()) continue;
            SceneOpResult r = ack.results().getFirst();
            if (r == null || !r.ok() || r.createdId() <= 0L) continue;

            long newId = r.createdId();
            idMap.put(fileId, newId);
            if (firstRootId == 0L && targetParent == parentId) firstRootId = newId;

            queueSet(newId, RuntimeScriptKeys.PROP_RUNTIME, "true");
            for (Map.Entry<String, String> entry : spec.properties().entrySet()) {
                String k = entry.getKey();
                if (k == null || k.startsWith("@")) continue;
                queueSet(newId, k, entry.getValue());
            }

            for (SceneTreeMutator.NodeSpec child : specs) {
                if (child != null && child.parentId() == fileId) {
                    queue.add(child.nodeId());
                }
            }
        }

        return firstRootId;
    }

    private ScriptCallable toScriptCallable(Object callback) {
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

    private static float[] toFloatArray(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof float[] floats) {
            return Arrays.copyOf(floats, floats.length);
        }
        if (data instanceof double[] doubles) {
            float[] out = new float[doubles.length];
            for (int i = 0; i < doubles.length; i++) out[i] = (float) doubles[i];
            return out;
        }
        if (data instanceof int[] ints) {
            float[] out = new float[ints.length];
            for (int i = 0; i < ints.length; i++) out[i] = ints[i];
            return out;
        }
        if (data instanceof long[] longs) {
            float[] out = new float[longs.length];
            for (int i = 0; i < longs.length; i++) out[i] = longs[i];
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

    private static String propKey(long nodeId, String key) {
        return nodeId + "\u0000" + key;
    }

    private record Target(long nodeId, String scriptPath, ScriptLanguage language) {}

    private static final class PendingTimer {
        double timeLeft;
        final ScriptCallable callback;
        PendingTimer(double timeLeft, ScriptCallable callback) {
            this.timeLeft = timeLeft;
            this.callback = callback;
        }
    }

    private static final class PendingTween {
        final long nodeId;
        final String prop;
        final float fromValue;
        final float toValue;
        final double duration;
        double elapsed;
        PendingTween(long nodeId, String prop, float fromValue, float toValue, double duration) {
            this.nodeId = nodeId;
            this.prop = prop;
            this.fromValue = fromValue;
            this.toValue = toValue;
            this.duration = duration;
            this.elapsed = 0.0;
        }
    }

    static final class NodeInstance {
        final long nodeId;
        final Path scriptFile;
        final ScriptLanguage language;
        final long programModifiedMs;
        final ScriptObject instance;
        final RuntimeApi api;
        boolean readyCalled;
        boolean disabled;
        final TimingBudget budget = new TimingBudget();
        long lastInputClientTick;
        ScriptInputApi inputApi;

        NodeInstance(long nodeId, Path scriptFile, ScriptLanguage language, long programModifiedMs, ScriptObject instance, RuntimeApi api) {
            this.nodeId = nodeId;
            this.scriptFile = scriptFile;
            this.language = language;
            this.programModifiedMs = programModifiedMs;
            this.instance = instance;
            this.api = api;
        }
    }

    public final class RuntimeApi {
        private final ServerScene scene;
        private final long selfId;

        RuntimeApi(ServerScene scene, long selfId) {
            this.scene = Objects.requireNonNull(scene, "scene");
            this.selfId = selfId;
        }

        @HostAccess.Export
        public void log(String message) {
            String msg = message == null ? "" : message;
            DebugLog.info(LOG_TAG, "scene=" + scene.sceneId() + " nodeId=" + selfId + " " + msg);
        }

        @HostAccess.Export
        public long id() { return selfId; }

        @HostAccess.Export
        public String name() {
            Node n = scene.engine().sceneTree().getNode(selfId);
            return n == null || n.name() == null ? "" : n.name();
        }

        @HostAccess.Export
        public String type() {
            Node n = scene.engine().sceneTree().getNode(selfId);
            return n == null ? "" : scene.engine().nodeTypes().typeIdFor(n);
        }

        @HostAccess.Export
        public String typeOf(long nodeId) {
            Node n = scene.engine().sceneTree().getNode(nodeId);
            return n == null ? "" : scene.engine().nodeTypes().typeIdFor(n);
        }

        @HostAccess.Export
        public String get(String key) { return get(selfId, key); }

        @HostAccess.Export
        public String get(long nodeId, String key) {
            if (nodeId <= 0L || key == null || key.isBlank()) return null;
            String pending = SceneRuntime.this.getPending(nodeId, key);
            if (pending != null) return pending;
            Node n = scene.engine().sceneTree().getNode(nodeId);
            return n == null ? null : n.getProperty(key);
        }

        @HostAccess.Export
        public void set(String key, String value) { set(selfId, key, value); }

        @HostAccess.Export
        public void set(long nodeId, String key, String value) {
            SceneRuntime.this.queueSet(nodeId, key, value);
        }

        @HostAccess.Export
        public void setNumber(String key, double value) {
            set(key, RuntimeScriptUtil.trimFloat((float) value));
        }

        @HostAccess.Export
        public void setNumber(long nodeId, String key, double value) {
            set(nodeId, key, RuntimeScriptUtil.trimFloat((float) value));
        }

        @HostAccess.Export
        public double getNumber(String key, double fallback) {
            String v = get(key);
            if (v == null || v.isBlank()) return fallback;
            try {
                double n = Double.parseDouble(v.trim());
                return Double.isFinite(n) ? n : fallback;
            } catch (Exception ignored) { return fallback; }
        }

        @HostAccess.Export
        public double getNumber(long nodeId, String key, double fallback) {
            String v = get(nodeId, key);
            if (v == null || v.isBlank()) return fallback;
            try {
                double n = Double.parseDouble(v.trim());
                return Double.isFinite(n) ? n : fallback;
            } catch (Exception ignored) { return fallback; }
        }

        @HostAccess.Export
        public void remove(String key) { remove(selfId, key); }

        @HostAccess.Export
        public void remove(long nodeId, String key) {
            SceneRuntime.this.queueRemove(nodeId, key);
        }

        @HostAccess.Export
        public void rename(String name) { rename(selfId, name); }

        @HostAccess.Export
        public void rename(long nodeId, String name) {
            SceneRuntime.this.queueRename(nodeId, name);
        }

        @HostAccess.Export
        public void reparent(long nodeId, long newParentId) {
            SceneRuntime.this.queueReparent(nodeId, newParentId);
        }

        @HostAccess.Export
        public void free(long nodeId) {
            SceneRuntime.this.queueFree(nodeId);
        }

        @HostAccess.Export
        public long createRuntime(long parentId, String name, String typeId) {
            return SceneRuntime.this.createRuntimeNode(scene, parentId, name, typeId);
        }

        @HostAccess.Export
        public void flush() {
            SceneRuntime.this.flush(scene);
        }

        @HostAccess.Export
        public String getString(String key, String fallback) {
            String v = get(key);
            return v != null ? v : (fallback != null ? fallback : "");
        }

        @HostAccess.Export
        public String getString(long nodeId, String key, String fallback) {
            String v = get(nodeId, key);
            return v != null ? v : (fallback != null ? fallback : "");
        }

        @HostAccess.Export
        public void setUniform(long nodeId, String name, double... values) {
            if (nodeId <= 0L || name == null || name.isBlank() || values == null) return;
            List<Float> floats = new ArrayList<>(values.length);
            for (double v : values) floats.add((float) v);
            scene.engine().setUniform(nodeId, name, List.copyOf(floats));
        }

        @HostAccess.Export
        public long[] findNodesByType(String type) {
            if (type == null || type.isBlank()) return new long[0];
            List<Long> result = new ArrayList<>();
            Deque<Node> queue = new ArrayDeque<>();
            queue.add(scene.engine().sceneTree().root());
            while (!queue.isEmpty()) {
                Node n = queue.poll();
                if (type.equals(scene.engine().nodeTypes().typeIdFor(n))) {
                    result.add(n.nodeId());
                }
                queue.addAll(n.children());
            }
            long[] arr = new long[result.size()];
            for (int i = 0; i < result.size(); i++) arr[i] = result.get(i);
            return arr;
        }

        @HostAccess.Export
        public long getRootId() {
            return scene.engine().sceneTree().root().nodeId();
        }

        /**
         * Upload per-frame instance transforms for a MultiMeshInstance3D node.
         * {@code data} must be a JS array of 13*N floats:
         *   [px, py, pz,  qx, qy, qz, qw,  sx, sy, sz,  cr, cg, cb]  per instance.
         */
        @HostAccess.Export
        public void setInstances(long nodeId, Object data) {
            if (nodeId <= 0L || data == null) return;
            float[] arr = SceneRuntime.toFloatArray(data);
            if (arr == null || arr.length == 0) {
                DebugLog.warn(LOG_TAG, "scene=" + scene.sceneId()
                        + " nodeId=" + selfId
                        + " stage=setInstances"
                        + " targetNodeId=" + nodeId
                        + " error=invalid instance array"
                        + " dataType=" + data.getClass().getSimpleName());
                return;
            }
            SceneRuntime.this.pendingMultiMesh.put(nodeId, arr);
            SceneRuntime.this.latestMultiMesh.put(nodeId, arr);
        }

        @HostAccess.Export
        public InputEvent input() {
            Node n = scene.engine().sceneTree().getNode(selfId);
            if (n == null) return null;
            PlayerInputState state = null;
            String ownerUuid = RuntimeScriptUtil.resolveOwnerUuid(n);
            if (ownerUuid != null) {
                state = inputsByPlayer.get(ownerUuid);
            } else if (inputsByPlayer.size() == 1) {
                state = inputsByPlayer.values().iterator().next();
            }
            return state == null ? null : new InputEvent(state);
        }

        @HostAccess.Export
        public long find(String path) {
            if (path == null || path.isBlank()) return 0L;
            Node self = scene.engine().sceneTree().getNode(selfId);
            if (self == null) return 0L;
            Node found = self.getNode(path.trim());
            return found == null ? 0L : found.nodeId();
        }

        @HostAccess.Export
        public PhysicsHit raycast(double ox, double oy, double oz,
                                  double dx, double dy, double dz, double maxDist) {
            JoltPhysicsWorld physics = scene.physics();
            if (physics == null) return null;
            return physics.raycast(ox, oy, oz, dx, dy, dz, maxDist)
                    .map(PhysicsHit::new)
                    .orElse(null);
        }

        @HostAccess.Export
        public int[] overlapSphere(double x, double y, double z, double radius) {
            JoltPhysicsWorld physics = scene.physics();
            if (physics == null) return new int[0];
            List<BodyHandle> handles = physics.overlapSphere(x, y, z, radius);
            int[] ids = new int[handles.size()];
            for (int i = 0; i < handles.size(); i++) ids[i] = handles.get(i).id();
            return ids;
        }

        @HostAccess.Export
        public void loadScene(String sceneId) {
            if (sceneId == null || sceneId.isBlank()) return;
            SceneRuntime.this.queueSceneTransition(sceneId);
        }

        @HostAccess.Export
        public void after(double seconds, Object callback) {
            SceneRuntime.this.scheduleTimer(seconds, SceneRuntime.this.toScriptCallable(callback));
        }

        @HostAccess.Export
        public void emit_signal(String signal) {
            signalBus.emit(selfId, signal, instanceValueMap());
        }

        @HostAccess.Export
        public void emit_signal(String signal, Object arg1) {
            signalBus.emit(selfId, signal, instanceValueMap(), arg1);
        }

        @HostAccess.Export
        public void emit_signal(String signal, Object arg1, Object arg2) {
            signalBus.emit(selfId, signal, instanceValueMap(), arg1, arg2);
        }

        @HostAccess.Export
        public void emit_signal(String signal, Object arg1, Object arg2, Object arg3) {
            signalBus.emit(selfId, signal, instanceValueMap(), arg1, arg2, arg3);
        }

        @HostAccess.Export
        public void connect(long sourceId, String signal, long targetId, String method) {
            signalBus.connect(sourceId, signal, targetId, method);
        }

        @HostAccess.Export
        public void disconnect(long sourceId, String signal, long targetId, String method) {
            signalBus.disconnect(sourceId, signal, targetId, method);
        }

        @HostAccess.Export
        public ScriptInputApi getInput() {
            NodeInstance inst = instances.get(selfId);
            return inst == null ? null : inst.inputApi;
        }

        @HostAccess.Export
        public double playerX() { return playerCoord(0); }

        @HostAccess.Export
        public double playerY() { return playerCoord(1); }

        @HostAccess.Export
        public double playerZ() { return playerCoord(2); }

        @HostAccess.Export
        public double playerYaw() { return playerCoord(3); }

        @HostAccess.Export
        public boolean teleportPlayer(String playerUuid, double x, double y, double z) {
            return teleportPlayer(playerUuid, x, y, z, Double.NaN, Double.NaN);
        }

        @HostAccess.Export
        public boolean teleportPlayer(String playerUuid, double x, double y, double z,
                                      double yawDeg, double pitchDeg) {
            if (playerUuid == null || playerUuid.isBlank()) return false;
            UUID uuid;
            try {
                uuid = UUID.fromString(playerUuid.trim());
            } catch (Exception ignored) { return false; }

            for (Player p : scene.instance().getPlayers()) {
                if (p == null || !uuid.equals(p.getUuid())) continue;
                Pos cur = p.getPosition();
                float yaw = Double.isFinite(yawDeg) ? (float) yawDeg : cur.yaw();
                float pitch = Double.isFinite(pitchDeg) ? (float) pitchDeg : cur.pitch();
                p.teleport(new Pos(x, y, z, yaw, pitch));
                return true;
            }
            return false;
        }

        @HostAccess.Export
        public void playerSetVelocity(String playerUuid, double vx, double vy, double vz) {
            Player p = findPlayer(playerUuid);
            if (p == null) return;
            playerMessageSink.send(p.getUuid(), Lane.EVENTS,
                    PlayerMotion.velocity((float) vx, (float) vy, (float) vz));
        }

        @HostAccess.Export
        public void playerAddVelocity(String playerUuid, double vx, double vy, double vz) {
            Player p = findPlayer(playerUuid);
            if (p == null) return;
            playerMessageSink.send(p.getUuid(), Lane.EVENTS,
                    PlayerMotion.velocity((float) vx, (float) vy, (float) vz));
        }

        @HostAccess.Export
        public double[] playerGetVelocity(String playerUuid) {
            if (playerUuid == null || playerUuid.isBlank()) return new double[]{0, 0, 0};
            float[] v = playerVelocities.get(playerUuid.trim());
            if (v == null || v.length < 3) return new double[]{0, 0, 0};
            return new double[]{v[0], v[1], v[2]};
        }

        private Player findPlayer(String playerUuid) {
            if (playerUuid == null || playerUuid.isBlank()) return null;
            UUID uuid;
            try {
                uuid = UUID.fromString(playerUuid.trim());
            } catch (Exception ignored) { return null; }
            for (Player p : scene.instance().getPlayers()) {
                if (p != null && uuid.equals(p.getUuid())) return p;
            }
            return null;
        }

        @HostAccess.Export
        public void setActiveCamera(long nodeId) {
            String uuid = resolveOwnerUuidOrSinglePlayer();
            if (uuid == null) return;
            followCameraByPlayer.remove(uuid);
            scriptCameraByPlayer.remove(uuid);
            if (nodeId <= 0L) {
                activeCameraByPlayer.remove(uuid);
            } else {
                activeCameraByPlayer.put(uuid, new OwnedValue<>(selfId, nodeId));
            }
        }

        @HostAccess.Export
        public void setFollowCamera(double localX, double localY, double localZ,
                                    double pitchDeg, double rollDeg) {
            String uuid = resolveOwnerUuidOrSinglePlayer();
            if (uuid == null) return;
            activeCameraByPlayer.remove(uuid);
            scriptCameraByPlayer.remove(uuid);
            followCameraByPlayer.put(uuid, new OwnedValue<>(selfId, new float[]{
                    (float) localX, (float) localY, (float) localZ,
                    (float) pitchDeg, (float) rollDeg
            }));
        }

        @HostAccess.Export
        public void setScriptCamera(double x, double y, double z,
                                    double yawDeg, double pitchDeg, double rollDeg) {
            String uuid = resolveOwnerUuidOrSinglePlayer();
            if (uuid == null) return;
            activeCameraByPlayer.remove(uuid);
            followCameraByPlayer.remove(uuid);
            scriptCameraByPlayer.put(uuid, new OwnedValue<>(selfId, new float[]{
                    (float) x, (float) y, (float) z,
                    (float) yawDeg, (float) pitchDeg, (float) rollDeg
            }));
        }

        @HostAccess.Export
        public void resetCamera() {
            String uuid = resolveOwnerUuidOrSinglePlayer();
            if (uuid == null) return;
            activeCameraByPlayer.remove(uuid);
            followCameraByPlayer.remove(uuid);
            scriptCameraByPlayer.remove(uuid);
        }

        @HostAccess.Export
        public void setCursorMode(boolean enabled) {
            String uuid = resolveOwnerUuidOrSinglePlayer();
            if (uuid == null) return;
            boolean osVisible = isOsCursorVisibleFor(uuid);
            setCursorState(uuid, enabled, osVisible);
        }

        @HostAccess.Export
        public boolean isCursorModeEnabled() {
            String uuid = resolveOwnerUuidOrSinglePlayer();
            return uuid != null && isCursorModeEnabledFor(uuid);
        }

        @HostAccess.Export
        public void setOsCursorVisible(boolean visible) {
            String uuid = resolveOwnerUuidOrSinglePlayer();
            if (uuid == null) return;
            boolean enabled = isCursorModeEnabledFor(uuid);
            setCursorState(uuid, enabled, visible);
        }

        @HostAccess.Export
        public boolean isOsCursorVisible() {
            String uuid = resolveOwnerUuidOrSinglePlayer();
            return uuid != null && isOsCursorVisibleFor(uuid);
        }

        @HostAccess.Export
        public double[] getCursorPosition() {
            String uuid = resolveOwnerUuidOrSinglePlayer();
            if (uuid == null) return new double[]{0.0, 0.0};
            PlayerInputState input = inputsByPlayer.get(uuid);
            if (input == null) return new double[]{0.0, 0.0};
            return new double[]{input.cursorX(), input.cursorY()};
        }

        @HostAccess.Export
        public CameraApi camera() { return new CameraApi(); }

        @HostAccess.Export
        public CursorApi cursor() { return new CursorApi(); }

        public final class CameraApi {
            @HostAccess.Export
            public void follow(double localX, double localY, double localZ,
                               double pitchDeg, double rollDeg) {
                setFollowCamera(localX, localY, localZ, pitchDeg, rollDeg);
            }

            @HostAccess.Export
            public void scene(long cameraNodeId) { setActiveCamera(cameraNodeId); }

            @HostAccess.Export
            public void scriptable(double x, double y, double z,
                                   double yawDeg, double pitchDeg, double rollDeg) {
                setScriptCamera(x, y, z, yawDeg, pitchDeg, rollDeg);
            }

            @HostAccess.Export
            public void reset() { resetCamera(); }
        }

        public final class CursorApi {
            @HostAccess.Export
            public void enable() { setCursorMode(true); }

            @HostAccess.Export
            public void disable() { setCursorMode(false); }

            @HostAccess.Export
            public void setVisible(boolean visible) { setOsCursorVisible(visible); }

            @HostAccess.Export
            public boolean enabled() { return isCursorModeEnabled(); }

            @HostAccess.Export
            public boolean visible() { return isOsCursorVisible(); }

            @HostAccess.Export
            public double[] position() { return getCursorPosition(); }
        }

        @HostAccess.Export
        public void setSceneCurrentCamera(long cameraNodeId) {
            if (cameraNodeId <= 0L) {
                clearAllSceneCurrentCameras();
                return;
            }
            Node chosen = scene.engine().sceneTree().getNode(cameraNodeId);
            if (chosen == null || !"Camera3D".equals(scene.engine().nodeTypes().typeIdFor(chosen))) return;

            ArrayList<Node> stack = new ArrayList<>();
            stack.add(scene.engine().sceneTree().root());
            while (!stack.isEmpty()) {
                Node node = stack.remove(stack.size() - 1);
                if (node == null) continue;
                if ("Camera3D".equals(scene.engine().nodeTypes().typeIdFor(node))) {
                    if (node.nodeId() == cameraNodeId) {
                        SceneRuntime.this.queueSet(node.nodeId(), "current", "true");
                    } else {
                        SceneRuntime.this.queueRemove(node.nodeId(), "current");
                    }
                }
                List<Node> children = node.children();
                for (int i = children.size() - 1; i >= 0; i--) stack.add(children.get(i));
            }
        }

        @HostAccess.Export
        public void clearAllSceneCurrentCameras() {
            ArrayList<Node> stack = new ArrayList<>();
            stack.add(scene.engine().sceneTree().root());
            while (!stack.isEmpty()) {
                Node node = stack.remove(stack.size() - 1);
                if (node == null) continue;
                if ("Camera3D".equals(scene.engine().nodeTypes().typeIdFor(node))) {
                    SceneRuntime.this.queueRemove(node.nodeId(), "current");
                }
                List<Node> children = node.children();
                for (int i = children.size() - 1; i >= 0; i--) stack.add(children.get(i));
            }
        }

        @HostAccess.Export
        public CollisionEvent[] getCollisionEvents() {
            JoltPhysicsWorld physics = scene.physics();
            if (physics == null) return new CollisionEvent[0];
            List<CollisionEvent> events = physics.consumeCollisionEvents();
            return events.toArray(new CollisionEvent[0]);
        }

        @HostAccess.Export
        public double[] getBodyVelocity(long nodeId) {
            JoltPhysicsWorld physics = scene.physics();
            if (physics == null) return new double[]{0, 0, 0};
            float[] v = physics.getLinearVelocity(nodeId);
            return new double[]{v[0], v[1], v[2]};
        }

        @HostAccess.Export
        public void applyForce(long nodeId, double fx, double fy, double fz) {
            JoltPhysicsWorld physics = scene.physics();
            if (physics == null) return;
            physics.applyForce(nodeId, (float) fx, (float) fy, (float) fz);
        }

        @HostAccess.Export
        public void applyImpulse(long nodeId, double fx, double fy, double fz) {
            JoltPhysicsWorld physics = scene.physics();
            if (physics == null) return;
            physics.applyImpulse(nodeId, (float) fx, (float) fy, (float) fz);
        }

        @HostAccess.Export
        public void setLinearVelocity(long nodeId, double vx, double vy, double vz) {
            JoltPhysicsWorld physics = scene.physics();
            if (physics == null) return;
            physics.setLinearVelocity(nodeId, (float) vx, (float) vy, (float) vz);
        }

        @HostAccess.Export
        public long[] getChildren(long nodeId) {
            Node n = scene.engine().sceneTree().getNode(nodeId);
            if (n == null) return new long[0];
            List<Node> children = n.children();
            long[] ids = new long[children.size()];
            for (int i = 0; i < children.size(); i++) ids[i] = children.get(i).nodeId();
            return ids;
        }

        @HostAccess.Export
        public boolean exists(long nodeId) {
            return nodeId > 0L && scene.engine().sceneTree().getNode(nodeId) != null;
        }

        @HostAccess.Export
        public PlayerInfo[] getPlayers() {
            String[] uuids = inputsByPlayer.keySet().toArray(new String[0]);
            PlayerInfo[] result = new PlayerInfo[uuids.length];
            for (int i = 0; i < uuids.length; i++) {
                String uuid = uuids[i];
                result[i] = new PlayerInfo(uuid, playerNames.get(uuid), playerPositions.get(uuid));
            }
            return result;
        }

        @HostAccess.Export
        public void tween(long nodeId, String prop, double targetValue, double duration) {
            if (nodeId <= 0L || prop == null || prop.isBlank()) return;
            if (duration <= 0.0) {
                SceneRuntime.this.queueSet(nodeId, prop, RuntimeScriptUtil.trimFloat((float) targetValue));
                return;
            }
            float from = (float) getNumber(nodeId, prop, targetValue);
            pendingTweens.removeIf(t -> t.nodeId == nodeId && prop.equals(t.prop));
            pendingTweens.add(new PendingTween(nodeId, prop, from, (float) targetValue, duration));
        }

        @HostAccess.Export
        public long instantiate(String scenePath, long parentId) {
            return SceneRuntime.this.instantiateScene(scene, scenePath, parentId);
        }

        private double playerCoord(int idx) {
            String uuid = resolveOwnerUuid();
            if (uuid == null && inputsByPlayer.size() == 1) uuid = inputsByPlayer.keys().nextElement();
            if (uuid == null) return 0.0;
            float[] pos = playerPositions.get(uuid);
            return pos != null && idx < pos.length ? pos[idx] : 0.0;
        }

        private String resolveOwnerUuid() {
            Node n = scene.engine().sceneTree().getNode(selfId);
            return n == null ? null : RuntimeScriptUtil.resolveOwnerUuid(n);
        }

        private String resolveOwnerUuidOrSinglePlayer() {
            String uuid = resolveOwnerUuid();
            if (uuid != null) return uuid;
            if (inputsByPlayer.size() == 1) {
                PlayerInputState st = inputsByPlayer.values().iterator().next();
                return st == null ? null : st.playerUuid();
            }
            return null;
        }

        private void setCursorState(String playerUuid, boolean enabled, boolean osVisible) {
            cursorStateByPlayer.put(playerUuid, new OwnedValue<>(selfId, new boolean[]{enabled, osVisible}));
            sendCursorState(playerUuid, enabled, osVisible);
        }

        private boolean isCursorModeEnabledFor(String playerUuid) {
            OwnedValue<boolean[]> ov = cursorStateByPlayer.get(playerUuid);
            boolean[] state = ov == null ? null : ov.value();
            return state != null && state.length > 0 && state[0];
        }

        private boolean isOsCursorVisibleFor(String playerUuid) {
            OwnedValue<boolean[]> ov = cursorStateByPlayer.get(playerUuid);
            boolean[] state = ov == null ? null : ov.value();
            return state == null || state.length < 2 || state[1];
        }

        private void sendCursorState(String playerUuid, boolean enabled, boolean osVisible) {
            if (playerUuid == null || playerUuid.isBlank()) return;
            try {
                playerMessageSink.send(UUID.fromString(playerUuid), Lane.EVENTS, new CursorState(enabled, osVisible));
            } catch (Exception ignored) {
            }
        }
    }
}
