package com.moud.server.minestom.scripting.api.modules;

import com.moud.core.scene.Node;
import com.moud.core.scripts.luau.LuauExport;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.scripting.runtime.RuntimeFacade;
import org.graalvm.polyglot.HostAccess;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@LuauExport(name = "SceneApi", doc = "Scene loading, transitions, instantiation, and node-by-type queries.")
public final class SceneApi {
    private final ServerScene scene;
    private final RuntimeFacade runtime;

    public SceneApi(ServerScene scene, RuntimeFacade runtime) {
        this.scene = scene;
        this.runtime = runtime;
    }

    @HostAccess.Export
    @LuauExport
    public long[] findNodesByType(String type) {
        if (type == null || type.isBlank()) {
            return new long[0];
        }
        List<Long> result = new ArrayList<>();
        Deque<Node> queue = new ArrayDeque<>();
        queue.add(scene.engine().sceneTree().root());
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (type.equals(scene.engine().nodeTypes().typeIdFor(node))) {
                result.add(node.nodeId());
            }
            queue.addAll(node.children());
        }
        long[] ids = new long[result.size()];
        for (int i = 0; i < result.size(); i++) {
            ids[i] = result.get(i);
        }
        return ids;
    }

    @HostAccess.Export
    @LuauExport
    public long getRootId() {
        return scene.engine().sceneTree().root().nodeId();
    }

    @HostAccess.Export
    @LuauExport
    public void loadScene(String sceneId) {
        runtime.queueSceneTransition(sceneId);
    }

    @HostAccess.Export
    @LuauExport
    public long instantiate(String scenePath, long parentId) {
        return runtime.instantiateScene(scene, scenePath, parentId);
    }

    @HostAccess.Export
    @LuauExport
    public void setSceneCurrentCamera(long cameraNodeId) {
        if (cameraNodeId <= 0L) {
            clearAllSceneCurrentCameras();
            return;
        }
        Node chosen = scene.engine().sceneTree().getNode(cameraNodeId);
        if (chosen == null || !"Camera3D".equals(scene.engine().nodeTypes().typeIdFor(chosen))) {
            return;
        }

        ArrayList<Node> stack = new ArrayList<>();
        stack.add(scene.engine().sceneTree().root());
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            if (node == null) {
                continue;
            }
            if ("Camera3D".equals(scene.engine().nodeTypes().typeIdFor(node))) {
                if (node.nodeId() == cameraNodeId) {
                    runtime.mutator().queueSet(node.nodeId(), "current", "true");
                } else {
                    runtime.mutator().queueRemove(node.nodeId(), "current");
                }
            }
            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i));
            }
        }
    }

    @HostAccess.Export
    @LuauExport
    public void clearAllSceneCurrentCameras() {
        ArrayList<Node> stack = new ArrayList<>();
        stack.add(scene.engine().sceneTree().root());
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            if (node == null) {
                continue;
            }
            if ("Camera3D".equals(scene.engine().nodeTypes().typeIdFor(node))) {
                runtime.mutator().queueRemove(node.nodeId(), "current");
            }
            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i));
            }
        }
    }
}
