package com.moud.server.minestom.scripting.api.modules.mesh;

import com.moud.core.mesh.ArrayMesh;
import com.moud.core.scripts.luau.LuauExport;
import org.graalvm.polyglot.HostAccess;

@LuauExport(name = "ArrayMeshHandle", doc = "Read-only handle to a baked ArrayMesh, returned by builder.build().")
public final class ArrayMeshHandle {
    final ArrayMesh mesh;

    ArrayMeshHandle(ArrayMesh mesh) {
        this.mesh = mesh;
    }

    public ArrayMesh mesh() {
        return mesh;
    }

    @HostAccess.Export
    @LuauExport
    public String hash() {
        return mesh.hash();
    }

    @HostAccess.Export
    @LuauExport
    public int surface_count() {
        return mesh.surfaces().size();
    }

    @HostAccess.Export
    @LuauExport
    public int vertex_count() {
        return mesh.totalVertexCount();
    }

    @HostAccess.Export
    @LuauExport
    public int triangle_count() {
        return mesh.totalTriangleCount();
    }
}
