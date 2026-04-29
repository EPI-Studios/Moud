package com.moud.core.mesh.build;

import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.Surface;
import com.moud.core.mesh.io.MeshHasher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MeshBuilder {
    private final List<Surface> surfaces = new ArrayList<>();

    public MeshBuilder addSurface(Surface surface) {
        surfaces.add(Objects.requireNonNull(surface, "surface"));
        return this;
    }

    public ArrayMesh build() {
        if (surfaces.isEmpty()) {
            throw new IllegalStateException("mesh must contain at least one surface");
        }

        float[] boundsMin = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] boundsMax = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (Surface surface : surfaces) {
            float[] positions = surface.positions();
            for (int i = 0; i + 2 < positions.length; i += 3) {
                float x = positions[i];
                float y = positions[i + 1];
                float z = positions[i + 2];
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                    throw new IllegalArgumentException("mesh contains non-finite vertex position");
                }
                boundsMin[0] = Math.min(boundsMin[0], x);
                boundsMin[1] = Math.min(boundsMin[1], y);
                boundsMin[2] = Math.min(boundsMin[2], z);
                boundsMax[0] = Math.max(boundsMax[0], x);
                boundsMax[1] = Math.max(boundsMax[1], y);
                boundsMax[2] = Math.max(boundsMax[2], z);
            }
        }

        List<Surface> built = List.copyOf(surfaces);
        return new ArrayMesh(MeshHasher.hash(built), built, boundsMin, boundsMax);
    }
}
