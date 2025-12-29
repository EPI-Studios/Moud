package com.moud.api.physics.player;

public record PlayerPhysicsConfig(
        float speed,
        float accel,
        float friction,
        float airResistance,
        float gravity,
        float jumpForce,
        float stepHeight,
        float width,
        float height,
        float sprintMultiplier,
        float sneakMultiplier
) {
    public static PlayerPhysicsConfig defaults() {
        return vanilla();
    }

    public static PlayerPhysicsConfig vanilla() {
        return new PlayerPhysicsConfig(
                4.317f,
                50.0f,
                20.0f,
                8.0f,
                -32.0f,
                8.5f,
                0.6f,
                0.6f,
                1.8f,
                1.3f,
                0.3f
        );
    }

    public static PlayerPhysicsConfig predictionDefaults() {
        return new PlayerPhysicsConfig(
                4.317f,
                50.0f,
                25.0f,
                8.0f,
                -50.0f,
                10.0f,
                0.6f,
                0.6f,
                1.8f,
                1.3f,
                0.3f
        );
    }
}
