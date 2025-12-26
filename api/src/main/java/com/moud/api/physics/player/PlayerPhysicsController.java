package com.moud.api.physics.player;

public interface PlayerPhysicsController {
    PlayerState step(
            PlayerState current,
            PlayerInput input,
            PlayerPhysicsConfig config,
            CollisionWorld world,
            float dt
    );
}

