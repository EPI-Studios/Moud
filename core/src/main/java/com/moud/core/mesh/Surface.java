package com.moud.core.mesh;

import java.util.Objects;

public record Surface(
        float[] positions,
        float[] normals,
        float[] uvs,
        int[] colors,
        int[] indices,
        String materialId,
        MeshPrimitive primitive
) {
    public Surface {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(normals, "normals");
        Objects.requireNonNull(uvs, "uvs");
        Objects.requireNonNull(indices, "indices");
        Objects.requireNonNull(primitive, "primitive");
        if (positions.length % 3 != 0) {
            throw new IllegalArgumentException("positions length must be divisible by 3");
        }
        int vertexCount = positions.length / 3;
        if (normals.length != vertexCount * 3) {
            throw new IllegalArgumentException("normals length must match vertex count");
        }
        if (uvs.length != vertexCount * 2) {
            throw new IllegalArgumentException("uvs length must match vertex count");
        }
        if (colors != null && colors.length != vertexCount) {
            throw new IllegalArgumentException("colors length must match vertex count");
        }
        if (indices.length % 3 != 0 && primitive == MeshPrimitive.TRIANGLES) {
            throw new IllegalArgumentException("indices length must be divisible by 3 for triangles");
        }
    }

    public int vertexCount() {
        return positions.length / 3;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    public boolean hasColors() {
        return colors != null && colors.length == vertexCount();
    }
}
