package com.moud.server.minestom.scripting;


import com.moud.net.protocol.MultiMeshData;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.ScriptActionInvoke;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListRequest;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.core.scene.Node;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.net.PlayerMessageSink;
import org.graalvm.polyglot.Engine;
import com.moud.server.minestom.project.ProjectService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ScriptService {
    private final ScriptLanguageRegistry languages;
    private final ToolScriptService jsTools;
    private final LuauToolScriptService luauTools;
    private final RuntimeScriptService runtime;

    public ScriptService(ProjectService project) {
        this(project, PlayerMessageSink.NOOP);
    }

    public ScriptService(ProjectService project, PlayerMessageSink playerMessageSink) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(playerMessageSink, "playerMessageSink");
        this.languages = new ScriptLanguageRegistry();
        Engine toolsEngine = Engine.create();
        Engine runtimeEngine = Engine.create();
        this.jsTools = new ToolScriptService(project, toolsEngine);
        this.luauTools = languages.supportFor(ScriptLanguage.LUAU).available() ? new LuauToolScriptService(project) : null;
        this.runtime = new RuntimeScriptService(project, runtimeEngine, languages, playerMessageSink);
    }

    /** @return a pending scene-transition ID, or {@code null} if none was requested. */
    public String tickRuntime(ServerScene scene, double dtSeconds) {
        return runtime.tick(scene, dtSeconds);
    }

    public void updatePlayerPositions(Map<UUID, float[]> positions, double dtSeconds) {
        runtime.updatePlayerPositions(positions, dtSeconds);
    }

    public void updatePlayerNames(Map<UUID, String> names) {
        runtime.updatePlayerNames(names);
    }

    public Long getActiveCameraForPlayer(String sceneId, UUID uuid) {
        if (sceneId == null || uuid == null) return null;
        return runtime.getActiveCameraForPlayer(sceneId, uuid.toString());
    }

    public float[] getFollowCameraForPlayer(String sceneId, UUID uuid) {
        if (sceneId == null || uuid == null) return null;
        return runtime.getFollowCameraForPlayer(sceneId, uuid.toString());
    }

    public float[] getScriptCameraForPlayer(String sceneId, UUID uuid) {
        if (sceneId == null || uuid == null) return null;
        return runtime.getScriptCameraForPlayer(sceneId, uuid.toString());
    }

    public void onPlayerInput(UUID uuid, PlayerInput input) {
        runtime.onPlayerInput(uuid, input);
    }

    public void onUiEvent(ServerScene scene, long nodeId, String event, float value) {
        runtime.onUiEvent(scene, nodeId, event, value);
    }

    public List<MultiMeshData> getLatestMultiMesh(String sceneId) {
        return runtime.getLatestMultiMesh(sceneId);
    }

    public List<MultiMeshData> drainMultiMesh(String sceneId) {
        return runtime.drainMultiMesh(sceneId);
    }

    public void refreshEditorRuntime(ServerScene scene) {
        runtime.refreshEditor(scene);
    }

    public void onSceneDeleted(String sceneId) {
        runtime.onSceneDeleted(sceneId);
    }

    public ScriptActionListResponse onListActions(ServerScene scene, ScriptActionListRequest request) {
        ScriptActionListResponse unsupported = unsupportedListResponse(scene, request);
        if (unsupported != null) {
            return unsupported;
        }
        ScriptReference script = scriptForNode(scene, request == null ? 0L : request.nodeId());
        if (script != null && script.language() == ScriptLanguage.LUAU && luauTools != null) {
            return luauTools.onListActions(scene, request);
        }
        return jsTools.onListActions(scene, request);
    }

    public ScriptActionInvokeAck onInvokeAction(ServerScene scene, ScriptActionInvoke request) {
        ScriptActionInvokeAck unsupported = unsupportedInvokeAck(scene, request);
        if (unsupported != null) {
            return unsupported;
        }
        ScriptReference script = scriptForNode(scene, request == null ? 0L : request.nodeId());
        if (script != null && script.language() == ScriptLanguage.LUAU && luauTools != null) {
            return luauTools.onInvokeAction(scene, request);
        }
        return jsTools.onInvokeAction(scene, request);
    }

    private ScriptActionListResponse unsupportedListResponse(ServerScene scene, ScriptActionListRequest request) {
        if (scene == null || request == null) {
            return null;
        }
        ScriptReference script = scriptForNode(scene, request.nodeId());
        if (script == null) {
            return null;
        }
        ScriptLanguageSupport support = languages.supportFor(script.language());
        if (support.available()) {
            return null;
        }
        return new ScriptActionListResponse(
                request.requestId(),
                request.nodeId(),
                false,
                support.messageForPath(script.path()),
                List.of()
        );
    }

    private ScriptActionInvokeAck unsupportedInvokeAck(ServerScene scene, ScriptActionInvoke request) {
        if (scene == null || request == null) {
            return null;
        }
        ScriptReference script = scriptForNode(scene, request.nodeId());
        if (script == null) {
            return null;
        }
        ScriptLanguageSupport support = languages.supportFor(script.language());
        if (support.available()) {
            return null;
        }
        return new ScriptActionInvokeAck(
                request.requestId(),
                request.nodeId(),
                false,
                support.messageForPath(script.path())
        );
    }

    private static ScriptReference scriptForNode(ServerScene scene, long nodeId) {
        if (scene == null || nodeId <= 0L) {
            return null;
        }
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null) {
            return null;
        }
        return ScriptPaths.parseScript(node.getProperty("script"));
    }
}
