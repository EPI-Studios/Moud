package com.moud.server.minestom.scripting;

import com.moud.core.scene.Node;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpResult;
import com.moud.server.minestom.engine.Engine;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.project.ProjectService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

final class SceneRuntime {
    private static final String LOG_TAG = "script-runtime";

    private final ProjectService project;
    private final ConcurrentHashMap<String, PlayerInputState> inputsByPlayer;
    private final Context ctx;
    private final Value createInstanceFn;
    private final Map<Path, Program> programs = new HashMap<>();
    private final Map<Long, NodeInstance> instances = new HashMap<>();
    private final ArrayList<SceneOp> pendingOps = new ArrayList<>();
    private final HashMap<String, String> pendingProps = new HashMap<>();
    private long cachedTargetsGraphRevision = Long.MIN_VALUE;
    private final ArrayList<Target> cachedTargets = new ArrayList<>();
    private volatile ServerScene lastScene;

    SceneRuntime(ProjectService project, Engine engine, ConcurrentHashMap<String, PlayerInputState> inputsByPlayer) {
        this.project = Objects.requireNonNull(project, "project");
        Objects.requireNonNull(engine, "engine");
        this.inputsByPlayer = Objects.requireNonNull(inputsByPlayer, "inputsByPlayer");
        this.ctx = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowHostClassLookup(ignored -> false)
                .build();
        this.createInstanceFn = ctx.eval("js", "(proto) => Object.create(proto)");
    }

    void close() {
        try {
            ctx.close(true);
        } catch (Exception ignored) {
        }
    }

    void tick(ServerScene scene, double dtSeconds) {
        Objects.requireNonNull(scene, "scene");
        lastScene = scene;

        ArrayList<Target> targets = targetsFor(scene);
        HashSet<Long> alive = new HashSet<>(targets.size());

        for (Target target : targets) {
            if (target == null || target.nodeId <= 0L || target.scriptPath == null) {
                continue;
            }
            long nodeId = target.nodeId;
            alive.add(nodeId);

            Node node = scene.engine().sceneTree().getNode(nodeId);
            if (node == null) {
                continue;
            }

            Path scriptFile;
            try {
                scriptFile = project.resolveProjectPath(target.scriptPath);
            } catch (Exception e) {
                disableInstance(scene, nodeId, null, "resolvePath", e);
                continue;
            }

            Program program = programFor(scriptFile);
            if (program == null) {
                disableInstance(scene, nodeId, scriptFile, "loadProgram", new IllegalStateException("Script load failed: " + scriptFile.toAbsolutePath()));
                continue;
            }

            NodeInstance inst = instances.get(nodeId);
            if (inst == null || !scriptFile.equals(inst.scriptFile) || inst.programModifiedMs != program.modifiedMs) {
                if (inst != null) {
                    invokeLifecycle(scene, inst, "_exit_tree", "_exitTree");
                }
                Value jsInstance;
                try {
                    jsInstance = createNodeInstance(program.exports);
                } catch (Exception e) {
                    disableInstance(scene, nodeId, scriptFile, "createInstance", e);
                    continue;
                }
                if (jsInstance == null) {
                    disableInstance(scene, nodeId, scriptFile, "createInstance", new IllegalStateException("Script did not return an instance"));
                    continue;
                }

                inst = new NodeInstance(nodeId, scriptFile, program.modifiedMs, jsInstance, new RuntimeApi(scene, nodeId));
                instances.put(nodeId, inst);
                invokeLifecycle(scene, inst, "_enter_tree", "_enterTree");
                inst.readyCalled = false;
                inst.disabled = false;
                inst.lastInputClientTick = -1L;
            }

            if (inst.disabled) {
                continue;
            }

            if (!inst.readyCalled) {
                invokeLifecycle(scene, inst, "_ready", null);
                inst.readyCalled = true;
            }

            maybeDispatchInput(scene, node, inst);

            invokeProcess(scene, inst, "_physics_process", "_physicsProcess", dtSeconds);
            invokeProcess(scene, inst, "_process", null, dtSeconds);
        }

        cleanupDead(scene, alive);
        flush(scene);
    }

    private ArrayList<Target> targetsFor(ServerScene scene) {
        long graphRev = scene.engine().graphRevision();
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

            String scriptPath = ScriptPaths.normalizeScriptPath(node.getProperty(RuntimeScriptKeys.SCRIPT_KEY));
            if (scriptPath != null) {
                cachedTargets.add(new Target(node.nodeId(), scriptPath));
            }

            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i));
            }
        }
        return cachedTargets;
    }

    private void cleanupDead(ServerScene scene, Set<Long> alive) {
        Iterator<Map.Entry<Long, NodeInstance>> it = instances.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, NodeInstance> entry = it.next();
            long nodeId = entry.getKey();
            if (alive.contains(nodeId)) {
                continue;
            }
            NodeInstance inst = entry.getValue();
            if (inst != null) {
                invokeLifecycle(scene, inst, "_exit_tree", "_exitTree");
            }
            it.remove();
        }
    }

    private Program programFor(Path scriptFile) {
        if (scriptFile == null) {
            return null;
        }
        long modified;
        try {
            modified = Files.getLastModifiedTime(scriptFile).toMillis();
        } catch (Exception e) {
            return null;
        }
        Program cached = programs.get(scriptFile);
        if (cached != null && cached.modifiedMs == modified) {
            return cached;
        }
        Program loaded = loadProgram(scriptFile, modified);
        if (loaded != null) {
            programs.put(scriptFile, loaded);
        }
        return loaded;
    }

    private Program loadProgram(Path scriptFile, long modifiedMs) {
        try {
            if (!Files.isRegularFile(scriptFile)) {
                return null;
            }
            String code = Files.readString(scriptFile, StandardCharsets.UTF_8);
            Source source = Source.newBuilder("js", code, scriptFile.toString()).build();
            Value exports = ctx.eval(source);
            if (exports == null) {
                return null;
            }
            return new Program(scriptFile, modifiedMs, exports);
        } catch (PolyglotException e) {
            DebugLog.error(LOG_TAG, "load failed: " + scriptFile + ": " + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            DebugLog.error(LOG_TAG, "load failed: " + scriptFile + ": " + e.getMessage(), e);
            return null;
        }
    }

    private Value createNodeInstance(Value exports) {
        if (exports == null) {
            return null;
        }
        try {
            if (exports.canInstantiate()) {
                return exports.newInstance();
            }
            if (exports.canExecute()) {
                Value v = exports.execute();
                if (v != null) {
                    return v;
                }
            }
            if (exports.hasMembers()) {
                return createInstanceFn.execute(exports);
            }
        } catch (PolyglotException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        throw new IllegalStateException("Script must evaluate to an object or a class/constructor");
    }

    private void maybeDispatchInput(ServerScene scene, Node node, NodeInstance inst) {
        if (scene == null || node == null || inst == null) {
            return;
        }
        PlayerInputState state = null;
        String ownerUuid = RuntimeScriptUtil.resolveOwnerUuid(node);
        if (ownerUuid != null) {
            state = inputsByPlayer.get(ownerUuid);
        } else if (inputsByPlayer.size() == 1) {
            state = inputsByPlayer.values().iterator().next();
        }
        if (state == null) {
            return;
        }
        if (!hasMemberCallable(inst.instance, "_input")) {
            return;
        }
        if (state.clientTick() == inst.lastInputClientTick) {
            return;
        }
        inst.lastInputClientTick = state.clientTick();
        try {
            inst.instance.invokeMember("_input", inst.api, new InputEvent(state));
        } catch (PolyglotException e) {
            disableInstance(scene, inst, "_input", e);
        }
    }

    private void invokeLifecycle(ServerScene scene, NodeInstance inst, String primary, String fallback) {
        if (scene == null || inst == null || primary == null) {
            return;
        }
        String member = resolveMember(inst.instance, primary, fallback);
        if (member == null) {
            return;
        }
        try {
            inst.instance.invokeMember(member, inst.api);
        } catch (PolyglotException e) {
            disableInstance(scene, inst, member, e);
        }
    }

    private void invokeProcess(ServerScene scene, NodeInstance inst, String primary, String fallback, double dtSeconds) {
        if (scene == null || inst == null || primary == null) {
            return;
        }
        String member = resolveMember(inst.instance, primary, fallback);
        if (member == null) {
            return;
        }
        try {
            inst.instance.invokeMember(member, inst.api, dtSeconds);
        } catch (PolyglotException e) {
            disableInstance(scene, inst, member, e);
        }
    }

    private static String resolveMember(Value obj, String primary, String fallback) {
        if (obj == null || primary == null) {
            return null;
        }
        if (hasMemberCallable(obj, primary)) {
            return primary;
        }
        if (fallback != null && hasMemberCallable(obj, fallback)) {
            return fallback;
        }
        return null;
    }

    private static boolean hasMemberCallable(Value obj, String member) {
        if (obj == null || member == null) {
            return false;
        }
        try {
            if (!obj.hasMember(member)) {
                return false;
            }
            Value fn = obj.getMember(member);
            return fn != null && fn.canExecute();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void disableInstance(ServerScene scene, NodeInstance inst, String stage, Throwable t) {
        long nodeId = inst == null ? 0L : inst.nodeId;
        Path scriptFile = inst == null ? null : inst.scriptFile;
        disableInstance(scene, nodeId, scriptFile, stage, t);
    }

    private void disableInstance(long nodeId, String message) {
        ServerScene scene = lastScene;
        disableInstance(scene, nodeId, null, "error", new IllegalStateException(message == null ? "Script error" : message));
    }

    private void disableInstance(ServerScene scene, long nodeId, Path scriptFile, String stage, Throwable t) {
        NodeInstance existing = nodeId > 0L ? instances.get(nodeId) : null;
        if (existing != null) {
            existing.disabled = true;
        }
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
        DebugLog.error(LOG_TAG, "scene=" + sceneId + " nodeId=" + nodeId + nodeInfo + " stage=" + st + " file=" + file + " error=" + msg, t);
    }

    void queueSet(long nodeId, String key, String value) {
        if (nodeId <= 0L || key == null || key.isBlank() || value == null) {
            return;
        }
        pendingOps.add(new SceneOp.SetProperty(nodeId, key, value));
        pendingProps.put(propKey(nodeId, key), value);
    }

    void queueRemove(long nodeId, String key) {
        if (nodeId <= 0L || key == null || key.isBlank()) {
            return;
        }
        pendingOps.add(new SceneOp.RemoveProperty(nodeId, key));
        pendingProps.remove(propKey(nodeId, key));
    }

    void queueRename(long nodeId, String name) {
        if (nodeId <= 0L || name == null || name.isBlank()) {
            return;
        }
        pendingOps.add(new SceneOp.Rename(nodeId, name));
    }

    void queueReparent(long nodeId, long newParentId) {
        if (nodeId <= 0L || newParentId < 0L) {
            return;
        }
        pendingOps.add(new SceneOp.Reparent(nodeId, newParentId, Integer.MAX_VALUE));
    }

    void queueFree(long nodeId) {
        if (nodeId <= 0L) {
            return;
        }
        pendingOps.add(new SceneOp.QueueFree(nodeId));
    }

    long createRuntimeNode(ServerScene scene, long parentId, String name, String typeId) {
        if (scene == null || parentId < 0L || name == null || name.isBlank() || typeId == null || typeId.isBlank()) {
            return 0L;
        }

        flush(scene);

        long batchId = SceneBatchIds.markRuntime((scene.engine().ticks() << 32) ^ System.nanoTime());
        SceneOpAck ack = scene.applier().apply(new SceneOpBatch(batchId, true, List.of(new SceneOp.CreateNode(parentId, name, typeId))));
        if (ack == null || ack.results() == null || ack.results().isEmpty()) {
            return 0L;
        }
        SceneOpResult r = ack.results().getFirst();
        if (r == null || !r.ok() || r.createdId() <= 0L) {
            return 0L;
        }

        queueSet(r.createdId(), RuntimeScriptKeys.PROP_RUNTIME, "true");
        return r.createdId();
    }

    String getPending(long nodeId, String key) {
        if (nodeId <= 0L || key == null || key.isBlank()) {
            return null;
        }
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
            if (show < n) sb.append(", …");
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
                if (msg == null || msg.isBlank()) {
                    msg = r.error() == null ? "SceneOp failed" : r.error().name();
                }
                DebugLog.error(LOG_TAG, "scene=" + scene.sceneId() + " SceneOp failed: " + msg);
            }
        }
    }

    private static String propKey(long nodeId, String key) {
        return nodeId + "\u0000" + key;
    }

    private record Target(long nodeId, String scriptPath) {
    }

    private record Program(Path file, long modifiedMs, Value exports) {
    }

    private final class NodeInstance {
        final long nodeId;
        final Path scriptFile;
        final long programModifiedMs;
        final Value instance;
        final RuntimeApi api;
        boolean readyCalled;
        boolean disabled;
        long lastInputClientTick;

        NodeInstance(long nodeId, Path scriptFile, long programModifiedMs, Value instance, RuntimeApi api) {
            this.nodeId = nodeId;
            this.scriptFile = scriptFile;
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
            System.out.println("[moud][script][" + scene.sceneId() + "][#" + selfId + "] " + msg);
        }

        @HostAccess.Export
        public long id() {
            return selfId;
        }

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
        public String get(String key) {
            return get(selfId, key);
        }

        @HostAccess.Export
        public String get(long nodeId, String key) {
            if (nodeId <= 0L || key == null || key.isBlank()) {
                return null;
            }
            String pending = SceneRuntime.this.getPending(nodeId, key);
            if (pending != null) {
                return pending;
            }
            Node n = scene.engine().sceneTree().getNode(nodeId);
            return n == null ? null : n.getProperty(key);
        }

        @HostAccess.Export
        public void set(String key, String value) {
            set(selfId, key, value);
        }

        @HostAccess.Export
        public void set(long nodeId, String key, String value) {
            SceneRuntime.this.queueSet(nodeId, key, value);
        }

        @HostAccess.Export
        public void setNumber(String key, double value) {
            float v = (float) value;
            set(key, RuntimeScriptUtil.trimFloat(v));
        }

        @HostAccess.Export
        public void setNumber(long nodeId, String key, double value) {
            float v = (float) value;
            set(nodeId, key, RuntimeScriptUtil.trimFloat(v));
        }

        @HostAccess.Export
        public double getNumber(String key, double fallback) {
            String v = get(key);
            if (v == null || v.isBlank()) {
                return fallback;
            }
            try {
                double n = Double.parseDouble(v.trim());
                return Double.isFinite(n) ? n : fallback;
            } catch (Exception ignored) {
                return fallback;
            }
        }

        @HostAccess.Export
        public double getNumber(long nodeId, String key, double fallback) {
            String v = get(nodeId, key);
            if (v == null || v.isBlank()) {
                return fallback;
            }
            try {
                double n = Double.parseDouble(v.trim());
                return Double.isFinite(n) ? n : fallback;
            } catch (Exception ignored) {
                return fallback;
            }
        }

        @HostAccess.Export
        public void remove(String key) {
            remove(selfId, key);
        }

        @HostAccess.Export
        public void remove(long nodeId, String key) {
            SceneRuntime.this.queueRemove(nodeId, key);
        }

        @HostAccess.Export
        public void rename(String name) {
            rename(selfId, name);
        }

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
        public InputEvent input() {
            Node n = scene.engine().sceneTree().getNode(selfId);
            if (n == null) {
                return null;
            }
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
            if (path == null || path.isBlank()) {
                return 0L;
            }
            Node self = scene.engine().sceneTree().getNode(selfId);
            if (self == null) {
                return 0L;
            }
            Node found = self.getNode(path.trim());
            return found == null ? 0L : found.nodeId();
        }
    }
}
