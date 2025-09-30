package com.moud.api.rendering.mesh;

import com.moud.api.math.Vector3;
import java.util.Objects;

/**
 * Represents a single vertex of a mesh.
 */
public final class MeshVertex {
    private final Vector3 position;
    private final Vector3 normal;
    private final float u;
    private final float v;

    MeshVertex(Vector3 position, Vector3 normal, float u, float v) {
        this.position = Objects.requireNonNull(position, "position");
        this.normal = normal != null ? normal : Vector3.up();
        this.u = u;
        this.v = v;
    }

    public Vector3 getPosition() {
        return position;
    }

    public Vector3 getNormal() {
        return normal;
    }

    public float getU() {
        return u;
    }

    public float getV() {
        return v;
    }
}
