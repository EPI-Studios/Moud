package com.moud.plugin.api.services;

import com.moud.api.math.Vector3;

public interface PhysicsController {
    boolean supported();

    void attachDynamic(long modelId, PhysicsBodyDefinition definition);

    void detach(long modelId);

    record PhysicsBodyDefinition(Vector3 halfExtents, float mass, Vector3 initialVelocity) {
        public PhysicsBodyDefinition {
            Vector3 safeExtents = halfExtents != null ? halfExtents : new Vector3(0.5, 0.5, 0.5);
            Vector3 safeVelocity = initialVelocity != null ? initialVelocity : Vector3.zero();
            halfExtents = safeExtents;
            initialVelocity = safeVelocity;
            mass = mass <= 0 ? 1.0f : mass;
        }
    }
}
