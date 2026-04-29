package com.moud.client.fabric.physics.rapier;

import com.moud.physics.api.Transform;
import com.moud.net.protocol.CollisionGeometrySnapshot;

public sealed interface PhysicsMutation
        permits PhysicsMutation.ApplyCollisionGeometry,
                PhysicsMutation.ClearCollisionGeometry,
                PhysicsMutation.EnsureKinematicMirror {

    record ApplyCollisionGeometry(CollisionGeometrySnapshot snapshot) implements PhysicsMutation {}

    record ClearCollisionGeometry() implements PhysicsMutation {}

    record EnsureKinematicMirror(long nodeId, Transform pose) implements PhysicsMutation {}
}
