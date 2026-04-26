package com.moud.server.minestom.scripting.api.modules.mesh;

import com.moud.core.mesh.Surface;
import com.moud.core.scripts.luau.LuauExport;

@LuauExport(name = "SurfaceHandle", doc = "Read-only view of a single surface inside an ArrayMesh.")
public final class SurfaceHandle {
    final Surface surface;

    SurfaceHandle(Surface surface) {
        this.surface = surface;
    }
}
