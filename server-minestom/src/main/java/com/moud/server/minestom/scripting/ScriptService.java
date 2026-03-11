package com.moud.server.minestom.scripting;


import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.ScriptActionInvoke;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListRequest;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.server.minestom.engine.ServerScene;
import org.graalvm.polyglot.Engine;
import com.moud.server.minestom.project.ProjectService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ScriptService {
    private final ToolScriptService tools;
    private final RuntimeScriptService runtime;

    public ScriptService(ProjectService project) {
        Objects.requireNonNull(project, "project");
        Engine toolsEngine = Engine.create();
        Engine runtimeEngine = Engine.create();
        this.tools = new ToolScriptService(project, toolsEngine);
        this.runtime = new RuntimeScriptService(project, runtimeEngine);
    }

    /** @return a pending scene-transition ID, or {@code null} if none was requested. */
    public String tickRuntime(ServerScene scene, double dtSeconds) {
        return runtime.tick(scene, dtSeconds);
    }

    public void updatePlayerPositions(Map<UUID, float[]> positions) {
        runtime.updatePlayerPositions(positions);
    }

    public Long getActiveCameraForPlayer(String sceneId, UUID uuid) {
        if (sceneId == null || uuid == null) return null;
        return runtime.getActiveCameraForPlayer(sceneId, uuid.toString());
    }

    public float[] getFollowCameraForPlayer(String sceneId, UUID uuid) {
        if (sceneId == null || uuid == null) return null;
        return runtime.getFollowCameraForPlayer(sceneId, uuid.toString());
    }

    public void onPlayerInput(UUID uuid, PlayerInput input) {
        runtime.onPlayerInput(uuid, input);
    }

    public void onSceneDeleted(String sceneId) {
        runtime.onSceneDeleted(sceneId);
    }

    public ScriptActionListResponse onListActions(ServerScene scene, ScriptActionListRequest request) {
        return tools.onListActions(scene, request);
    }

    public ScriptActionInvokeAck onInvokeAction(ServerScene scene, ScriptActionInvoke request) {
        return tools.onInvokeAction(scene, request);
    }
}
