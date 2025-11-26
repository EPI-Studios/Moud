package com.moud.client.collision;

import net.minecraft.util.math.Vec3d;

public record CollisionResult(Vec3d allowedMovement, boolean horizontalCollision, boolean verticalCollision,
                              Vec3d groundNormal, double groundDistance) {

    public static CollisionResult none(Vec3d movement) {
        return new CollisionResult(movement, false, false, Vec3d.ZERO, Double.POSITIVE_INFINITY);
    }
}
