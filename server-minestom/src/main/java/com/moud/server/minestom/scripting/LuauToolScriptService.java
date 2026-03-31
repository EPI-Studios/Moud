package com.moud.server.minestom.scripting;

import com.moud.core.scene.Node;
import com.moud.net.protocol.ScriptActionInvoke;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListRequest;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.util.DebugLog;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

final class LuauToolScriptService {
    private final ProjectService project;
    private final LuauRuntimeBridge luau;

    LuauToolScriptService(ProjectService project) {
        this.project = Objects.requireNonNull(project, "project");
        this.luau = new LuauRuntimeBridge();
    }

    void close() {
        luau.close();
    }

    ScriptActionListResponse onListActions(ServerScene scene, ScriptActionListRequest request) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(request, "request");

        long nodeId = request.nodeId();
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null) {
            return new ScriptActionListResponse(request.requestId(), nodeId, false, "Node not found", List.of());
        }

        ScriptReference script = ScriptPaths.parseScript(node.getProperty("script"));
        if (script == null) {
            return new ScriptActionListResponse(request.requestId(), nodeId, true, null, List.of());
        }

        Path file;
        try {
            file = project.resolveProjectPath(script.path());
        } catch (Exception e) {
            return new ScriptActionListResponse(request.requestId(), nodeId, false, e.getMessage(), List.of());
        }

        try (LuauRuntimeBridge.ToolExports exports = luau.loadToolExports(luau.programFor(file))) {
            if (!exports.tool()) {
                return new ScriptActionListResponse(request.requestId(), nodeId, true, null, List.of());
            }
            return new ScriptActionListResponse(request.requestId(), nodeId, true, null, exports.listActions());
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

        ScriptReference script = ScriptPaths.parseScript(node.getProperty("script"));
        if (script == null) {
            return new ScriptActionInvokeAck(request.requestId(), nodeId, false, "No script attached");
        }

        Path file;
        try {
            file = project.resolveProjectPath(script.path());
        } catch (Exception e) {
            return new ScriptActionInvokeAck(request.requestId(), nodeId, false, e.getMessage());
        }

        String action = request.action();
        if (action == null || action.isBlank()) {
            return new ScriptActionInvokeAck(request.requestId(), nodeId, false, "Action is required");
        }

        try (LuauRuntimeBridge.ToolExports exports = luau.loadToolExports(luau.programFor(file))) {
            if (!exports.tool()) {
                return new ScriptActionInvokeAck(request.requestId(), nodeId, false, "Script is not a tool script");
            }
            ToolScriptService.ToolApi api = new ToolScriptService.ToolApi(scene, nodeId);
            exports.invokeAction(action, api);
            api.flushOps(request.requestId());
            return new ScriptActionInvokeAck(request.requestId(), nodeId, true, null);
        } catch (Exception e) {
            DebugLog.error("script-tools", "invokeAction failed scene=" + scene.sceneId() + " nodeId=" + nodeId + " action=" + action + " script=" + file + ": " + e.getMessage(), e);
            String msg = e.getMessage() == null ? "Invoke failed" : e.getMessage();
            return new ScriptActionInvokeAck(request.requestId(), nodeId, false, msg);
        }
    }
}
