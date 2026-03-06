package com.moud.server.minestom.scripting;

import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.ScriptActionInvoke;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListRequest;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.project.ProjectService;
import org.graalvm.polyglot.Engine;

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

    public void tickRuntime(ServerScene scene, double dtSeconds) {
        runtime.tick(scene, dtSeconds);
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
