package com.moud.api.physics.player;

public final class DefaultPlayerPhysicsController implements PlayerPhysicsController {
    @Override
    public PlayerState step(PlayerState current, PlayerInput input, PlayerPhysicsConfig config, CollisionWorld world, float dt) {
        return PlayerController.step(current, input, config, world, dt);
    }
}

