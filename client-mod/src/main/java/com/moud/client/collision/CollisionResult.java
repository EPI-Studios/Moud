package com.moud.client.collision;

import net.minecraft.util.math.Vec3d;

public class CollisionResult {
    public final Vec3d allowedMovement;
    public final boolean horizontalCollision;
    public final boolean verticalCollision;
    public final Vec3d groundNormal;
    public final double groundDistance;

    public CollisionResult(Vec3d allowedMovement, boolean horizontalCollision, boolean verticalCollision, Vec3d groundNormal, double groundDistance) {
        this.allowedMovement = allowedMovement;
        this.horizontalCollision = horizontalCollision;
        this.verticalCollision = verticalCollision;
        this.groundNormal = groundNormal;
        this.groundDistance = groundDistance;
    }

    public static CollisionResult none(Vec3d movement) {
        return new CollisionResult(movement, false, false, Vec3d.ZERO, Double.POSITIVE_INFINITY);
    }
}
