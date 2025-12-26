package com.moud.api.physics.player;

import com.moud.api.collision.AABB;

import java.util.List;

public final class PlayerController {
    private static final double EPSILON = 1.0e-9;
    private static final float MAX_PITCH = 90.0f;

    private PlayerController() {
    }

    public static PlayerState step(
            PlayerState current,
            PlayerInput input,
            PlayerPhysicsConfig config,
            CollisionWorld world,
            float dt
    ) {
        if (current == null) {
            current = PlayerState.at(0.0, 0.0, 0.0);
        }
        if (input == null) {
            input = new PlayerInput(0L, false, false, false, false, false, false, false, 0.0f, 0.0f);
        }
        if (config == null) {
            config = PlayerPhysicsConfig.defaults();
        }
        float clampedDt = Math.max(0.0f, Math.min(0.25f, dt));
        if (clampedDt <= 0.0f) {
            return current;
        }

        float velX = current.velX();
        float velY = current.velY();
        float velZ = current.velZ();
        boolean onGround = current.onGround();

        boolean jump = input.jump();
        boolean sprint = input.sprint();
        boolean sneak = input.sneak();

        float yaw = normalizeYaw(input.yaw());
        float pitch = clampPitch(input.pitch());

        float targetSpeed = config.speed();
        if (sprint) {
            targetSpeed *= config.sprintMultiplier();
        }
        if (sneak) {
            targetSpeed *= config.sneakMultiplier();
        }

        double[] moveDir = computeMoveDirection(input, yaw);
        float targetVelX = (float) (moveDir[0] * targetSpeed);
        float targetVelZ = (float) (moveDir[1] * targetSpeed);

        float accel = onGround ? config.accel() : config.airResistance();
        velX = moveTowards(velX, targetVelX, accel * clampedDt);
        velZ = moveTowards(velZ, targetVelZ, accel * clampedDt);

        if (onGround && !input.hasMovementInput()) {
            velX = moveTowards(velX, 0.0f, config.friction() * clampedDt);
            velZ = moveTowards(velZ, 0.0f, config.friction() * clampedDt);
        }

        if (onGround) {
            if (jump) {
                velY = config.jumpForce();
                onGround = false;
            } else if (velY < 0.0f) {
                velY = 0.0f;
            }
        } else {
            velY += config.gravity() * clampedDt;
        }

        double dx = velX * clampedDt;
        double dy = velY * clampedDt;
        double dz = velZ * clampedDt;

        AABB box = playerBox(current.x(), current.y(), current.z(), config);
        AABB broadphase = box.union(box.moved(dx, dy, dz)).expanded(0.25, 0.25, 0.25);
        List<AABB> colliders = world != null ? world.getCollisions(broadphase) : List.of();

        MoveResult normal = moveWithCollisions(box, dx, dy, dz, colliders);
        MoveResult best = normal;

        boolean shouldTryStep = config.stepHeight() > 0.0f && normal.collidedHorizontally && (onGround || current.onGround());
        if (shouldTryStep) {
            MoveResult stepped = tryStepUp(box, dx, dy, dz, colliders, config.stepHeight());
            if (stepped.horizontalDistanceSq > best.horizontalDistanceSq + 1.0e-8) {
                best = stepped;
            }
        }

        double newX = best.box.centerX();
        double newY = best.box.minY();
        double newZ = best.box.centerZ();

        if (best.hitX) {
            velX = 0.0f;
        }
        if (best.hitY) {
            velY = 0.0f;
        }
        if (best.hitZ) {
            velZ = 0.0f;
        }

        boolean grounded = best.hitDown || (current.onGround() && velY <= 0.0f && isSupported(best.box, colliders));
        boolean collidingHorizontally = best.collidedHorizontally;

        return new PlayerState(newX, newY, newZ, velX, velY, velZ, grounded, collidingHorizontally);
    }

    private static float normalizeYaw(float yaw) {
        float y = yaw;
        while (y <= -180.0f) {
            y += 360.0f;
        }
        while (y > 180.0f) {
            y -= 360.0f;
        }
        return y;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
    }

    private static double[] computeMoveDirection(PlayerInput input, float yawDegrees) {
        int forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        int strafe = (input.right() ? 1 : 0) - (input.left() ? 1 : 0);

        if (forward == 0 && strafe == 0) {
            return new double[]{0.0, 0.0};
        }

        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        double dirX = (-sin * forward) + (cos * strafe);
        double dirZ = (cos * forward) + (sin * strafe);

        double lenSq = dirX * dirX + dirZ * dirZ;
        if (lenSq > 1.0e-12) {
            double inv = 1.0 / Math.sqrt(lenSq);
            dirX *= inv;
            dirZ *= inv;
        }

        return new double[]{dirX, dirZ};
    }

    private static AABB playerBox(double x, double y, double z, PlayerPhysicsConfig config) {
        float width = config.width() > 0 ? config.width() : 0.6f;
        float height = config.height() > 0 ? config.height() : 1.8f;
        double half = width * 0.5;
        return new AABB(
                x - half,
                y,
                z - half,
                x + half,
                y + height,
                z + half
        );
    }

    private static MoveResult tryStepUp(AABB box, double dx, double dy, double dz, List<AABB> colliders, float stepHeight) {
        MoveResult up = moveWithCollisions(box, 0.0, stepHeight, 0.0, colliders);
        MoveResult horiz = moveWithCollisions(up.box, dx, 0.0, dz, colliders);
        MoveResult down = moveWithCollisions(horiz.box, 0.0, -stepHeight, 0.0, colliders);

        double movedX = down.actualDx;
        double movedZ = down.actualDz;
        double horizSq = movedX * movedX + movedZ * movedZ;

        return new MoveResult(
                down.box,
                movedX,
                down.actualDy,
                movedZ,
                down.hitX,
                down.hitY,
                down.hitZ,
                down.hitDown,
                down.collidedHorizontally,
                horizSq
        );
    }

    private static MoveResult moveWithCollisions(AABB box, double dx, double dy, double dz, List<AABB> colliders) {
        double actualDx = dx;
        double actualDy = dy;
        double actualDz = dz;

        boolean hitX = false;
        boolean hitY = false;
        boolean hitZ = false;
        boolean hitDown = false;

        AABB moved = box;

        if (dy != 0.0) {
            actualDy = clipAxisY(moved, dy, colliders);
            if (actualDy != dy) {
                hitY = true;
                if (dy < 0.0) {
                    hitDown = true;
                }
            }
            moved = moved.moved(0.0, actualDy, 0.0);
        }

        if (dx != 0.0) {
            actualDx = clipAxisX(moved, dx, colliders);
            if (actualDx != dx) {
                hitX = true;
            }
            moved = moved.moved(actualDx, 0.0, 0.0);
        }

        if (dz != 0.0) {
            actualDz = clipAxisZ(moved, dz, colliders);
            if (actualDz != dz) {
                hitZ = true;
            }
            moved = moved.moved(0.0, 0.0, actualDz);
        }

        boolean collidedHorizontally = hitX || hitZ;
        double horizSq = actualDx * actualDx + actualDz * actualDz;

        return new MoveResult(
                moved,
                actualDx,
                actualDy,
                actualDz,
                hitX,
                hitY,
                hitZ,
                hitDown,
                collidedHorizontally,
                horizSq
        );
    }

    private static boolean isSupported(AABB box, List<AABB> colliders) {
        if (box == null || colliders == null || colliders.isEmpty()) {
            return false;
        }
        AABB probe = box.moved(0.0, -0.05, 0.0);
        for (AABB c : colliders) {
            if (!probe.intersects(c)) {
                continue;
            }
            if (c.maxY() <= box.minY() + 0.051) {
                return true;
            }
        }
        return false;
    }

    private static double clipAxisX(AABB box, double dx, List<AABB> colliders) {
        if (dx == 0.0) {
            return 0.0;
        }
        double clipped = dx;
        for (AABB c : colliders) {
            if (!box.intersectsInYZ(c)) {
                continue;
            }
            if (dx > 0.0) {
                double max = c.minX() - box.maxX();
                if (max >= -EPSILON && max < clipped) {
                    clipped = max;
                }
            } else {
                double min = c.maxX() - box.minX();
                if (min <= EPSILON && min > clipped) {
                    clipped = min;
                }
            }
        }
        return clipped;
    }

    private static double clipAxisY(AABB box, double dy, List<AABB> colliders) {
        if (dy == 0.0) {
            return 0.0;
        }
        double clipped = dy;
        for (AABB c : colliders) {
            if (!box.intersectsInXZ(c)) {
                continue;
            }
            if (dy > 0.0) {
                double max = c.minY() - box.maxY();
                if (max >= -EPSILON && max < clipped) {
                    clipped = max;
                }
            } else {
                double min = c.maxY() - box.minY();
                if (min <= EPSILON && min > clipped) {
                    clipped = min;
                }
            }
        }
        return clipped;
    }

    private static double clipAxisZ(AABB box, double dz, List<AABB> colliders) {
        if (dz == 0.0) {
            return 0.0;
        }
        double clipped = dz;
        for (AABB c : colliders) {
            if (!box.intersectsInXY(c)) {
                continue;
            }
            if (dz > 0.0) {
                double max = c.minZ() - box.maxZ();
                if (max >= -EPSILON && max < clipped) {
                    clipped = max;
                }
            } else {
                double min = c.maxZ() - box.minZ();
                if (min <= EPSILON && min > clipped) {
                    clipped = min;
                }
            }
        }
        return clipped;
    }

    private static float moveTowards(float current, float target, float maxDelta) {
        if (maxDelta <= 0.0f) {
            return current;
        }
        float delta = target - current;
        if (Math.abs(delta) <= maxDelta) {
            return target;
        }
        return current + Math.copySign(maxDelta, delta);
    }

    private record MoveResult(
            AABB box,
            double actualDx,
            double actualDy,
            double actualDz,
            boolean hitX,
            boolean hitY,
            boolean hitZ,
            boolean hitDown,
            boolean collidedHorizontally,
            double horizontalDistanceSq
    ) {
    }
}
