package com.moud.core.mesh;

import java.util.List;
import java.util.Objects;

public record ArrayMesh(
        String hash,
        List<Surface> surfaces,
        float[] boundsMin,
        float[] boundsMax
) {
    public ArrayMesh {
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(boundsMin, "boundsMin");
        Objects.requireNonNull(boundsMax, "boundsMax");
        if (boundsMin.length != 3 || boundsMax.length != 3) {
            throw new IllegalArgumentException("bounds must be length-3 vectors");
        }
        surfaces = List.copyOf(surfaces);
    }

    public boolean isEmpty() {
        return surfaces.isEmpty();
    }

    public int totalVertexCount() {
        int total = 0;
        for (Surface surface : surfaces) {
            total += surface.vertexCount();
        }
        return total;
    }

    public int totalTriangleCount() {
        int total = 0;
        for (Surface surface : surfaces) {
            total += surface.triangleCount();
        }
        return total;
    }
}
