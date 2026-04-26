package com.moud.server.minestom.scripting.api.modules.mesh;

import com.moud.core.mesh.ArrayMesh;
import org.graalvm.polyglot.HostAccess;

public final class ArrayMeshHandle {
    final ArrayMesh mesh;

    ArrayMeshHandle(ArrayMesh mesh) {
        this.mesh = mesh;
    }

    public ArrayMesh mesh() {
        return mesh;
    }

    @HostAccess.Export
    public String hash() {
        return mesh.hash();
    }

    @HostAccess.Export
    public int surface_count() {
        return mesh.surfaces().size();
    }

    @HostAccess.Export
    public int vertex_count() {
        return mesh.totalVertexCount();
    }

    @HostAccess.Export
    public int triangle_count() {
        return mesh.totalTriangleCount();
    }
}
