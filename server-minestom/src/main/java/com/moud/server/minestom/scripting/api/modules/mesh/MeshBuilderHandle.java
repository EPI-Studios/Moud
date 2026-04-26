package com.moud.server.minestom.scripting.api.modules.mesh;

import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.build.MeshBuilder;
import com.moud.core.scripts.luau.LuauExport;
import org.graalvm.polyglot.HostAccess;

@LuauExport(name = "MeshBuilderHandle", doc = "Builds an ArrayMesh from one or more surfaces. Call build() to finalize.")
public final class MeshBuilderHandle {
    private final MeshBuilder builder = new MeshBuilder();

    public static MeshBuilderHandle create() {
        return new MeshBuilderHandle();
    }

    @HostAccess.Export
    @LuauExport
    public MeshBuilderHandle add_surface(SurfaceHandle handle) {
        builder.addSurface(handle.surface);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public ArrayMeshHandle build() {
        ArrayMesh mesh = builder.build();
        return new ArrayMeshHandle(mesh);
    }
}
