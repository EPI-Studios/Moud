package com.moud.server.minestom.scripting.physics;

import com.moud.core.physics.BodyHandle;
import com.moud.core.scene.Node;
import com.moud.core.util.MathUtils;
import com.moud.core.util.ParseUtils;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.physics.JoltPhysicsWorld;
import com.moud.server.minestom.scripting.lang.RuntimeScriptUtil;
import com.moud.server.minestom.scripting.player.PlayerInputState;
import com.moud.server.minestom.scripting.player.PlayerNetworkSink;
import com.moud.server.minestom.scripting.player.PlayerStateManager;
import com.moud.server.minestom.scripting.scene.SceneMutator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CharacterBodySimulator {
    private static final double CHARACTER_TICKS_PER_SECOND = 20.0;
    private static final double CHARACTER_SIMULATION_TICKS_PER_SECOND = 60.0;
    private static final int CHARACTER_SUBSTEPS = (int) (CHARACTER_SIMULATION_TICKS_PER_SECOND / CHARACTER_TICKS_PER_SECOND);
    private static final double CHARACTER_GRAVITY_PER_SECOND = 30.0;
    private static final int MAX_SLIDES = 4;
    private static final double FLOOR_EPSILON = 1.0e-5;
    private static final double FLOOR_COS = Math.cos(Math.toRadians(46.0));
    private static final double VECTOR_EPSILON = 1.0e-8;
    private static final double INPUT_EPSILON = 1.0e-6;
    private static final double SWEEP_EPSILON = 1.0e-3;
    private static final double COLLISION_SKIN = 2.0e-4;
    private static final double JUMP_LIFTOFF = 0.05;
    private static final int JUMP_BUFFER_STEPS = CHARACTER_SUBSTEPS * 3;
    private static final int COYOTE_STEPS = CHARACTER_SUBSTEPS * 2;
    private static final double WALL_CONTACT_DURATION_SECONDS = 0.15;
    private static final double SQRT_2_OVER_2 = Math.sqrt(2.0) / 2.0;
    private static final double[] WALL_PROBE_DIRECTIONS = {
            1.0, 0.0,
            -1.0, 0.0,
            0.0, 1.0,
            0.0, -1.0,
            SQRT_2_OVER_2, SQRT_2_OVER_2,
            -SQRT_2_OVER_2, SQRT_2_OVER_2,
            SQRT_2_OVER_2, -SQRT_2_OVER_2,
            -SQRT_2_OVER_2, -SQRT_2_OVER_2
    };

    private final PlayerStateManager playerState;
    private final SceneMutator mutator;
    private final PlayerNetworkSink networkSink;
    private final Map<Long, CharacterState> characterStates = new HashMap<>();

    public CharacterBodySimulator(PlayerStateManager playerState, SceneMutator mutator, PlayerNetworkSink networkSink) {
        this.playerState = Objects.requireNonNull(playerState, "playerState");
        this.mutator = Objects.requireNonNull(mutator, "mutator");
        this.networkSink = Objects.requireNonNull(networkSink, "networkSink");
    }

    public void tick(ServerScene scene, double dtSeconds) {
        if (scene == null) {
            return;
        }
        double dt = Double.isFinite(dtSeconds) && dtSeconds > 0.0 ? dtSeconds : 1.0 / CHARACTER_TICKS_PER_SECOND;
        ArrayList<Node> stack = new ArrayList<>();
        stack.add(scene.engine().sceneTree().root());
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            if (node == null) {
                continue;
            }

            if (isCharacterBody(scene, node)) {
                boolean enabled = propBool(node, "enabled", true);
                boolean playerCtrl = propBool(node, "player_controlled", false);
                boolean scriptCtrl = propBool(node, "script_controlled", false);
                if (enabled && playerCtrl && !scriptCtrl) {
                    tickCharacterBody(scene, node, dt);
                }
            }

            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i));
            }
        }
    }

    public void cleanupNode(long nodeId) {
        characterStates.remove(nodeId);
    }

    public double[] moveAndSlide(ServerScene scene, Node node, double deltaSeconds, double vx, double vy, double vz) {
        if (scene == null || !isCharacterBody(scene, node)) {
            return new double[]{0.0, 0.0, 0.0};
        }
        double dt = Double.isFinite(deltaSeconds) && deltaSeconds > 0.0 ? deltaSeconds : 1.0 / 20.0;
        long nodeId = node.nodeId();
        double floorSnapLength = Math.max(0.0, propNumber(node, "floor_snap_length", 0.2));
        double startX = propNumber(node, "x", 0.0);
        double startY = propNumber(node, "y", 0.0);
        double startZ = propNumber(node, "z", 0.0);
        CharacterState state = stateFor(nodeId);
        boolean wasOnFloor = startY <= state.floorY + FLOOR_EPSILON && vy <= 0.0;
        CharacterMotion motion = resolveCharacterMotion(scene, node, startX, startY, startZ, vx, vy, vz, dt, floorSnapLength, wasOnFloor, false);
        double x = motion.x();
        double y = motion.y();
        double z = motion.z();
        boolean onFloor = motion.onFloor();
        state.floorY = onFloor ? y : Double.NEGATIVE_INFINITY;
        double actualVx = dt > VECTOR_EPSILON ? (x - startX) / dt : 0.0;
        double actualVy = onFloor ? 0.0 : (dt > VECTOR_EPSILON ? (y - startY) / dt : 0.0);
        double actualVz = dt > VECTOR_EPSILON ? (z - startZ) / dt : 0.0;

        mutator.queueSet(nodeId, "x", RuntimeScriptUtil.trimFloat((float) x));
        mutator.queueSet(nodeId, "y", RuntimeScriptUtil.trimFloat((float) y));
        mutator.queueSet(nodeId, "z", RuntimeScriptUtil.trimFloat((float) z));
        mutator.queueSet(nodeId, "on_wall", Boolean.toString(motion.onWall()));
        mutator.queueSet(nodeId, "on_ceiling", Boolean.toString(motion.onCeiling()));
        mutator.queueSet(nodeId, "on_floor", Boolean.toString(onFloor));
        mutator.queueSet(nodeId, "wall_normal_x", RuntimeScriptUtil.trimFloat((float) motion.wallNx()));
        mutator.queueSet(nodeId, "wall_normal_z", RuntimeScriptUtil.trimFloat((float) motion.wallNz()));
        if (onFloor) {
            mutator.queueSet(nodeId, "velocity_y", "0");
        }

        String ownerUuid = playerState.resolveOwnerUuidOrSinglePlayer(node);
        if (ownerUuid != null) {
            networkSink.sendPlayerVelocity(ownerUuid, (float) actualVx, (float) actualVy, (float) actualVz);
        }

        return new double[]{actualVx, actualVy, actualVz};
    }

    public boolean isOnFloor(Node node) {
        return node != null && "true".equals(prop(node, "on_floor"));
    }

    public boolean isOnWall(Node node) {
        return node != null && "true".equals(prop(node, "on_wall"));
    }

    public boolean isOnCeiling(Node node) {
        return node != null && "true".equals(prop(node, "on_ceiling"));
    }

    public double[] wallNormal(Node node) {
        if (node == null) {
            return new double[]{0.0, 0.0, 0.0};
        }
        double nx = propNumber(node, "wall_normal_x", 0.0);
        double nz = propNumber(node, "wall_normal_z", 0.0);
        return new double[]{nx, 0.0, nz};
    }

    public double[] inputDirection(Node node) {
        PlayerInputState input = playerState.resolveInputFor(node);
        if (input == null) {
            return new double[]{0.0, 0.0, 0.0};
        }
        double[] dir = MathUtils.yawInputToDirection(input.yawDeg(), input.moveX(), input.moveZ());
        return new double[]{dir[0], 0.0, dir[1]};
    }

    public static double[] movementVector(float yawDeg, float moveX, float moveZ) {
        return MathUtils.yawInputToDirection(yawDeg, moveX, moveZ);
    }

    private void tickCharacterBody(ServerScene scene, Node node, double dt) {
        PlayerInputState input = playerState.resolveInputFor(node);
        if (input == null) {
            return;
        }

        long nodeId = node.nodeId();
        CharacterState state = stateFor(nodeId);
        BehaviorFrame frame = new BehaviorFrame();
        CharacterConfig config = readCharacterConfig(node, frame);
        PositionState position = readPositionState(node, frame, state);

        boolean jumpDown = input.jump();
        boolean sprintDown = input.sprint();
        boolean sneakDown = input.sneak();
        double subDt = dt / CHARACTER_SUBSTEPS;
        double wallContact = state.wallContactSeconds;
        boolean onFloor = frame.onFloorPrev;
        boolean onWall = frame.onWallPrev;
        boolean onCeiling = frame.onCeilingPrev;
        double wallNormalX = position.wallNormalX;
        double wallNormalZ = position.wallNormalZ;
        frame.wallNormalX = wallNormalX;
        frame.wallNormalZ = wallNormalZ;

        if (jumpDown && !state.jumpDown) {
            state.jumpBufferSteps = JUMP_BUFFER_STEPS;
        }

        for (int step = 0; step < CHARACTER_SUBSTEPS; step++) {
            frame.jumpWasDown = step == 0 ? state.jumpDown : jumpDown;
            frame.sprintWasDown = step == 0 ? state.sprintDown : sprintDown;
            frame.sneakWasDown = step == 0 ? state.sneakDown : sneakDown;
            frame.onFloorPrev = onFloor;
            frame.onWallPrev = onWall;
            frame.onCeilingPrev = onCeiling;
            frame.floorBeforeMove = position.y <= position.floorY + FLOOR_EPSILON && frame.velocityY <= 0.0;
            frame.jumpRequested = false;

            HorizontalVelocity velocity = applyHorizontalInput(frame, input, subDt, config.groundFriction());
            double vx = velocity.vx();
            double vz = velocity.vz();
            double vy = frame.velocityY;

            onFloor = state.jumpLockSteps <= 0 && vy <= FLOOR_EPSILON
                    && (frame.onFloorPrev || state.coyoteSteps > 0 || position.y <= position.floorY + FLOOR_EPSILON);
            if (onFloor && Double.isFinite(position.floorY) && position.y <= position.floorY + Math.max(config.floorSnapLength(), JUMP_LIFTOFF)) {
                position.y = position.floorY;
                vy = 0.0;
            }

            if (state.jumpBufferSteps > 0 && !onFloor && state.jumpLockSteps <= 0) {
                double probe = Math.max(0.08, config.floorSnapLength() + JUMP_LIFTOFF);
                JoltPhysicsWorld physics = scene.physics();
                var floorProbe = physics != null
                        ? physics.sweepCharacter(node, position.x, position.y, position.z, 0.0, -probe, 0.0)
                        : java.util.Optional.<JoltPhysicsWorld.SweepHit>empty();
                if (floorProbe.isPresent() && floorProbe.get().ny() >= FLOOR_COS) {
                    double snap = probe * Math.max(0.0, floorProbe.get().fraction() - SWEEP_EPSILON);
                    position.y -= snap;
                    position.floorY = position.y;
                    state.floorY = position.floorY;
                    onFloor = true;
                    vy = 0.0;
                }
            }

            if (state.jumpBufferSteps > 0 && onFloor) {
                position.y += JUMP_LIFTOFF;
                vy = frame.jumpVelocity;
                onFloor = false;
                position.floorY = Double.NEGATIVE_INFINITY;
                state.floorY = Double.NEGATIVE_INFINITY;
                state.jumpLockSteps = CHARACTER_SUBSTEPS;
                state.jumpBufferSteps = 0;
            } else if (!onFloor) {
                vy -= CHARACTER_GRAVITY_PER_SECOND * frame.gravityScale * subDt;
            }

            double stepStartX = position.x;
            double stepStartY = position.y;
            double stepStartZ = position.z;
            CharacterMotion motion = resolveCharacterMotion(
                    scene,
                    node,
                    position.x,
                    position.y,
                    position.z,
                    vx,
                    vy,
                    vz,
                    subDt,
                    config.floorSnapLength(),
                    onFloor || frame.onFloorPrev,
                    state.jumpLockSteps > 0
            );
            position.x = motion.x();
            position.y = motion.y();
            position.z = motion.z();
            onFloor = motion.onFloor();
            onCeiling = motion.onCeiling();
            position.floorY = onFloor ? position.y : Double.NEGATIVE_INFINITY;
            state.floorY = position.floorY;
            if (onFloor) {
                state.coyoteSteps = COYOTE_STEPS;
            } else if (state.coyoteSteps > 0) {
                state.coyoteSteps--;
            }

            double actualVx = subDt > VECTOR_EPSILON ? (position.x - stepStartX) / subDt : 0.0;
            double actualVy = onFloor ? 0.0 : (subDt > VECTOR_EPSILON ? (position.y - stepStartY) / subDt : 0.0);
            double actualVz = subDt > VECTOR_EPSILON ? (position.z - stepStartZ) / subDt : 0.0;
            boolean wallProbe = detectCharacterWallContact(scene, node, position.x, position.y, position.z);
            wallContact = updateWallContact(wallContact, motion.onWall() || wallProbe, subDt);
            onWall = wallContact > INPUT_EPSILON;
            wallNormalX = motion.wallNx();
            wallNormalZ = motion.wallNz();
            frame.wallNormalX = wallNormalX;
            frame.wallNormalZ = wallNormalZ;

            frame.velocityX = actualVx;
            frame.velocityY = actualVy;
            frame.velocityZ = actualVz;
            if (state.jumpLockSteps > 0) {
                state.jumpLockSteps--;
            }
            if (state.jumpBufferSteps > 0) {
                state.jumpBufferSteps--;
            }
        }

        state.jumpDown = jumpDown;
        state.sprintDown = sprintDown;
        state.sneakDown = sneakDown;
        state.wallContactSeconds = wallContact;
        state.floorY = position.floorY;

        double actualVx = frame.velocityX;
        double actualVz = frame.velocityZ;
        double vy = frame.velocityY;

        mutator.queueSet(nodeId, "x", RuntimeScriptUtil.trimFloat((float) position.x));
        mutator.queueSet(nodeId, "y", RuntimeScriptUtil.trimFloat((float) position.y));
        mutator.queueSet(nodeId, "z", RuntimeScriptUtil.trimFloat((float) position.z));
        mutator.queueSet(nodeId, "velocity_x", RuntimeScriptUtil.trimFloat((float) actualVx));
        mutator.queueSet(nodeId, "velocity_y", RuntimeScriptUtil.trimFloat((float) vy));
        mutator.queueSet(nodeId, "velocity_z", RuntimeScriptUtil.trimFloat((float) actualVz));
        mutator.queueSet(nodeId, "on_floor", Boolean.toString(onFloor));
        mutator.queueSet(nodeId, "on_wall", Boolean.toString(onWall));
        mutator.queueSet(nodeId, "on_ceiling", Boolean.toString(onCeiling));
        mutator.queueSet(nodeId, "wall_normal_x", RuntimeScriptUtil.trimFloat((float) wallNormalX));
        mutator.queueSet(nodeId, "wall_normal_z", RuntimeScriptUtil.trimFloat((float) wallNormalZ));

        networkSink.sendPlayerPosition(input.playerUuid(), (float) position.x, (float) position.y, (float) position.z, input.yawDeg());
        networkSink.sendPlayerVelocity(input.playerUuid(), (float) actualVx, (float) vy, (float) actualVz);
    }

    private CharacterConfig readCharacterConfig(Node node, BehaviorFrame frame) {
        frame.speed = Math.max(0.0, propNumber(node, "speed", 5.0));
        frame.acceleration = Math.max(0.0, propNumber(node, "acceleration", 40.0));
        frame.deceleration = Math.max(0.0, propNumber(node, "deceleration", 30.0));
        frame.airControl = Math.max(0.0, Math.min(1.0, propNumber(node, "air_control", 0.3)));
        frame.jumpVelocity = Math.max(0.0, propNumber(node, "jump_velocity", 10.0));
        frame.gravityScale = propNumber(node, "gravity_scale", 1.0);
        double groundFriction = Math.max(0.0, propNumber(node, "ground_friction", 70.0));
        double floorSnapLength = Math.max(0.0, propNumber(node, "floor_snap_length", 0.2));
        return new CharacterConfig(groundFriction, floorSnapLength);
    }

    private PositionState readPositionState(Node node, BehaviorFrame frame, CharacterState state) {
        double x = propNumber(node, "x", 0.0);
        double y = propNumber(node, "y", 0.0);
        double z = propNumber(node, "z", 0.0);
        frame.velocityX = propNumber(node, "velocity_x", 0.0);
        frame.velocityY = propNumber(node, "velocity_y", 0.0);
        frame.velocityZ = propNumber(node, "velocity_z", 0.0);
        frame.onFloorPrev = propBool(node, "on_floor", false);
        frame.onWallPrev = propBool(node, "on_wall", false);
        frame.onCeilingPrev = propBool(node, "on_ceiling", false);
        frame.jumpWasDown = state.jumpDown;
        frame.sprintWasDown = state.sprintDown;
        frame.sneakWasDown = state.sneakDown;
        double floorY = state.floorY;
        frame.floorBeforeMove = y <= floorY + FLOOR_EPSILON && frame.velocityY <= 0.0;
        double wallNormalX = propNumber(node, "wall_normal_x", 0.0);
        double wallNormalZ = propNumber(node, "wall_normal_z", 0.0);
        return new PositionState(x, y, z, floorY, wallNormalX, wallNormalZ);
    }

    private HorizontalVelocity applyHorizontalInput(BehaviorFrame frame, PlayerInputState input, double subDt, double groundFriction) {
        double[] wish = movementVector(input.yawDeg(), input.moveX(), input.moveZ());
        double wishVx = wish[0] * frame.speed;
        double wishVz = wish[1] * frame.speed;
        boolean hasInput = Math.abs(wish[0]) > INPUT_EPSILON || Math.abs(wish[1]) > INPUT_EPSILON;
        double control = frame.onFloorPrev ? 1.0 : frame.airControl;
        double accelRate = frame.acceleration * control * subDt;
        double decelRate = frame.deceleration * control * subDt;
        double vx = MathUtils.approach(frame.velocityX, wishVx, hasInput ? accelRate : decelRate);
        double vz = MathUtils.approach(frame.velocityZ, wishVz, hasInput ? accelRate : decelRate);
        if (frame.onFloorPrev && !hasInput) {
            vx = MathUtils.approach(vx, 0.0, groundFriction * subDt);
            vz = MathUtils.approach(vz, 0.0, groundFriction * subDt);
        }
        return new HorizontalVelocity(vx, vz);
    }

    private double updateWallContact(double wallContact, boolean hasWallContact, double subDt) {
        if (hasWallContact) {
            return WALL_CONTACT_DURATION_SECONDS;
        }
        return Math.max(0.0, wallContact - subDt);
    }

    private CharacterState stateFor(long nodeId) {
        return characterStates.computeIfAbsent(nodeId, ignored -> new CharacterState());
    }

    private CharacterMotion resolveCharacterMotion(ServerScene scene, Node node, double x, double y, double z,
                                                   double vx, double vy, double vz, double dt,
                                                   double floorSnapLength, boolean wasOnFloor, boolean jumpLocked) {
        if (node == null || scene == null) {
            return new CharacterMotion(x + vx * dt, y + vy * dt, z + vz * dt, false, false, false, 0.0, 0.0);
        }
        JoltPhysicsWorld physics = scene.physics();
        if (physics == null) {
            return new CharacterMotion(x + vx * dt, y + vy * dt, z + vz * dt, false, false, false, 0.0, 0.0);
        }

        double radius = Math.max(0.05, propNumber(node, "radius", 0.5));
        double height = Math.max(radius * 2.0, propNumber(node, "height", 2.0));
        double px = x;
        double py = y;
        double pz = z;
        double remX = vx * dt;
        double remY = vy * dt;
        double remZ = vz * dt;
        boolean onFloor = false;
        boolean onWall = false;
        boolean onCeiling = false;
        double wallNx = 0.0;
        double wallNz = 0.0;

        for (int i = 0; i < MAX_SLIDES; i++) {
            if (remX * remX + remY * remY + remZ * remZ <= VECTOR_EPSILON) {
                break;
            }

            var hit = physics.sweepCharacter(node, px, py, pz, remX, remY, remZ).orElse(null);
            if (hit == null || hit.fraction() >= 1.0) {
                px += remX;
                py += remY;
                pz += remZ;
                break;
            }

            double moveFrac = Math.max(0.0, hit.fraction() - SWEEP_EPSILON);
            px += remX * moveFrac;
            py += remY * moveFrac;
            pz += remZ * moveFrac;

            if (jumpLocked && hit.fraction() <= SWEEP_EPSILON && hit.ny() >= FLOOR_COS && remY > 0.0) {
                px += remX;
                py += remY;
                pz += remZ;
                onWall = false;
                onCeiling = false;
                break;
            }

            if (hit.ny() >= FLOOR_COS && remY <= FLOOR_EPSILON) {
                onFloor = true;
            } else if (hit.ny() <= -FLOOR_COS && remY > 0.0) {
                onCeiling = true;
            } else {
                onWall = true;
                double nLenSq = hit.nx() * hit.nx() + hit.nz() * hit.nz();
                if (nLenSq > VECTOR_EPSILON) {
                    double invLen = 1.0 / Math.sqrt(nLenSq);
                    wallNx = hit.nx() * invLen;
                    wallNz = hit.nz() * invLen;
                }
            }

            double remain = Math.max(0.0, 1.0 - hit.fraction());
            double slideX = remX * remain;
            double slideY = remY * remain;
            double slideZ = remZ * remain;

            double nx = hit.nx();
            double ny = hit.ny();
            double nz = hit.nz();
            double into = slideX * nx + slideY * ny + slideZ * nz;
            if (into > 0.0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
                into = slideX * nx + slideY * ny + slideZ * nz;
            }
            px += nx * COLLISION_SKIN;
            py += ny * COLLISION_SKIN;
            pz += nz * COLLISION_SKIN;
            if (hit.fraction() <= SWEEP_EPSILON && into >= -FLOOR_EPSILON) {
                break;
            }
            if (into < 0.0) {
                slideX -= nx * into;
                slideY -= ny * into;
                slideZ -= nz * into;
            }

            remX = slideX;
            remY = slideY;
            remZ = slideZ;
        }

        double reach = jumpLocked ? 0.0 : (wasOnFloor ? floorSnapLength : Math.min(floorSnapLength, 0.05));
        double fallDist = remY < 0.0 ? Math.min(Math.abs(remY), 10.0) : 0.0;
        if (!onFloor && remY <= FLOOR_EPSILON && reach > 0.0) {
            double snapDist = reach + fallDist;
            var hit = physics.sweepCharacter(node, px, py, pz, 0.0, -snapDist, 0.0).orElse(null);
            if (hit != null && hit.ny() >= FLOOR_COS) {
                double snap = snapDist * Math.max(0.0, hit.fraction() - SWEEP_EPSILON);
                py -= snap;
                onFloor = true;
            }
        }

        return new CharacterMotion(px, py, pz, onFloor, onWall, onCeiling, wallNx, wallNz);
    }

    private FloorSnap snapCharacterFloor(ServerScene scene, Node node, double x, double y, double z, double vy, double dt, double snapLength) {
        if (vy > 0.0) {
            return new FloorSnap(y, false);
        }
        JoltPhysicsWorld physics = scene.physics();
        if (physics != null && snapLength > 0.0) {
            double fallDist = vy < 0.0 ? Math.min(Math.abs(vy) * dt, 10.0) : 0.0;
            double rayStartY = y + 0.1 + fallDist;
            double maxDist = snapLength + 0.15 + fallDist;
            var hit = physics.raycast(x, rayStartY, z, 0.0, -1.0, 0.0, maxDist);
            if (hit.isPresent()) {
                double hitY = hit.get().hitY();
                if (hitY <= rayStartY + FLOOR_EPSILON && y - hitY <= snapLength + fallDist + 0.1) {
                    return new FloorSnap(hitY, true);
                }
            }
        }
        return new FloorSnap(y, false);
    }

    private boolean detectCharacterWallContact(ServerScene scene, Node node, double x, double y, double z) {
        if (node == null || scene == null) {
            return false;
        }
        JoltPhysicsWorld physics = scene.physics();
        if (physics == null) {
            return false;
        }
        Integer selfBodyId = physics.bodyIdForNode(node.nodeId());
        double radius = Math.max(0.05, propNumber(node, "radius", 0.5));
        double height = Math.max(radius * 2.0, propNumber(node, "height", 2.0));
        double probeRadius = Math.max(0.06, radius * 0.22);
        double probeDistance = radius + probeRadius + 0.03;
        double lowerY = y + Math.min(height * 0.35, Math.max(radius, 0.45));
        double upperY = y + Math.max(lowerY + 0.2, height * 0.7);
        double[] ys = {lowerY, upperY};
        for (double probeY : ys) {
            for (int i = 0; i < WALL_PROBE_DIRECTIONS.length; i += 2) {
                double px = x + WALL_PROBE_DIRECTIONS[i] * probeDistance;
                double pz = z + WALL_PROBE_DIRECTIONS[i + 1] * probeDistance;
                for (BodyHandle handle : physics.overlapSphere(px, probeY, pz, probeRadius)) {
                    if (handle == null || !handle.isValid()) {
                        continue;
                    }
                    if (selfBodyId != null && handle.id() == selfBodyId) {
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private HorizontalMotion resolveCharacterHorizontalMotion(ServerScene scene, Node node, double x, double y, double z, double dx, double dz) {
        if (node == null || scene == null) {
            return new HorizontalMotion(x + dx, z + dz, false, 0.0, 0.0);
        }
        JoltPhysicsWorld physics = scene.physics();
        if (physics == null) {
            return new HorizontalMotion(x + dx, z + dz, false, 0.0, 0.0);
        }
        double nextX = x;
        double nextZ = z;
        double remainX = dx;
        double remainZ = dz;
        boolean onWall = false;
        double lastWallNx = 0.0;
        double lastWallNz = 0.0;

        for (int i = 0; i < 2; i++) {
            if (remainX * remainX + remainZ * remainZ <= VECTOR_EPSILON) {
                break;
            }
            var hit = physics.sweepCharacter(node, nextX, y, nextZ, remainX, 0.0, remainZ).orElse(null);
            if (hit == null || hit.fraction() >= 1.0) {
                nextX += remainX;
                nextZ += remainZ;
                break;
            }

            onWall = true;
            double moveFrac = Math.max(0.0, hit.fraction() - SWEEP_EPSILON);
            nextX += remainX * moveFrac;
            nextZ += remainZ * moveFrac;

            double leftoverFrac = Math.max(0.0, 1.0 - hit.fraction());
            double slideX = remainX * leftoverFrac;
            double slideZ = remainZ * leftoverFrac;

            double nx = hit.nx();
            double nz = hit.nz();
            double nLenSq = nx * nx + nz * nz;
            if (nLenSq <= VECTOR_EPSILON) {
                break;
            }
            double invLen = 1.0 / Math.sqrt(nLenSq);
            nx *= invLen;
            nz *= invLen;

            lastWallNx = nx;
            lastWallNz = nz;

            double into = slideX * nx + slideZ * nz;
            if (into > 0.0) {
                nx = -nx;
                nz = -nz;
                into = slideX * nx + slideZ * nz;
            }
            nextX += nx * COLLISION_SKIN;
            nextZ += nz * COLLISION_SKIN;
            if (hit.fraction() <= SWEEP_EPSILON && into >= -FLOOR_EPSILON) {
                break;
            }
            if (into < 0.0) {
                slideX -= nx * into;
                slideZ -= nz * into;
            }

            remainX = slideX;
            remainZ = slideZ;
        }
        return new HorizontalMotion(nextX, nextZ, onWall, lastWallNx, lastWallNz);
    }

    private boolean isCharacterBody(ServerScene scene, Node node) {
        return node != null && scene != null && "CharacterBody3D".equals(scene.engine().nodeTypes().typeIdFor(node));
    }

    private boolean propBool(Node node, String key, boolean fallback) {
        return ParseUtils.parseBool(prop(node, key), fallback);
    }

    private double propNumber(Node node, String key, double fallback) {
        return ParseUtils.parseDouble(prop(node, key), fallback);
    }

    private String prop(Node node, String key) {
        if (node == null || key == null || key.isBlank()) {
            return null;
        }
        String alias = ParseUtils.alternatePropertyKey(key);
        if (alias != null) {
            String pendingAlias = mutator.getPending(node.nodeId(), alias);
            if (pendingAlias != null) {
                return pendingAlias;
            }
            String aliasValue = node.getProperty(alias);
            if (aliasValue != null) {
                return aliasValue;
            }
        }
        String pending = mutator.getPending(node.nodeId(), key);
        if (pending != null) {
            return pending;
        }
        return node.getProperty(key);
    }

    private static final class CharacterState {
        private double floorY = Double.NEGATIVE_INFINITY;
        private boolean jumpDown;
        private boolean sprintDown;
        private boolean sneakDown;
        private double wallContactSeconds;
        private int jumpLockSteps;
        private int jumpBufferSteps;
        private int coyoteSteps;
    }

    private record CharacterConfig(double groundFriction, double floorSnapLength) {
    }

    private record HorizontalVelocity(double vx, double vz) {
    }

    private record CharacterMotion(double x, double y, double z,
                                   boolean onFloor, boolean onWall, boolean onCeiling,
                                   double wallNx, double wallNz) {
    }

    private static final class PositionState {
        private double x;
        private double y;
        private double z;
        private double floorY;
        private final double wallNormalX;
        private final double wallNormalZ;

        private PositionState(double x, double y, double z, double floorY, double wallNormalX, double wallNormalZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.floorY = floorY;
            this.wallNormalX = wallNormalX;
            this.wallNormalZ = wallNormalZ;
        }
    }
}
