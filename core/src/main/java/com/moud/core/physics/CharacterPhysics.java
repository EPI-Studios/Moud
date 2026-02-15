package com.moud.core.physics;

public final class CharacterPhysics {

    public static final float GRAVITY = 30.0f;
    public static final float JUMP_VELOCITY = 10.0f;
    public static final float GROUND_ACCEL = 40.0f;
    public static final float GROUND_DECEL = 50.0f;
    public static final float AIR_ACCEL = 10.0f;
    public static final float AIR_DECEL = 5.0f;
    public static final float SPRINT_MULT = 1.5f;
    public static final float FLOOR_Y = -64.0f;

    private CharacterPhysics() {
    }

    public record State(
            float x, float y, float z,
            float velX, float velY, float velZ,
            boolean onFloor
    ) {
        public static State at(float x, float y, float z) {
            return new State(x, y, z, 0, 0, 0, true);
        }
    }

    public static State simulate(State state, float moveX, float moveZ,
                                  float yawDeg, float speed, boolean jump,
                                  boolean sprint, float dt) {
        if (dt <= 0.0f) {
            return state;
        }

        float effectiveSpeed = sprint ? speed * SPRINT_MULT : speed;

        double yawRad = Math.toRadians(yawDeg);
        float cos = (float) Math.cos(yawRad);
        float sin = (float) Math.sin(yawRad);

        float targetVelX = (-moveZ * sin + moveX * cos) * effectiveSpeed;
        float targetVelZ = (moveZ * cos + moveX * sin) * effectiveSpeed;

        boolean isMoving = Math.abs(moveX) > 1e-4f || Math.abs(moveZ) > 1e-4f;
        float accel;
        if (state.onFloor) {
            accel = isMoving ? GROUND_ACCEL : GROUND_DECEL;
        } else {
            accel = isMoving ? AIR_ACCEL : AIR_DECEL;
        }

        float velX = moveToward(state.velX, targetVelX, accel * dt);
        float velZ = moveToward(state.velZ, targetVelZ, accel * dt);

        float velY = state.velY;
        if (state.onFloor && jump) {
            velY = JUMP_VELOCITY;
        }
        velY -= GRAVITY * dt;

        float x = state.x + velX * dt;
        float y = state.y + velY * dt;
        float z = state.z + velZ * dt;

        boolean onFloor = false;
        if (y <= FLOOR_Y) {
            y = FLOOR_Y;
            if (velY < 0.0f) {
                velY = 0.0f;
            }
            onFloor = true;
        }

        return new State(x, y, z, velX, velY, velZ, onFloor);
    }

    public static float moveToward(float current, float target, float maxDelta) {
        float diff = target - current;
        if (Math.abs(diff) <= maxDelta) {
            return target;
        }
        return current + Math.signum(diff) * maxDelta;
    }
}
