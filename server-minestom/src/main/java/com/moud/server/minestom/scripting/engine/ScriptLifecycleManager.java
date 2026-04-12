package com.moud.server.minestom.scripting.engine;

import com.moud.core.scene.Node;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.scripting.ScriptInvocationException;
import com.moud.server.minestom.scripting.ScriptLanguage;
import com.moud.server.minestom.scripting.ScriptObject;
import com.moud.server.minestom.scripting.ScriptReference;
import com.moud.server.minestom.scripting.lang.RuntimeScriptKeys;
import com.moud.server.minestom.scripting.lang.ScriptPaths;
import com.moud.server.minestom.scripting.luau.LuauRuntimeBridge;
import com.moud.server.minestom.scripting.player.InputEvent;
import com.moud.server.minestom.scripting.player.PlayerInputState;
import com.moud.server.minestom.scripting.player.PlayerStateManager;
import com.moud.server.minestom.scripting.signal.SignalBus;
import com.moud.server.minestom.util.DebugLog;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ScriptLifecycleManager {
    private static final String LOG_TAG = "script-runtime";

    private final ProjectService project;
    private final Context ctx;
    private final ScriptLoader scriptLoader;
    private final LuauRuntimeBridge luau;
    private final PlayerStateManager playerState;
    private final SignalBus signalBus;
    private final RuntimeApiFactory apiFactory;
    private final DisableHandler disableHandler;
    private final CameraCleanup cameraCleanup;
    private final HashMap<Long, RuntimeScriptInstance> instances = new HashMap<>();
    private final ArrayList<ScriptTarget> cachedTargets = new ArrayList<>();
    private long cachedTargetsGraphRevision = Long.MIN_VALUE;

    public ScriptLifecycleManager(ProjectService project,
                           Context ctx,
                           ScriptLoader scriptLoader,
                           LuauRuntimeBridge luau,
                           PlayerStateManager playerState,
                           SignalBus signalBus,
                           RuntimeApiFactory apiFactory,
                           DisableHandler disableHandler,
                           CameraCleanup cameraCleanup) {
        this.project = Objects.requireNonNull(project, "project");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.scriptLoader = Objects.requireNonNull(scriptLoader, "scriptLoader");
        this.luau = luau;
        this.playerState = Objects.requireNonNull(playerState, "playerState");
        this.signalBus = Objects.requireNonNull(signalBus, "signalBus");
        this.apiFactory = Objects.requireNonNull(apiFactory, "apiFactory");
        this.disableHandler = Objects.requireNonNull(disableHandler, "disableHandler");
        this.cameraCleanup = Objects.requireNonNull(cameraCleanup, "cameraCleanup");
    }

    public Set<Long> tickScripts(ServerScene scene, double dtSeconds) {
        ArrayList<ScriptTarget> targets = targetsFor(scene);
        HashSet<Long> alive = new HashSet<>(targets.size());

        for (ScriptTarget target : targets) {
            if (target == null || target.nodeId() <= 0L || target.scriptPath() == null || target.language() == null) {
                continue;
            }
            long nodeId = target.nodeId();
            alive.add(nodeId);

            Node node = scene.engine().sceneTree().getNode(nodeId);
            if (node == null) {
                continue;
            }

            Path scriptFile;
            try {
                scriptFile = project.resolveProjectPath(target.scriptPath());
            } catch (Exception e) {
                disableHandler.disable(scene, nodeId, null, "resolvePath", e);
                continue;
            }

            RuntimeScriptInstance instance = instances.get(nodeId);
            long programModifiedMs = resolveProgramModifiedMs(target, scriptFile, scene, nodeId);
            if (programModifiedMs < 0L) {
                continue;
            }

            if (requiresReload(instance, target, scriptFile, programModifiedMs)) {
                instance = reloadInstance(scene, target, scriptFile, programModifiedMs, instance);
                if (instance == null) {
                    continue;
                }
            }

            if (instance.disabled) {
                continue;
            }

            if (!instance.readyCalled) {
                invokeLifecycle(scene, instance, "_ready", null);
                instance.readyCalled = true;
            }

            maybeDispatchInput(scene, node, instance);
            instance.inputApi = playerState.updateInputApi(node);

            invokeProcess(scene, instance, "_physics_process", "_physicsProcess", dtSeconds);
            invokeProcess(scene, instance, "_process", null, dtSeconds);
        }
        return alive;
    }

    public void cleanupDead(ServerScene scene) {
        cleanupDead(scene, Set.of());
    }

    public void cleanupDead(ServerScene scene, Set<Long> alive) {
        Iterator<Map.Entry<Long, RuntimeScriptInstance>> it = instances.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, RuntimeScriptInstance> entry = it.next();
            long nodeId = entry.getKey();
            if (!alive.isEmpty() && alive.contains(nodeId)) {
                continue;
            }
            RuntimeScriptInstance instance = entry.getValue();
            if (instance != null) {
                invokeLifecycle(scene, instance, "_exit_tree", "_exitTree");
            }
            cameraCleanup.clearCameraOverridesOwnedBy(nodeId);
            signalBus.removeNode(nodeId);
            if (instance != null && instance.instance != null) {
                try {
                    instance.instance.close();
                } catch (Exception ignored) {
                }
            }
            it.remove();
        }
    }

    public Map<Long, ScriptObject> instanceValueMap() {
        HashMap<Long, ScriptObject> map = new HashMap<>(instances.size());
        for (Map.Entry<Long, RuntimeScriptInstance> entry : instances.entrySet()) {
            RuntimeScriptInstance instance = entry.getValue();
            if (instance != null && !instance.disabled && instance.instance != null) {
                map.put(entry.getKey(), instance.instance);
            }
        }
        return map;
    }

    public Map<Long, RuntimeScriptInstance> instances() {
        return instances;
    }

    public RuntimeScriptInstance getInstance(long nodeId) {
        return instances.get(nodeId);
    }

    public void removeNodeState(long nodeId) {
        RuntimeScriptInstance instance = instances.remove(nodeId);
        if (instance != null && instance.instance != null) {
            try {
                instance.instance.close();
            } catch (Exception ignored) {
            }
        }
    }

    private ArrayList<ScriptTarget> targetsFor(ServerScene scene) {
        long graphRev = scene.engine().sceneRevision();
        if (cachedTargetsGraphRevision == graphRev) {
            return cachedTargets;
        }
        cachedTargetsGraphRevision = graphRev;
        cachedTargets.clear();

        ArrayList<Node> stack = new ArrayList<>();
        stack.add(scene.engine().sceneTree().root());
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            if (node == null) {
                continue;
            }

            ScriptReference script = ScriptPaths.parseScript(node.getProperty(RuntimeScriptKeys.SCRIPT_KEY));
            if (script != null && (script.language() == ScriptLanguage.JAVASCRIPT
                    || script.language() == ScriptLanguage.TYPESCRIPT
                    || (script.language() == ScriptLanguage.LUAU && luau != null))) {
                cachedTargets.add(new ScriptTarget(node.nodeId(), script.path(), script.language()));
            }

            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i));
            }
        }
        return cachedTargets;
    }

    private long resolveProgramModifiedMs(ScriptTarget target, Path scriptFile, ServerScene scene, long nodeId) {
        if (target.language() == ScriptLanguage.JAVASCRIPT || target.language() == ScriptLanguage.TYPESCRIPT) {
            ScriptLoader.Program program = scriptLoader.programFor(scriptFile, target.language());
            if (program == null) {
                disableHandler.disable(scene, nodeId, scriptFile, "loadProgram",
                        new IllegalStateException("Script load failed: " + scriptFile.toAbsolutePath()));
                return -1L;
            }
            return program.modifiedMs();
        }
        if (target.language() == ScriptLanguage.LUAU) {
            LuauRuntimeBridge.Program program = luau == null ? null : luau.programFor(scriptFile);
            if (program == null) {
                disableHandler.disable(scene, nodeId, scriptFile, "loadProgram",
                        new IllegalStateException("Script load failed: " + scriptFile.toAbsolutePath()));
                return -1L;
            }
            return program.modifiedMs();
        }
        return -1L;
    }

    private boolean requiresReload(RuntimeScriptInstance instance, ScriptTarget target, Path scriptFile, long programModifiedMs) {
        return instance == null
                || instance.language != target.language()
                || !scriptFile.equals(instance.scriptFile)
                || instance.programModifiedMs != programModifiedMs;
    }

    private RuntimeScriptInstance reloadInstance(ServerScene scene,
                                                 ScriptTarget target,
                                                 Path scriptFile,
                                                 long programModifiedMs,
                                                 RuntimeScriptInstance existing) {
        if (existing != null) {
            invokeLifecycle(scene, existing, "_exit_tree", "_exitTree");
            cameraCleanup.clearCameraOverridesOwnedBy(existing.nodeId);
            try {
                existing.instance.close();
            } catch (Exception ignored) {
            }
        }
        try {
            Object api = apiFactory.create(scene, target.nodeId());
            ScriptObject scriptInstance;
            if (target.language() == ScriptLanguage.JAVASCRIPT || target.language() == ScriptLanguage.TYPESCRIPT) {
                ScriptLoader.Program program = scriptLoader.programFor(scriptFile, target.language());
                Value jsInstance = program == null ? null : scriptLoader.createNodeInstance(program.exports());
                scriptInstance = jsInstance == null ? null : JsScriptAdapters.object(jsInstance);
            } else {
                LuauRuntimeBridge.Program program = luau == null ? null : luau.programFor(scriptFile);
                scriptInstance = program == null ? null : luau.createNodeInstance(program, api);
            }
            if (scriptInstance == null) {
                disableHandler.disable(scene, target.nodeId(), scriptFile, "createInstance",
                        new IllegalStateException("Script did not return an instance"));
                return null;
            }
            RuntimeScriptInstance reloaded = new RuntimeScriptInstance(target.nodeId(), scriptFile, target.language(), programModifiedMs, scriptInstance, api);
            instances.put(target.nodeId(), reloaded);
            invokeLifecycle(scene, reloaded, "_enter_tree", "_enterTree");
            reloaded.readyCalled = false;
            reloaded.disabled = false;
            return reloaded;
        } catch (Exception e) {
            disableHandler.disable(scene, target.nodeId(), scriptFile, "createInstance", e);
            return null;
        }
    }

    private void maybeDispatchInput(ServerScene scene, Node node, RuntimeScriptInstance instance) {
        if (!instance.instance.hasMethod("_input")) {
            return;
        }
        PlayerInputState state = playerState.resolveInputFor(node);
        if (state == null || state.clientTick() == instance.lastInputClientTick) {
            return;
        }
        instance.lastInputClientTick = state.clientTick();
        try {
            instance.instance.invokeMethod("_input", instance.api, new InputEvent(state));
        } catch (ScriptInvocationException e) {
            disableHandler.disable(scene, instance, "_input", e);
        }
    }

    private void invokeLifecycle(ServerScene scene, RuntimeScriptInstance instance, String primary, String fallback) {
        String member = resolveMember(instance.instance, primary, fallback);
        if (member == null) {
            return;
        }
        try {
            instance.instance.invokeMethod(member, instance.api);
        } catch (ScriptInvocationException e) {
            disableHandler.disable(scene, instance, member, e);
        }
    }

    private void invokeProcess(ServerScene scene, RuntimeScriptInstance instance, String primary, String fallback, double dtSeconds) {
        String member = resolveMember(instance.instance, primary, fallback);
        if (member == null) {
            return;
        }

        if (instance.language == ScriptLanguage.JAVASCRIPT) {
            ctx.getBindings("js").putMember("Input", instance.inputApi);
        }

        long start = System.nanoTime();
        try {
            instance.instance.invokeMethod(member, instance.api, dtSeconds);
        } catch (ScriptInvocationException e) {
            disableHandler.disable(scene, instance, member, e);
            return;
        }

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        if (instance.budget.record(elapsedMs)) {
            disableHandler.disable(scene, instance.nodeId, instance.scriptFile, member,
                    new IllegalStateException("Script disabled: %s averaged %.1fms over threshold (%dms) %d times"
                            .formatted(member, instance.budget.emaMs(), (long) instance.budget.killMs(), instance.budget.strikeLimit())));
        }
    }

    private static String resolveMember(ScriptObject object, String primary, String fallback) {
        if (object == null || primary == null) {
            return null;
        }
        if (object.hasMethod(primary)) {
            return primary;
        }
        if (fallback != null && object.hasMethod(fallback)) {
            return fallback;
        }
        return null;
    }

    public interface DisableHandler {
        void disable(ServerScene scene, RuntimeScriptInstance instance, String stage, Throwable throwable);

        void disable(ServerScene scene, long nodeId, Path scriptFile, String stage, Throwable throwable);
    }

    public interface CameraCleanup {
        void clearCameraOverridesOwnedBy(long ownerNodeId);
    }
}
