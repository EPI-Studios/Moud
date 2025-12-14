package com.moud.plugin.api.services;

import com.moud.api.math.Vector3;

public interface PhysicsController {
    boolean supported();

    void attachDynamic(long modelId, PhysicsBodyDefinition definition);

    void detach(long modelId);

    /**
     * Attach a model to follow an entity (player or model) with an offset.
     */
    void attachFollow(long modelId, String entityUuid, Vector3 offset, boolean kinematic);

    /**
     * Attach a spring constraint to an anchor point.
     */
    void attachSpring(long modelId, Vector3 anchor, double stiffness, double damping, Double restLength);

    /**
     * Clear any constraints (follow/spring) on the model.
     */
    void clearConstraints(long modelId);

    /**
     * Snapshot the current physics state of a model.
     */
    PhysicsState state(long modelId);

    record PhysicsBodyDefinition(Vector3 halfExtents, float mass, Vector3 initialVelocity, boolean allowPlayerPush) {
        public PhysicsBodyDefinition {
            Vector3 safeExtents = halfExtents != null ? halfExtents : new Vector3(0.5, 0.5, 0.5);
            Vector3 safeVelocity = initialVelocity != null ? initialVelocity : Vector3.zero();
            halfExtents = safeExtents;
            initialVelocity = safeVelocity;
            mass = mass <= 0 ? 1.0f : mass;
        }
    }

    record PhysicsState(Vector3 linearVelocity,
                        Vector3 angularVelocity,
                        boolean active,
                        boolean onGround,
                        Vector3 lastImpulse,
                        boolean hasFollowConstraint,
                        boolean hasSpringConstraint) {}
}
