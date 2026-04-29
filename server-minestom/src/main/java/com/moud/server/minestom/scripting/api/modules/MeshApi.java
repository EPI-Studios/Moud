package com.moud.server.minestom.scripting.api.modules;

import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.MeshRegistry;
import com.moud.core.mesh.source.AssetRefMesh;
import com.moud.core.mesh.source.HashRefMesh;
import com.moud.core.mesh.source.MeshSourceCodec;
import com.moud.core.mesh.source.ObjRefMesh;
import com.moud.core.scene.Node;
import com.moud.core.scripts.luau.LuauExport;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.mesh.MeshPublishService;
import com.moud.server.minestom.scripting.api.modules.mesh.ArrayMeshHandle;
import com.moud.server.minestom.scripting.api.modules.mesh.MeshBuilderHandle;
import com.moud.server.minestom.scripting.api.modules.mesh.NoiseHandle;
import com.moud.server.minestom.scripting.api.modules.mesh.SurfaceToolHandle;
import com.moud.server.minestom.scripting.runtime.RuntimeFacade;
import org.graalvm.polyglot.HostAccess;

@LuauExport(name = "MeshApi", doc = "Procedural mesh authoring and runtime mesh-source attachment.")
public final class MeshApi {
    private final ServerScene scene;
    private final MeshPublishService publisher;
    private final RuntimeFacade runtime;

    public MeshApi(ServerScene scene, MeshPublishService publisher, RuntimeFacade runtime) {
        this.scene = scene;
        this.publisher = publisher;
        this.runtime = runtime;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle surface_tool(String primitive) {
        return SurfaceToolHandle.begin(primitive, scene, publisher);
    }

    @HostAccess.Export
    @LuauExport
    public MeshBuilderHandle builder() {
        return MeshBuilderHandle.create();
    }

    @HostAccess.Export
    @LuauExport
    public NoiseHandle perlin(long seed) {
        return NoiseHandle.perlin(seed);
    }

    @HostAccess.Export
    @LuauExport
    public NoiseHandle simplex(long seed) {
        return NoiseHandle.simplex(seed);
    }

    @HostAccess.Export
    @LuauExport
    public NoiseHandle worley(long seed) {
        return NoiseHandle.worley(seed);
    }

    @HostAccess.Export
    @LuauExport
    public long spawn_mesh_child(long parentId, String name) {
        if (scene == null || runtime == null) return 0L;
        String effectiveName = (name == null || name.isBlank()) ? "ProceduralMesh" : name;
        return runtime.createRuntimeNode(scene, parentId, effectiveName, "MeshInstance3D");
    }

    @HostAccess.Export
    @LuauExport
    public void attach_inline(long nodeId, ArrayMeshHandle handle) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null || handle == null) {
            return;
        }
        ArrayMesh mesh = MeshRegistry.instance().register(handle.mesh());
        node.setProperty("mesh_source", MeshSourceCodec.encode(new HashRefMesh(mesh.hash())));
        if (publisher != null) {
            publisher.register(node);
        }
        scene.engine().bumpPhysicsRevision();
    }

    @HostAccess.Export
    @LuauExport
    public void attach_asset(long nodeId, String resPath) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null || resPath == null || resPath.isBlank()) {
            return;
        }
        var source = new AssetRefMesh(resPath);
        node.setProperty("mesh_source", MeshSourceCodec.encode(source));
        if (publisher != null) {
            publisher.register(node);
        }
        scene.engine().bumpPhysicsRevision();
    }

    @HostAccess.Export
    @LuauExport
    public void attach_obj(long nodeId, String resPath) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null || resPath == null || resPath.isBlank()) {
            return;
        }
        node.setProperty("mesh_source", MeshSourceCodec.encode(new ObjRefMesh(resPath)));
        if (publisher != null) {
            publisher.register(node);
        }
        scene.engine().bumpPhysicsRevision();
    }

    @HostAccess.Export
    @LuauExport
    public void clear_mesh_source(long nodeId) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null) {
            return;
        }
        node.setProperty("mesh_source", "");
        if (publisher != null) {
            publisher.unregister(nodeId);
        }
        scene.engine().bumpPhysicsRevision();
    }
}
