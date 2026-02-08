package com.moud.api.collision;

import com.moud.api.math.Vector3;

public record MeshCollisionResult(
        Vector3 allowedMovement,
        boolean horizontalCollision,
        boolean verticalCollision,
        Vector3 groundNormal,
        double groundDistance
) {
    public static MeshCollisionResult none(Vector3 movement) {
        return new MeshCollisionResult(movement, false, false, Vector3.zero(), Double.POSITIVE_INFINITY);
    }

    public static MeshCollisionResult none(double mx, double my, double mz) {
        return new MeshCollisionResult(new Vector3(mx, my, mz), false, false, Vector3.zero(), Double.POSITIVE_INFINITY);
    }

    public boolean hasCollision() {
        return horizontalCollision || verticalCollision;
    }
}
