package com.moud.client.fabric.render.mesh.cache;

import java.util.List;

public record GpuMesh(
        String hash,
        List<GpuSurface> surfaces,
        float[] boundsMin,
        float[] boundsMax
) {
    public GpuMesh {
        surfaces = List.copyOf(surfaces);
    }
}
