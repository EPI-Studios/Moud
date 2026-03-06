package com.moud.core.physics;

/** Result of a successful raycast query against a {@link PhysicsWorld}. */
public record RaycastResult(
        double hitX, double hitY, double hitZ,
        double normalX, double normalY, double normalZ,
        double distance,
        BodyHandle body
) {}
