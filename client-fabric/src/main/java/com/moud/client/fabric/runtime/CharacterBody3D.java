package com.moud.client.fabric.runtime;

import com.moud.client.fabric.mixin.accessor.EntityGroundAccessor;
import com.moud.client.fabric.physics.rapier.ClientRapierPhysics;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.scene.SceneNodeTransforms;
import com.moud.client.fabric.scripting.api.BodyApiTarget;
import com.moud.core.util.MathUtils;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.SceneSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public final class CharacterBody3D implements BodyApiTarget {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double SIMULATION_TPS = 60.0;
    private static final int SUBSTEPS = (int) (SIMULATION_TPS / TICKS_PER_SECOND);
    private static final double SUB_DT = 1.0 / SIMULATION_TPS;
    private static final double DEFAULT_GRAVITY = 30.0;
    private static final int MAX_SLIDES = 4;
    private static final int JUMP_LOCK_STEPS = SUBSTEPS;
    private static final double EPSILON = 1.0e-5;
    private static final double FLOOR_COS = Math.cos(Math.toRadians(46.0));
    private static final double SWEEP_EPSILON = 1.0e-3;
    private static final double COLLISION_SKIN = 2.0e-4;
    private static final double JUMP_LIFTOFF = 0.05;
    private static final int JUMP_BUFFER_STEPS = SUBSTEPS * 3;
    private static final int COYOTE_STEPS = SUBSTEPS * 2;
    private static final double WALL_CONTACT_TIME = 0.15;
    private static final double SQRT2_2 = Math.sqrt(2.0) / 2.0;
    private static final double[] WALL_PROBES = {
            1, 0, -1, 0, 0, 1, 0, -1,
            SQRT2_2, SQRT2_2, -SQRT2_2, SQRT2_2, SQRT2_2, -SQRT2_2, -SQRT2_2, -SQRT2_2
    };

    private final Vector3f initialPos = new Vector3f();
    private final Vector3f scratchPos = new Vector3f();

    private long sceneVersion = Long.MIN_VALUE, serverPosVersion = Long.MIN_VALUE, nodeId;
    private boolean enabled, initialPosApplied, hasScriptVelocity, simInitialized;
    private float speed = 5f, acceleration = 40f, deceleration = 30f, groundFriction = 70f;
    private float airControl = 0.3f, jumpVelocity = 10f, gravityScale = 1f;
    private float floorSnap = 0.2f, radius = 0.3f, height = 1.8f, rotationZ = 0f;

    private double floorY = Double.NEGATIVE_INFINITY, simX, simY, simZ, prevSimX, prevSimY, prevSimZ;
    private double wallContact, wallNx, wallNz, floorNx = 0, floorNy = 1, floorNz = 0;

    private boolean prevOnFloor, justLeftFloor, justLanded, onFloor, onWall, onCeiling;
    private boolean jumpWasDown, sprintWasDown, sneakWasDown, jumpRequested;
    private int jumpLock, jumpBuffer, coyoteSteps;

    private String clientScriptPath;
    private Vec3d velocity = Vec3d.ZERO, scriptVelocity = Vec3d.ZERO;

    public boolean isActive() {
        refreshBinding();
        return enabled && nodeId > 0L;
    }

    public void reset() {
        sceneVersion = Long.MIN_VALUE;
        serverPosVersion = Long.MIN_VALUE;
        nodeId = 0L;
        enabled = initialPosApplied = hasScriptVelocity = simInitialized = false;
        speed = 5f; acceleration = 40f; deceleration = 30f; groundFriction = 70f;
        airControl = 0.3f; jumpVelocity = 10f; gravityScale = 1f; floorSnap = 0.2f;
        radius = 0.3f; height = 1.8f; rotationZ = 0f;
        scriptVelocity = velocity = Vec3d.ZERO;
        prevOnFloor = justLeftFloor = justLanded = onFloor = onWall = onCeiling = false;
        jumpWasDown = sprintWasDown = sneakWasDown = jumpRequested = false;
        jumpLock = jumpBuffer = coyoteSteps = 0;
        wallContact = wallNx = wallNz = floorNx = floorNz = 0;
        floorNy = 1;
        floorY = Double.NEGATIVE_INFINITY;
        simX = simY = simZ = prevSimX = prevSimY = prevSimZ = 0.0;
        clientScriptPath = null;
        initialPos.zero();
    }

    public boolean applyInputToVanilla(Input input, PlayRuntimeInputState state) {
        if (input == null || state == null || !isActive()) return false;
        var move = state.movement();
        input.movementSideways = -move.moveX();
        input.movementForward = move.moveZ();
        input.pressingLeft = move.moveX() < -EPSILON;
        input.pressingRight = move.moveX() > EPSILON;
        input.pressingForward = move.moveZ() > EPSILON;
        input.pressingBack = move.moveZ() < -EPSILON;
        input.jumping = state.jump();
        input.sneaking = false;
        return true;
    }

    public boolean tick(PlayerEntity player, PlayRuntimeInputState state) {
        if (player == null || state == null || !isActive()) return false;

        if (!simInitialized || player.squaredDistanceTo(simX, simY, simZ) > 0.25) {
            simX = prevSimX = player.getX();
            simY = prevSimY = player.getY();
            simZ = prevSimZ = player.getZ();
            simInitialized = true;
        }

        applyInitialPosition(player);
        applyAuthoritativeScriptPosition(player);

        if (player.squaredDistanceTo(simX, simY, simZ) > 0.0001) {
            simX = prevSimX = player.getX();
            simY = prevSimY = player.getY();
            simZ = prevSimZ = player.getZ();
        }
        applyCollisionBox(player);

        if (hasScriptVelocity) {
            velocity = Vec3d.ZERO;
            player.setVelocity(Vec3d.ZERO);
            player.setPosition(prevSimX = simX, prevSimY = simY, prevSimZ = simZ);
            applyCollisionBox(player);
            ((EntityGroundAccessor) player).setOnGround(onFloor);
            return true;
        }

        var physics = ClientRapierPhysics.get();
        physics.syncSceneIfNeeded();
        if (player.getWorld() instanceof ClientWorld cw) {
            physics.updateTerrainWindow(cw, simX, simY, simZ);
        }

        if (jumpRequested || (state.jump() && !jumpWasDown)) {
            jumpBuffer = JUMP_BUFFER_STEPS;
        }
        jumpRequested = false;

        prevSimX = simX; prevSimY = simY; prevSimZ = simZ;
        for (int i = 0; i < SUBSTEPS; i++) runSubstep(player, state, physics);

        player.setPosition(simX, simY, simZ);
        applyCollisionBox(player);
        player.setVelocity(velocity);
        ((EntityGroundAccessor) player).setOnGround(onFloor);
        jumpWasDown = state.jump();
        sprintWasDown = state.sprint();
        sneakWasDown = state.sneak();
        return true;
    }

    public boolean applyRenderPose(PlayerEntity player, float delta) {
        if (player == null || !isActive() || !simInitialized) return false;
        double a = MathUtils.clamp01(delta);
        player.setPosition(prevSimX + (simX - prevSimX) * a, prevSimY + (simY - prevSimY) * a, prevSimZ + (simZ - prevSimZ) * a);
        applyCollisionBox(player);
        player.setVelocity(velocity);
        ((EntityGroundAccessor) player).setOnGround(onFloor);
        return true;
    }

    private void runSubstep(PlayerEntity player, PlayRuntimeInputState state, ClientRapierPhysics physics) {
        double px = simX, py = simY, pz = simZ;
        double r = Math.max(0.05, radius), h = Math.max(r * 2.0, height);
        double vx = velocity.x * TICKS_PER_SECOND, vy = velocity.y * TICKS_PER_SECOND, vz = velocity.z * TICKS_PER_SECOND;

        boolean grounded = jumpLock <= 0 && vy <= EPSILON
                && (onFloor || prevOnFloor || coyoteSteps > 0 || py <= floorY + EPSILON);
        if (grounded && Double.isFinite(floorY) && py <= floorY + Math.max(floorSnap, JUMP_LIFTOFF)) {
            py = floorY;
            vy = 0;
        }

        var move = state.movement();
        double[] wishDir = MathUtils.yawInputToDirection(player.getYaw(), move.moveX(), move.moveZ());
        boolean hasInput = Math.abs(wishDir[0]) > EPSILON || Math.abs(wishDir[1]) > EPSILON;

        double curSpeed = speed * (state.sprint() ? 1.35 : state.sneak() ? 0.3 : 1.0);
        double control = onFloor ? 1.0 : airControl;
        double accel = acceleration * control * SUB_DT, decel = deceleration * control * SUB_DT;

        vx = MathUtils.approach(vx, wishDir[0] * curSpeed, hasInput ? accel : decel);
        vz = MathUtils.approach(vz, wishDir[1] * curSpeed, hasInput ? accel : decel);

        if (onFloor && !hasInput) {
            vx = MathUtils.approach(vx, 0, groundFriction * SUB_DT);
            vz = MathUtils.approach(vz, 0, groundFriction * SUB_DT);
        }

        if (jumpBuffer > 0 && !grounded && jumpLock <= 0) {
            double probe = Math.max(0.08, floorSnap + JUMP_LIFTOFF);
            var floorProbe = physics.sweepCapsule(px, py, pz, r, h, 0.0, -probe, 0.0);
            if (floorProbe.isPresent() && floorProbe.get().ny() >= FLOOR_COS) {
                double snap = probe * Math.max(0.0, floorProbe.get().fraction() - SWEEP_EPSILON);
                py -= snap;
                floorY = py;
                grounded = true;
                vy = 0.0;
            }
        }

        if (jumpBuffer > 0 && grounded) {
            py += JUMP_LIFTOFF;
            vy = Math.max(0, jumpVelocity);
            grounded = false;
            floorY = Double.NEGATIVE_INFINITY;
            jumpLock = JUMP_LOCK_STEPS;
            jumpBuffer = 0;
        } else if (!grounded) {
            vy -= DEFAULT_GRAVITY * gravityScale * SUB_DT;
        }

        double startX = px, startY = py, startZ = pz;
        double remX = vx * SUB_DT, remY = vy * SUB_DT, remZ = vz * SUB_DT;
        boolean hitWall = false;
        boolean hitCeiling = false;
        boolean stepOnFloor = false;
        double floorHitNx = 0.0, floorHitNy = 1.0, floorHitNz = 0.0;
        double wallHitNx = 0.0, wallHitNz = 0.0;
        wallNx = wallNz = 0.0;

        for (int i = 0; i < MAX_SLIDES; i++) {
            double lenSq = remX * remX + remY * remY + remZ * remZ;
            if (lenSq <= EPSILON) break;

            var hitOpt = physics.sweepCapsule(px, py, pz, r, h, remX, remY, remZ);
            if (hitOpt.isEmpty()) {
                px += remX;
                py += remY;
                pz += remZ;
                break;
            }

            var hit = hitOpt.get();
            double frac = Math.max(0.0, hit.fraction() - SWEEP_EPSILON);
            px += remX * frac;
            py += remY * frac;
            pz += remZ * frac;

            if (jumpLock > 0 && hit.fraction() <= SWEEP_EPSILON && hit.ny() >= FLOOR_COS && remY > 0.0) {
                px += remX;
                py += remY;
                pz += remZ;
                hitWall = false;
                hitCeiling = false;
                break;
            }

            if (hit.ny() >= FLOOR_COS && remY <= EPSILON) {
                stepOnFloor = true;
                if (hit.ny() >= floorHitNy) {
                    floorHitNx = hit.nx();
                    floorHitNy = hit.ny();
                    floorHitNz = hit.nz();
                }
            } else if (hit.ny() <= -FLOOR_COS && remY > 0.0) {
                hitCeiling = true;
            } else {
                hitWall = true;
                double nLenSq = hit.nx() * hit.nx() + hit.nz() * hit.nz();
                if (nLenSq > EPSILON) {
                    double invLen = 1.0 / Math.sqrt(nLenSq);
                    wallHitNx = hit.nx() * invLen;
                    wallHitNz = hit.nz() * invLen;
                }
            }

            double remain = Math.max(0.0, 1.0 - hit.fraction());
            double sx = remX * remain;
            double sy = remY * remain;
            double sz = remZ * remain;

            double nx = hit.nx();
            double ny = hit.ny();
            double nz = hit.nz();
            double dot = sx * nx + sy * ny + sz * nz;

            if (dot > 0.0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
                dot = sx * nx + sy * ny + sz * nz;
            }
            px += nx * COLLISION_SKIN;
            py += ny * COLLISION_SKIN;
            pz += nz * COLLISION_SKIN;
            if (hit.fraction() <= SWEEP_EPSILON && dot >= -EPSILON) {
                break;
            }
            if (dot < 0.0) {
                sx -= nx * dot;
                sy -= ny * dot;
                sz -= nz * dot;
            }

            remX = sx;
            remY = sy;
            remZ = sz;
        }

        onFloor = false;
        double reach = jumpLock > 0 ? 0.0 : ((grounded || prevOnFloor) ? floorSnap : Math.min(floorSnap, 0.05));
        double fallDist = remY < 0.0 ? Math.min(Math.abs(remY), 10.0) : 0.0;

        if (!stepOnFloor && remY <= EPSILON && reach > 0.0) {
            double snapDist = reach + fallDist;
            var hit = physics.sweepCapsule(px, py, pz, r, h, 0.0, -snapDist, 0.0);
            if (hit.isPresent() && hit.get().ny() >= FLOOR_COS) {
                double snap = snapDist * Math.max(0.0, hit.get().fraction() - SWEEP_EPSILON);
                py -= snap;
                stepOnFloor = true;
                floorHitNx = hit.get().nx();
                floorHitNy = hit.get().ny();
                floorHitNz = hit.get().nz();
                if (vy < 0.0) {
                    vy = 0.0;
                }
            }
        }

        if (stepOnFloor) {
            floorY = py;
            onFloor = true;
            coyoteSteps = COYOTE_STEPS;
            floorNx = floorHitNx;
            floorNy = floorHitNy;
            floorNz = floorHitNz;
            if (vy < 0.0) vy = 0.0;
        } else {
            floorY = Double.NEGATIVE_INFINITY;
            floorNx = 0.0;
            floorNy = 1.0;
            floorNz = 0.0;
            if (coyoteSteps > 0) coyoteSteps--;
        }

        if (hitWall) {
            wallNx = wallHitNx;
            wallNz = wallHitNz;
        }
        if (hitCeiling && vy > 0.0) {
            vy = 0.0;
        }

        if (hitWall || detectWallContact(physics, px, py, pz, r, h)) wallContact = WALL_CONTACT_TIME;
        else wallContact = Math.max(0, wallContact - SUB_DT);

        onWall = wallContact > EPSILON;
        onCeiling = hitCeiling;
        justLeftFloor = prevOnFloor && !onFloor;
        justLanded = !prevOnFloor && onFloor;
        prevOnFloor = onFloor;

        simX = px; simY = py; simZ = pz;
        double storedVy = onFloor ? 0.0 : (py - startY) / SUB_DT / TICKS_PER_SECOND;
        velocity = new Vec3d((px - startX) / SUB_DT / TICKS_PER_SECOND, storedVy, (pz - startZ) / SUB_DT / TICKS_PER_SECOND);
        if (jumpLock > 0) jumpLock--;
        if (jumpBuffer > 0) jumpBuffer--;
    }

    private boolean detectWallContact(ClientRapierPhysics physics, double x, double y, double z, double r, double h) {
        double pRad = Math.max(0.06, r * 0.22), pDist = r + pRad + 0.03;
        double lowerY = y + Math.min(h * 0.35, Math.max(r, 0.45));
        double upperY = y + Math.max(lowerY + 0.2, h * 0.7);

        for (double py : new double[]{lowerY, upperY}) {
            for (int i = 0; i < WALL_PROBES.length; i += 2) {
                if (physics.overlapSphere(x + WALL_PROBES[i] * pDist, py, z + WALL_PROBES[i + 1] * pDist, pRad)) return true;
            }
        }
        return false;
    }

    public Vec3d velocity() { return velocity; }
    public float rotationZ() { return rotationZ; }
    public double velocityX() { return velocity.x * TICKS_PER_SECOND; }
    public double velocityY() { return velocity.y * TICKS_PER_SECOND; }
    public double velocityZ() { return velocity.z * TICKS_PER_SECOND; }
    public void setScriptVelocity(double vx, double vy, double vz) {
        this.velocity = new Vec3d(vx / TICKS_PER_SECOND, vy / TICKS_PER_SECOND, vz / TICKS_PER_SECOND);
    }

    private Vector3f livePlayerPos() {
        var p = MinecraftClient.getInstance().player;
        if (p != null) return scratchPos.set((float) p.getX(), (float) p.getY(), (float) p.getZ());
        SceneNodeTransforms.tryWorldPosition(nodeId, scratchPos);
        return scratchPos;
    }

    @Override
    public float getBodyFloat(String key) {
        if (key == null) return 0f;
        var p = MinecraftClient.getInstance().player;

        return switch (key) {
            case "position_x" -> livePlayerPos().x;
            case "position_y" -> livePlayerPos().y;
            case "position_z" -> livePlayerPos().z;
            case "velocity_x" -> (float) velocityX();
            case "velocity_y" -> (float) velocityY();
            case "velocity_z" -> (float) velocityZ();
            case "speed" -> speed;
            case "acceleration" -> acceleration;
            case "deceleration" -> deceleration;
            case "ground_friction" -> groundFriction;
            case "air_control" -> airControl;
            case "jump_velocity" -> jumpVelocity;
            case "gravity_scale" -> gravityScale;
            case "wall_normal_x" -> (float) wallNx;
            case "wall_normal_z" -> (float) wallNz;
            case "floor_normal_x" -> (float) floorNx;
            case "floor_normal_y" -> (float) floorNy;
            case "floor_normal_z" -> (float) floorNz;
            case "rotation_y" -> p != null ? p.bodyYaw : 0f;
            case "rotation_x" -> p != null ? p.getPitch() : 0f;
            case "rotation_z" -> rotationZ;
            default -> 0f;
        };
    }

    @Override
    public void setBodyFloat(String key, float value) {
        if (key == null) return;
        var p = MinecraftClient.getInstance().player;

        switch (key) {
            case "velocity_x" -> setScriptVelocity(value, velocityY(), velocityZ());
            case "velocity_y" -> setScriptVelocity(velocityX(), value, velocityZ());
            case "velocity_z" -> setScriptVelocity(velocityX(), velocityY(), value);
            case "speed" -> speed = Math.max(0, value);
            case "acceleration" -> acceleration = Math.max(0, value);
            case "deceleration" -> deceleration = Math.max(0, value);
            case "ground_friction" -> groundFriction = Math.max(0, value);
            case "air_control" -> airControl = Math.clamp(value, 0f, 1f);
            case "jump_velocity" -> jumpVelocity = value;
            case "gravity_scale" -> gravityScale = value;
            case "rotation_y" -> { if (p != null) p.setBodyYaw(value); }
            case "head_yaw" -> { if (p != null) { p.setYaw(value); p.setHeadYaw(value); } }
            case "rotation_x" -> { if (p != null) p.setPitch(value); }
            case "rotation_z" -> rotationZ = value;
        }
    }

    @Override
    public boolean getBodyBool(String key) {
        return key != null && switch (key) {
            case "on_floor" -> onFloor;
            case "on_wall" -> onWall;
            case "on_ceiling" -> onCeiling;
            case "just_left_floor" -> justLeftFloor;
            case "just_landed" -> justLanded;
            default -> false;
        };
    }

    @Override
    public void setBodyBool(String key, boolean value) {
        if ("jump_requested".equals(key)) jumpRequested = value;
    }

    public long nodeId() { refreshBinding(); return enabled ? nodeId : 0L; }

    private void refreshBinding() {
        if (sceneVersion == ClientSceneBus.version()) return;
        sceneVersion = ClientSceneBus.version();

        var node = nodeId > 0L ? ClientSceneBus.getNode(nodeId) : null;
        if (node == null || !"CharacterBody3D".equals(node.type())) {
            node = ClientSceneBus.copyNodes().stream()
                    .filter(n -> n != null && "CharacterBody3D".equals(n.type()) && propBool(n, "enabled", true))
                    .filter(n -> propBool(n, "player_controlled", false))
                    .findFirst().orElse(null);
        }

        long prevNodeId = nodeId;
        if (node == null) {
            nodeId = 0L; enabled = initialPosApplied = false;
            return;
        }

        nodeId = node.nodeId();
        enabled = propBool(node, "enabled", true) && propBool(node, "player_controlled", true) && !propBool(node, "script_controlled", false);
        speed = propFloat(node, "speed", 5f);
        acceleration = Math.max(0, propFloat(node, "acceleration", 40f));
        deceleration = Math.max(0, propFloat(node, "deceleration", 30f));
        groundFriction = Math.max(0, propFloat(node, "ground_friction", 70f));
        airControl = Math.clamp(propFloat(node, "air_control", 0.3f), 0f, 1f);
        jumpVelocity = propFloat(node, "jump_velocity", 10f);
        gravityScale = propFloat(node, "gravity_scale", 1f);
        floorSnap = Math.max(0, propFloat(node, "floor_snap_length", 0.2f));
        radius = Math.max(0.05f, propFloat(node, "radius", 0.3f));
        height = Math.max(radius * 2f, propFloat(node, "height", 1.8f));
        clientScriptPath = prop(node, "client_script");
        hasScriptVelocity = false; scriptVelocity = Vec3d.ZERO;

        if (nodeId != prevNodeId) {
            initialPosApplied = simInitialized = false;
            serverPosVersion = Long.MIN_VALUE;
            velocity = Vec3d.ZERO;
            jumpWasDown = sprintWasDown = sneakWasDown = jumpRequested = prevOnFloor = justLeftFloor = justLanded = onFloor = onWall = onCeiling = false;
            jumpLock = jumpBuffer = coyoteSteps = 0;
            floorY = Double.NEGATIVE_INFINITY;
            wallContact = wallNx = wallNz = 0;
        }

        if (!initialPosApplied || hasScriptVelocity) SceneNodeTransforms.tryWorldPosition(nodeId, initialPos);
    }

    private void applyInitialPosition(PlayerEntity p) {
        if (!initialPosApplied && Float.isFinite(initialPos.x) && Float.isFinite(initialPos.y) && Float.isFinite(initialPos.z)) {
            initialPosApplied = true;
            p.setPosition(initialPos.x, initialPos.y, initialPos.z);
            p.setVelocity(velocity = Vec3d.ZERO);
            applyCollisionBox(p);
        }
    }

    private void applyAuthoritativeScriptPosition(PlayerEntity p) {
        if (hasScriptVelocity && serverPosVersion != sceneVersion && Float.isFinite(initialPos.x)) {
            serverPosVersion = sceneVersion;
            p.setPosition(initialPos.x, initialPos.y, initialPos.z);
            applyCollisionBox(p);
        }
    }

    private void applyCollisionBox(PlayerEntity p) {
        if (p == null) return;
        double r = Math.max(0.05, radius), h = Math.max(r * 2.0, height);
        p.setBoundingBox(new Box(p.getX() - r, p.getY(), p.getZ() - r, p.getX() + r, p.getY() + h, p.getZ() + r));
    }

    private static float propFloat(SceneSnapshot.NodeSnapshot n, String k, float def) { return ParseUtils.parseFloat(prop(n, k), def); }
    private static boolean propBool(SceneSnapshot.NodeSnapshot n, String k, boolean def) { return ParseUtils.parseBool(prop(n, k), def); }

    private static String prop(SceneSnapshot.NodeSnapshot node, String key) {
        if (node == null || node.properties() == null) return null;
        String alias = ParseUtils.alternatePropertyKey(key);
        for (var p : node.properties()) {
            if (p != null && (key.equals(p.key()) || (alias != null && alias.equals(p.key())))) return p.value();
        }
        return null;
    }

}
