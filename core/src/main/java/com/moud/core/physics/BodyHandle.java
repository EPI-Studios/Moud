package com.moud.core.physics;

/** Opaque handle to a body managed by a {@link PhysicsWorld}. */
public record BodyHandle(int id) {
    public static final BodyHandle INVALID = new BodyHandle(-1);

    public boolean isValid() {
        return id >= 0;
    }
}
