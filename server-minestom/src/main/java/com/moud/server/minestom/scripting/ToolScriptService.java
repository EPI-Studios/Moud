package com.moud.server.minestom.scripting;


import com.moud.core.scene.Node;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpResult;
import com.moud.net.protocol.ScriptActionInvoke;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListRequest;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.server.minestom.engine.ServerScene;
import org.graalvm.polyglot.Engine;
import com.moud.server.minestom.engine.SceneBatchIds;
import com.moud.server.minestom.project.ProjectService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import com.moud.server.minestom.util.DebugLog;

final class ToolScriptService {
    private final ProjectService project;
    private final Engine engine;

    ToolScriptService(ProjectService project, Engine engine) {
        this.project = Objects.requireNonNull(project, "project");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    ScriptActionListResponse onListActions(ServerScene scene, ScriptActionListRequest request) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(request, "request");

        long nodeId = request.nodeId();
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null) {
            return new ScriptActionListResponse(request.requestId(), nodeId, false, "Node not found", List.of());
        }

        String scriptPath = ScriptPaths.normalizeScriptPath(node.getProperty("script"));
        if (scriptPath == null) {
            return new ScriptActionListResponse(request.requestId(), nodeId, true, null, List.of());
        }

        Path file;
        try {
            file = project.resolveProjectPath(scriptPath);
        } catch (Exception e) {
            return new ScriptActionListResponse(request.requestId(), nodeId, false, e.getMessage(), List.of());
        }

        try (Context ctx = createContext()) {
            ScriptExports exports = loadExports(ctx, file);
            if (!exports.tool) {
                return new ScriptActionListResponse(request.requestId(), nodeId, true, null, List.of());
            }
            if (exports.actions == null || !exports.actions.hasMembers()) {
                return new ScriptActionListResponse(request.requestId(), nodeId, true, null, List.of());
            }

            ArrayList<String> keys = new ArrayList<>(exports.actions.getMemberKeys());
            keys.removeIf(k -> k == null || k.isBlank());
            keys.sort(Comparator.naturalOrder());
            return new ScriptActionListResponse(request.requestId(), nodeId, true, null, List.copyOf(keys));
        } catch (Exception e) {
            DebugLog.error("script-tools", "listActions failed scene=" + scene.sceneId() + " nodeId=" + nodeId + " script=" + file + ": " + e.getMessage(), e);
            String msg = e.getMessage() == null ? "Script load failed" : e.getMessage();
            return new ScriptActionListResponse(request.requestId(), nodeId, false, msg, List.of());
        }
    }

    ScriptActionInvokeAck onInvokeAction(ServerScene scene, ScriptActionInvoke request) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(request, "request");

        long nodeId = request.nodeId();
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null) {
            return new ScriptActionInvokeAck(request.requestId(), nodeId, false, "Node not found");
        }

        String scriptPath = ScriptPaths.normalizeScriptPath(node.getProperty("script"));
        if (scriptPath == null) {
            return new ScriptActionInvokeAck(request.requestId(), nodeId, false, "No script attached");
        }

        Path file;
        try {
            file = project.resolveProjectPath(scriptPath);
        } catch (Exception e) {
            return new ScriptActionInvokeAck(request.requestId(), nodeId, false, e.getMessage());
        }

        String action = request.action();
        if (action == null || action.isBlank()) {
            return new ScriptActionInvokeAck(request.requestId(), nodeId, false, "Action is required");
        }

        try (Context ctx = createContext()) {
            ScriptExports exports = loadExports(ctx, file);
            if (!exports.tool) {
                return new ScriptActionInvokeAck(request.requestId(), nodeId, false, "Script is not a tool script");
            }
            if (exports.actions == null || !exports.actions.hasMembers()) {
                return new ScriptActionInvokeAck(request.requestId(), nodeId, false, "Script has no actions");
            }

            Value fn = exports.actions.getMember(action);
            if (fn == null || !fn.canExecute()) {
                return new ScriptActionInvokeAck(request.requestId(), nodeId, false, "Unknown action: " + action);
            }

            ToolApi api = new ToolApi(scene, nodeId);
            fn.execute(api);
            api.flushOps(request.requestId());
            return new ScriptActionInvokeAck(request.requestId(), nodeId, true, null);
        } catch (PolyglotException e) {
            DebugLog.error("script-tools", "invokeAction failed scene=" + scene.sceneId() + " nodeId=" + nodeId + " action=" + action + " script=" + file + ": " + e.getMessage(), e);
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            return new ScriptActionInvokeAck(request.requestId(), nodeId, false, msg);
        } catch (Exception e) {
            DebugLog.error("script-tools", "invokeAction failed scene=" + scene.sceneId() + " nodeId=" + nodeId + " action=" + action + " script=" + file + ": " + e.getMessage(), e);
            String msg = e.getMessage() == null ? "Invoke failed" : e.getMessage();
            return new ScriptActionInvokeAck(request.requestId(), nodeId, false, msg);
        }
    }

    private Context createContext() {
        return Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT).allowArrayAccess(true).build())
                .allowHostClassLookup(ignored -> false)
                .build();
    }

    private ScriptExports loadExports(Context ctx, Path file) throws Exception {
        if (ctx == null) {
            throw new IllegalArgumentException("Context missing");
        }
        if (file == null) {
            throw new IllegalArgumentException("Script path missing");
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Script not found: " + file.toAbsolutePath());
        }
        if (ScriptLanguage.fromPath(file.toString()) == ScriptLanguage.TYPESCRIPT) {
            return new ScriptExports(false, null);
        }
        String code = Files.readString(file, StandardCharsets.UTF_8);
        Source source = Source.newBuilder("js", code, file.toString()).build();
        Value exports = ctx.eval(source);
        if (exports == null || !exports.hasMembers()) {
            throw new IllegalStateException("Script must evaluate to an object (example: ({ tool: true, actions: { ... } }))");
        }
        boolean tool = exports.hasMember("tool") && safeBoolean(exports.getMember("tool"));
        Value actions = exports.hasMember("actions") ? exports.getMember("actions") : null;
        return new ScriptExports(tool, actions);
    }

    private static boolean safeBoolean(Value v) {
        try {
            return v != null && v.isBoolean() && v.asBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private record ScriptExports(boolean tool, Value actions) {
    }

    static final class ToolApi {
        private final ServerScene scene;
        private final long selectedNodeId;
        private final ArrayList<SceneOp> pendingOps = new ArrayList<>();

        ToolApi(ServerScene scene, long selectedNodeId) {
            this.scene = Objects.requireNonNull(scene, "scene");
            this.selectedNodeId = selectedNodeId;
        }

        @HostAccess.Export
        public void log(String message) {
            String msg = message == null ? "" : message;
            DebugLog.info("script-tools", "scene=" + scene.sceneId() + " nodeId=" + selectedNodeId + " " + msg);
        }

        @HostAccess.Export
        public ScriptNode node() {
            Node n = scene.engine().sceneTree().getNode(selectedNodeId);
            if (n == null) {
                return new ScriptNode(selectedNodeId, "", "");
            }
            return new ScriptNode(n.nodeId(), n.name() == null ? "" : n.name(), scene.engine().nodeTypes().typeIdFor(n));
        }

        @HostAccess.Export
        public String get(long nodeId, String key) {
            if (nodeId <= 0L || key == null || key.isBlank()) {
                return null;
            }
            Node n = scene.engine().sceneTree().getNode(nodeId);
            if (n == null) {
                return null;
            }
            return n.getProperty(key);
        }

        @HostAccess.Export
        public void set(long nodeId, String key, String value) {
            if (nodeId <= 0L || key == null || key.isBlank() || value == null) {
                return;
            }
            pendingOps.add(new SceneOp.SetProperty(nodeId, key, value));
        }

        @HostAccess.Export
        public void remove(long nodeId, String key) {
            if (nodeId <= 0L || key == null || key.isBlank()) {
                return;
            }
            pendingOps.add(new SceneOp.RemoveProperty(nodeId, key));
        }

        @HostAccess.Export
        public void rename(long nodeId, String name) {
            if (nodeId <= 0L || name == null || name.isBlank()) {
                return;
            }
            pendingOps.add(new SceneOp.Rename(nodeId, name));
        }

        @HostAccess.Export
        public void reparent(long nodeId, long newParentId) {
            if (nodeId <= 0L || newParentId < 0L) {
                return;
            }
            pendingOps.add(new SceneOp.Reparent(nodeId, newParentId, Integer.MAX_VALUE));
        }

        @HostAccess.Export
        public void free(long nodeId) {
            if (nodeId <= 0L) {
                return;
            }
            pendingOps.add(new SceneOp.QueueFree(nodeId));
        }

        @HostAccess.Export
        public long create(long parentId, String name, String typeId) {
            if (parentId < 0L || name == null || name.isBlank() || typeId == null || typeId.isBlank()) {
                return 0L;
            }
            long batchId = SceneBatchIds.clearRuntime((scene.engine().ticks() << 32) ^ System.nanoTime());
            SceneOpAck ack = scene.apply(new SceneOpBatch(batchId, true,
                    List.of(new SceneOp.CreateNode(parentId, name, typeId))));
            if (ack == null || ack.results() == null || ack.results().isEmpty()) {
                return 0L;
            }
            SceneOpResult r = ack.results().getFirst();
            return r != null && r.ok() ? r.createdId() : 0L;
        }

        void flushOps(long requestId) {
            if (pendingOps.isEmpty()) {
                return;
            }
            long batchId = SceneBatchIds.clearRuntime((scene.engine().ticks() << 32) ^ requestId ^ System.nanoTime());
            SceneOpAck ack = scene.apply(new SceneOpBatch(batchId, true, List.copyOf(pendingOps)));
            pendingOps.clear();
            if (ack == null) {
                throw new IllegalStateException("Scene apply failed");
            }
            for (SceneOpResult r : ack.results()) {
                if (r != null && !r.ok()) {
                    String msg = r.message();
                    if (msg == null || msg.isBlank()) {
                        msg = r.error() == null ? "SceneOp failed" : r.error().name();
                    }
                    throw new IllegalStateException(msg);
                }
            }
        }
    }

    static final class ScriptNode {
        @HostAccess.Export
        public final long id;
        @HostAccess.Export
        public final String name;
        @HostAccess.Export
        public final String type;

        ScriptNode(long id, String name, String type) {
            this.id = id;
            this.name = name;
            this.type = type;
        }

        @HostAccess.Export
        public long id() {
            return id;
        }

        @HostAccess.Export
        public String name() {
            return name;
        }

        @HostAccess.Export
        public String type() {
            return type;
        }
    }
}
