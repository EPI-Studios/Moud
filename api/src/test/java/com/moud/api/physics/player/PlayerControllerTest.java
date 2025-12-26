package com.moud.api.physics.player;

import com.moud.api.collision.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerControllerTest {

    @Test
    void clipsMovementAgainstWall() {
        CollisionWorld world = box -> List.of(
                new AABB(0.35, 0.0, -1.0, 1.0, 2.0, 1.0)
        );

        PlayerPhysicsConfig config = PlayerPhysicsConfig.defaults();
        PlayerState start = new PlayerState(0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f, true, false);
        PlayerInput input = new PlayerInput(1L, false, false, false, true, false, false, false, 0.0f, 0.0f);

        PlayerState next = PlayerController.step(start, input, config, world, 0.25f);

        assertTrue(next.x() <= 0.051, "Expected wall to clip X movement");
        assertEquals(0.0f, next.velX(), 1.0e-6f, "X velocity should be zeroed on collision");
        assertTrue(next.collidingHorizontally(), "Expected horizontal collision flag");
    }

    @Test
    void stepsUpSmallObstacle() {
        CollisionWorld world = box -> List.of(
                new AABB(0.35, 0.0, -1.0, 1.0, 0.5, 1.0)
        );

        PlayerPhysicsConfig base = PlayerPhysicsConfig.defaults();
        PlayerPhysicsConfig config = new PlayerPhysicsConfig(
                base.speed(),
                base.accel(),
                base.friction(),
                base.airResistance(),
                base.gravity(),
                base.jumpForce(),
                0.6f,
                base.width(),
                base.height(),
                base.sprintMultiplier(),
                base.sneakMultiplier()
        );

        PlayerState start = new PlayerState(0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f, true, false);
        PlayerInput input = new PlayerInput(1L, false, false, false, true, false, false, false, 0.0f, 0.0f);

        PlayerState next = PlayerController.step(start, input, config, world, 0.25f);

        assertTrue(next.y() >= 0.49, "Expected to step onto the obstacle");
        assertTrue(next.onGround(), "Expected to be grounded after stepping");
    }
}

