package com.moud.server.minestom.scripting.api.modules.mesh;

import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.build.MeshBuilder;
import org.graalvm.polyglot.HostAccess;

public final class MeshBuilderHandle {
    private final MeshBuilder builder = new MeshBuilder();

    public static MeshBuilderHandle create() {
        return new MeshBuilderHandle();
    }

    @HostAccess.Export
    public MeshBuilderHandle add_surface(SurfaceHandle handle) {
        builder.addSurface(handle.surface);
        return this;
    }

    @HostAccess.Export
    public ArrayMeshHandle build() {
        ArrayMesh mesh = builder.build();
        return new ArrayMeshHandle(mesh);
    }
}
