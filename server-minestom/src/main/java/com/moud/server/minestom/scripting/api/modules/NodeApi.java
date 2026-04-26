package com.moud.server.minestom.scripting.api.modules;


import com.moud.core.scene.Node;
import com.moud.core.scripts.luau.LuauExport;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.scripting.lang.RuntimeScriptUtil;
import com.moud.server.minestom.scripting.runtime.RuntimeFacade;
import org.graalvm.polyglot.HostAccess;

@LuauExport(name = "NodeApi", doc = "Node tree creation, lookup, property access, and lifecycle ops.")
public final class NodeApi {
    private final ServerScene scene;
    private final RuntimeFacade runtime;
    private final long selfId;
    private NetApi netApi;

    public NodeApi(ServerScene scene, RuntimeFacade runtime, long selfId) {
        this.scene = scene;
        this.runtime = runtime;
        this.selfId = selfId;
    }

    @HostAccess.Export
    @LuauExport
    public NetApi net() {
        if (netApi == null && runtime.scriptMessageRouter() != null) {
            netApi = new NetApi(runtime.scriptMessageRouter(), selfId, runtime.connectedPlayerUuids());
        }
        return netApi;
    }

    @HostAccess.Export
    @LuauExport
    public long id() {
        return selfId;
    }

    @HostAccess.Export
    @LuauExport
    public String name() {
        Node node = scene.engine().sceneTree().getNode(selfId);
        return node == null || node.name() == null ? "" : node.name();
    }

    @HostAccess.Export
    @LuauExport
    public String type() {
        Node node = scene.engine().sceneTree().getNode(selfId);
        return node == null ? "" : scene.engine().nodeTypes().typeIdFor(node);
    }

    @HostAccess.Export
    @LuauExport
    public String typeOf(long nodeId) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        return node == null ? "" : scene.engine().nodeTypes().typeIdFor(node);
    }

    @HostAccess.Export
    @LuauExport
    public String get(String key) {
        return get(selfId, key);
    }

    @HostAccess.Export
    @LuauExport
    public String get(long nodeId, String key) {
        if (nodeId <= 0L || key == null || key.isBlank()) {
            return null;
        }
        String pending = runtime.getPending(nodeId, key);
        if (pending != null) {
            return pending;
        }
        Node node = scene.engine().sceneTree().getNode(nodeId);
        return node == null ? null : node.getProperty(key);
    }

    @HostAccess.Export
    @LuauExport
    public void set(String key, String value) {
        set(selfId, key, value);
    }

    @HostAccess.Export
    @LuauExport
    public void set(long nodeId, String key, String value) {
        runtime.mutator().queueSet(nodeId, key, value);
    }

    @HostAccess.Export
    @LuauExport
    public void setNumber(String key, double value) {
        set(key, RuntimeScriptUtil.trimFloat((float) value));
    }

    @HostAccess.Export
    @LuauExport
    public void setNumber(long nodeId, String key, double value) {
        set(nodeId, key, RuntimeScriptUtil.trimFloat((float) value));
    }

    @HostAccess.Export
    @LuauExport
    public double getNumber(String key, double fallback) {
        return getNumber(selfId, key, fallback);
    }

    @HostAccess.Export
    @LuauExport
    public double getNumber(long nodeId, String key, double fallback) {
        String value = get(nodeId, key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            double number = Double.parseDouble(value.trim());
            return Double.isFinite(number) ? number : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @HostAccess.Export
    @LuauExport
    public String getString(String key, String fallback) {
        return getString(selfId, key, fallback);
    }

    @HostAccess.Export
    @LuauExport
    public String getString(long nodeId, String key, String fallback) {
        String value = get(nodeId, key);
        return value != null ? value : (fallback != null ? fallback : "");
    }

    @HostAccess.Export
    @LuauExport
    public void remove(String key) {
        remove(selfId, key);
    }

    @HostAccess.Export
    @LuauExport
    public void remove(long nodeId, String key) {
        runtime.mutator().queueRemove(nodeId, key);
    }

    @HostAccess.Export
    @LuauExport
    public void rename(String name) {
        rename(selfId, name);
    }

    @HostAccess.Export
    @LuauExport
    public void rename(long nodeId, String name) {
        runtime.mutator().queueRename(nodeId, name);
    }

    @HostAccess.Export
    @LuauExport
    public void reparent(long nodeId, long newParentId) {
        runtime.mutator().queueReparent(nodeId, newParentId);
    }

    @HostAccess.Export
    @LuauExport
    public void free(long nodeId) {
        runtime.mutator().queueFree(nodeId);
    }

    @HostAccess.Export
    @LuauExport
    public long createRuntime(long parentId, String name, String typeId) {
        return runtime.createRuntimeNode(scene, parentId, name, typeId);
    }

    @HostAccess.Export
    @LuauExport
    public void flush() {
        runtime.flush(scene);
    }

    @HostAccess.Export
    @LuauExport
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

    @HostAccess.Export
    @LuauExport
    public long[] getChildren(long nodeId) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null) {
            return new long[0];
        }
        long[] ids = new long[node.children().size()];
        for (int i = 0; i < node.children().size(); i++) {
            ids[i] = node.children().get(i).nodeId();
        }
        return ids;
    }

    @HostAccess.Export
    @LuauExport
    public boolean exists(long nodeId) {
        return nodeId > 0L && scene.engine().sceneTree().getNode(nodeId) != null;
    }
}
