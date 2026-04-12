package com.moud.client.fabric.runtime;

import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.scene.SceneNodeTransforms;
import com.moud.client.fabric.scripting.api.BodyApiTarget;
import com.moud.core.util.MathUtils;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.SceneSnapshot;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public final class CharacterBody3D implements BodyApiTarget {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double SIMULATION_TICKS_PER_SECOND = 60.0;
    private static final int SUBSTEPS_PER_TICK = (int) (SIMULATION_TICKS_PER_SECOND / TICKS_PER_SECOND);
    private static final double DEFAULT_GRAVITY_PER_SECOND = 30.0;
    private final Vector3f initialPosition = new Vector3f();
    private final Vector3f scratchPos = new Vector3f();

    private long sceneVersion = Long.MIN_VALUE;
    private long nodeId;
    private long serverPositionVersionApplied = Long.MIN_VALUE;
    private boolean enabled;
    private boolean initialPositionApplied;
    private float speed = 5.0f;
    private float acceleration = 40.0f;
    private float deceleration = 30.0f;
    private float groundFriction = 70.0f;
    private float airControl = 0.3f;
    private float jumpVelocity = 10.0f;
    private float gravityScale = 1.0f;
    private float collisionRadius = 0.3f;
    private float collisionHeight = 1.8f;
    private boolean hasScriptVelocity;
    private Vec3d scriptVelocity = Vec3d.ZERO;

    private boolean prevOnFloor;
    private boolean justLeftFloor;
    private boolean justLanded;
    private String clientScriptPath;

    private double floorNormalX = 0.0;
    private double floorNormalY = 1.0;
    private double floorNormalZ = 0.0;

    private Vec3d velocity = Vec3d.ZERO;
    private boolean jumpWasDown;
    private boolean sprintWasDown;
    private boolean sneakWasDown;
    private boolean onFloor;
    private boolean onWall;
    private boolean onCeiling;
    private boolean jumpRequested;
    private double wallContact;
    private double wallNormalX;
    private double wallNormalZ;

    public boolean isActive() {
        refreshBinding();
        return enabled && nodeId > 0L;
    }

    public void reset() {
        sceneVersion = Long.MIN_VALUE;
        nodeId = 0L;
        serverPositionVersionApplied = Long.MIN_VALUE;
        enabled = false;
        initialPositionApplied = false;
        speed = 5.0f;
        acceleration = 40.0f;
        deceleration = 30.0f;
        groundFriction = 70.0f;
        airControl = 0.3f;
        jumpVelocity = 10.0f;
        gravityScale = 1.0f;
        collisionRadius = 0.3f;
        collisionHeight = 1.8f;
        hasScriptVelocity = false;
        scriptVelocity = Vec3d.ZERO;
        prevOnFloor = false;
        justLeftFloor = false;
        justLanded = false;
        clientScriptPath = null;
        velocity = Vec3d.ZERO;
        jumpWasDown = false;
        sprintWasDown = false;
        sneakWasDown = false;
        onFloor = false;
        onWall = false;
        onCeiling = false;
        jumpRequested = false;
        wallContact = 0.0;
        wallNormalX = 0.0;
        wallNormalZ = 0.0;
        floorNormalX = 0.0;
        floorNormalY = 1.0;
        floorNormalZ = 0.0;
        initialPosition.zero();
    }

    public boolean applyInputToVanilla(net.minecraft.client.input.Input input, PlayRuntimeInputState inputState) {
        if (input == null || inputState == null || !isActive()) {
            return false;
        }
        PlayRuntimeInputState.Movement movement = inputState.movement();
        input.movementSideways = -movement.moveX();
        input.movementForward = movement.moveZ();
        input.pressingLeft = movement.moveX() < -1.0e-5f;
        input.pressingRight = movement.moveX() > 1.0e-5f;
        input.pressingForward = movement.moveZ() > 1.0e-5f;
        input.pressingBack = movement.moveZ() < -1.0e-5f;
        input.jumping = inputState.jump();
        input.sneaking = false;
        return true;
    }

    public boolean travel(PlayerEntity player, PlayRuntimeInputState inputState, double dt) {
        if (player == null || inputState == null || !isActive()) {
            return false;
        }

        applyInitialPosition(player);
        applyAuthoritativeScriptPosition(player);
        applyCollisionBox(player);
        Vec3d frameStart = player.getPos();

        if (hasScriptVelocity) {
            velocity = Vec3d.ZERO;
            player.setVelocity(Vec3d.ZERO);
            return true;
        }

        boolean jumpDown = inputState.jump();
        boolean nativeJumpPending = jumpDown && !jumpWasDown;
        boolean sprintDown = inputState.sprint();
        boolean sneakDown = inputState.sneak();
        double substepDt = 1.0 / SIMULATION_TICKS_PER_SECOND;
        int numSubsteps = Math.max(1, Math.min(8, (int) Math.round(dt * SIMULATION_TICKS_PER_SECOND)));
        double simVx = velocity.x * TICKS_PER_SECOND;
        double simVy = velocity.y * TICKS_PER_SECOND;
        double simVz = velocity.z * TICKS_PER_SECOND;

        for (int step = 0; step < numSubsteps; step++) {
            boolean floorBeforeMove = player.isOnGround();

            PlayRuntimeInputState.Movement movement = inputState.movement();
            Vec3d wish = movementVector(player.getYaw(), movement.moveX(), movement.moveZ());
            double wishVx = wish.x * Math.max(0.0, speed);
            double wishVz = wish.z * Math.max(0.0, speed);
            boolean hasInput = Math.abs(wish.x) > 1.0e-6 || Math.abs(wish.z) > 1.0e-6;
            double airMul = floorBeforeMove ? 1.0 : airControl;
            double rate = (hasInput ? acceleration : deceleration) * airMul * substepDt;
            simVx = MathUtils.approach(simVx, wishVx, rate);
            simVz = MathUtils.approach(simVz, wishVz, rate);
            if (floorBeforeMove && !hasInput) {
                simVx = MathUtils.approach(simVx, 0.0, groundFriction * substepDt);
                simVz = MathUtils.approach(simVz, 0.0, groundFriction * substepDt);
            }
            if (floorBeforeMove && simVy < 0.0) {
                simVy = 0.0;
            }

            if (jumpRequested || (nativeJumpPending && floorBeforeMove)) {
                simVy = Math.max(0.0, jumpVelocity);
                nativeJumpPending = false;
            } else {
                simVy -= DEFAULT_GRAVITY_PER_SECOND * gravityScale * substepDt;
            }
            jumpRequested = false;

            double attemptX = simVx * substepDt;
            double attemptZ = simVz * substepDt;
            Vec3d requested = new Vec3d(attemptX, simVy * substepDt, attemptZ);
            Vec3d before = player.getPos();
            player.setVelocity(requested);
            player.move(MovementType.SELF, requested);
            Vec3d actual = player.getPos().subtract(before);

            onFloor = player.isOnGround();
            onCeiling = player.verticalCollision && requested.y > 0.0;

            if (onFloor && actual.y < 0.0) {
                actual = new Vec3d(actual.x, 0.0, actual.z);
            } else if (onCeiling && actual.y > 0.0) {
                actual = new Vec3d(actual.x, 0.0, actual.z);
            }

            double blockedX = attemptX - actual.x;
            double blockedZ = attemptZ - actual.z;
            double blockedLen = Math.sqrt(blockedX * blockedX + blockedZ * blockedZ);
            boolean steppedUp = actual.y > 1.0e-3;
            if (blockedLen > 1.0e-6 && !steppedUp) {
                wallNormalX = -(blockedX / blockedLen);
                wallNormalZ = -(blockedZ / blockedLen);
                wallContact = 0.15;
            } else {
                wallContact = Math.max(0.0, wallContact - substepDt);
            }
            onWall = wallContact > 1.0e-6;

            simVx = actual.x / substepDt;
            simVy = actual.y / substepDt;
            simVz = actual.z / substepDt;
        }

        jumpWasDown = jumpDown;
        sprintWasDown = sprintDown;
        sneakWasDown = sneakDown;

        justLeftFloor = prevOnFloor && !onFloor;
        justLanded    = !prevOnFloor && onFloor;
        prevOnFloor   = onFloor;

        if (onFloor) {
            Vec3d frameEnd = player.getPos();
            double fdx = frameEnd.x - frameStart.x;
            double fdy = Math.max(0.0, frameEnd.y - frameStart.y);
            double fdz = frameEnd.z - frameStart.z;
            double hLen = Math.sqrt(fdx * fdx + fdz * fdz);
            if (fdy > 1.0e-3 && hLen > 1.0e-6) {
                double totalLen = Math.sqrt(hLen * hLen + fdy * fdy);
                floorNormalX = -(fdx / hLen) * (fdy / totalLen);
                floorNormalY = hLen / totalLen;
                floorNormalZ = -(fdz / hLen) * (fdy / totalLen);
            } else {
                floorNormalX = 0.0;
                floorNormalY = 1.0;
                floorNormalZ = 0.0;
            }
        }

        velocity = new Vec3d(simVx / TICKS_PER_SECOND, simVy / TICKS_PER_SECOND, simVz / TICKS_PER_SECOND);
        player.setVelocity(velocity);
        return true;
    }

    public Vec3d velocity() {
        return velocity;
    }

    public boolean isOnFloor() {
        return onFloor;
    }

    public boolean isOnWall() {
        return onWall;
    }

    public boolean isOnCeiling() {
        return onCeiling;
    }

    public boolean isJustLeftFloor() { return justLeftFloor; }
    public boolean isJustLanded()    { return justLanded; }
    public double wallNormalX()      { return wallNormalX; }
    public double wallNormalZ()      { return wallNormalZ; }
    public double velocityX()        { return velocity.x * TICKS_PER_SECOND; }
    public double velocityY()        { return velocity.y * TICKS_PER_SECOND; }
    public double velocityZ()        { return velocity.z * TICKS_PER_SECOND; }
    public float speed()             { return speed; }
    public float acceleration()      { return acceleration; }
    public float deceleration()      { return deceleration; }
    public float groundFriction()    { return groundFriction; }
    public float airControl()        { return airControl; }
    public float jumpVelocity()      { return jumpVelocity; }
    public float gravityScale()      { return gravityScale; }
    public String clientScriptPath() { return clientScriptPath; }

    public void setJumpRequested(boolean v)   { this.jumpRequested = v; }
    public void setGravityScale(float v)      { this.gravityScale = v; }
    public void setJumpVelocity(float v)      { this.jumpVelocity = v; }
    public void setSpeed(float v)             { this.speed = v; }
    public void setScriptVelocity(double vx, double vy, double vz) {
        this.velocity = new Vec3d(vx / TICKS_PER_SECOND, vy / TICKS_PER_SECOND, vz / TICKS_PER_SECOND);
    }


    private Vector3f nodeWorldPos() {
        SceneNodeTransforms.tryWorldPosition(nodeId, scratchPos);
        return scratchPos;
    }
    private Vector3f livePlayerPos() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            scratchPos.set((float) player.getX(), (float) player.getY(), (float) player.getZ());
            return scratchPos;
        }
        return nodeWorldPos();
    }

    @Override
    public float getBodyFloat(String key) {
        if (key == null) return 0f;
        return switch (key) {
            case "position_x"    -> livePlayerPos().x;
            case "position_y"    -> livePlayerPos().y;
            case "position_z"    -> livePlayerPos().z;
            case "velocity_x"    -> (float) velocityX();
            case "velocity_y"    -> (float) velocityY();
            case "velocity_z"    -> (float) velocityZ();
            case "speed"         -> speed;
            case "acceleration"  -> acceleration;
            case "deceleration"  -> deceleration;
            case "ground_friction" -> groundFriction;
            case "air_control"   -> airControl;
            case "jump_velocity" -> jumpVelocity;
            case "gravity_scale" -> gravityScale;
            case "wall_normal_x" -> (float) wallNormalX;
            case "wall_normal_z" -> (float) wallNormalZ;
            case "rotation_y"      -> { PlayerEntity p = MinecraftClient.getInstance().player; yield p != null ? p.getYaw() : 0f; }
            case "rotation_x"      -> { PlayerEntity p = MinecraftClient.getInstance().player; yield p != null ? p.getPitch() : 0f; }
            case "floor_normal_x"  -> (float) floorNormalX;
            case "floor_normal_y"  -> (float) floorNormalY;
            case "floor_normal_z"  -> (float) floorNormalZ;
            default              -> 0f;
        };
    }

    @Override
    public void setBodyFloat(String key, float value) {
        if (key == null) return;
        switch (key) {
            case "velocity_x", "velocity_y", "velocity_z" -> {
                double vx = key.equals("velocity_x") ? value : velocityX();
                double vy = key.equals("velocity_y") ? value : velocityY();
                double vz = key.equals("velocity_z") ? value : velocityZ();
                setScriptVelocity(vx, vy, vz);
            }
            case "speed"           -> speed = Math.max(0f, value);
            case "acceleration"    -> acceleration = Math.max(0f, value);
            case "deceleration"    -> deceleration = Math.max(0f, value);
            case "ground_friction" -> groundFriction = Math.max(0f, value);
            case "air_control"     -> airControl = Math.max(0f, Math.min(1f, value));
            case "jump_velocity"   -> jumpVelocity = value;
            case "gravity_scale"   -> gravityScale = value;
            case "rotation_y" -> {
                PlayerEntity p = MinecraftClient.getInstance().player;
                if (p != null) { p.setYaw(value); p.setBodyYaw(value); p.setHeadYaw(value); }
            }
            case "rotation_x" -> {
                PlayerEntity p = MinecraftClient.getInstance().player;
                if (p != null) p.setPitch(value);
            }
            default -> { /* ignore */ }
        }
    }

    @Override
    public boolean getBodyBool(String key) {
        if (key == null) return false;
        return switch (key) {
            case "on_floor"       -> onFloor;
            case "on_wall"        -> onWall;
            case "on_ceiling"     -> onCeiling;
            case "just_left_floor" -> justLeftFloor;
            case "just_landed"    -> justLanded;
            default               -> false;
        };
    }

    @Override
    public void setBodyBool(String key, boolean value) {
        if ("jump_requested".equals(key)) {
            jumpRequested = value;
        }
    }


    public long nodeId() {
        refreshBinding();
        return enabled ? nodeId : 0L;
    }

    private void refreshBinding() {
        long version = ClientSceneBus.version();
        if (sceneVersion == version) {
            return;
        }
        sceneVersion = version;

        SceneSnapshot.NodeSnapshot next = findPlayerCharacterBody();
        long previousNodeId = nodeId;
        if (next == null) {
            nodeId = 0L;
            enabled = false;
            initialPositionApplied = false;
            return;
        }

        nodeId = next.nodeId();
        enabled = propertyBool(next, "enabled", true);
        speed = propertyFloat(next, "speed", 5.0f);
        acceleration = Math.max(0f, propertyFloat(next, "acceleration", 40.0f));
        deceleration = Math.max(0f, propertyFloat(next, "deceleration", 30.0f));
        groundFriction = Math.max(0f, propertyFloat(next, "ground_friction", 70.0f));
        airControl = Math.max(0f, Math.min(1f, propertyFloat(next, "air_control", 0.3f)));
        jumpVelocity = propertyFloat(next, "jump_velocity", 10.0f);
        gravityScale = propertyFloat(next, "gravity_scale", 1.0f);
        collisionRadius = Math.max(0.05f, propertyFloat(next, "radius", 0.3f));
        collisionHeight = Math.max(collisionRadius * 2.0f, propertyFloat(next, "height", 1.8f));
        clientScriptPath = property(next, "client_script");
        hasScriptVelocity = false;
        scriptVelocity = Vec3d.ZERO;

        if (nodeId != previousNodeId) {
            initialPositionApplied = false;
            velocity = Vec3d.ZERO;
            jumpWasDown = false;
            sprintWasDown = false;
            sneakWasDown = false;
            jumpRequested = false;
            prevOnFloor = false;
            justLeftFloor = false;
            justLanded = false;
            serverPositionVersionApplied = Long.MIN_VALUE;
        }
        if (!initialPositionApplied || hasScriptVelocity) {
            SceneNodeTransforms.tryWorldPosition(nodeId, initialPosition);
        }
    }

    private static SceneSnapshot.NodeSnapshot findPlayerCharacterBody() {
        List<SceneSnapshot.NodeSnapshot> nodes = ClientSceneBus.copyNodes();
        SceneSnapshot.NodeSnapshot fallback = null;
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null || !"CharacterBody3D".equals(node.type())) {
                continue;
            }
            if (!propertyBool(node, "enabled", true)) {
                continue;
            }
            if (propertyBool(node, "player_controlled", false)) {
                return node;
            }
            if (fallback == null) {
                fallback = node;
            }
        }
        return fallback;
    }

    private void applyInitialPosition(PlayerEntity player) {
        if (initialPositionApplied) {
            return;
        }
        initialPositionApplied = true;
        if (!Float.isFinite(initialPosition.x) || !Float.isFinite(initialPosition.y) || !Float.isFinite(initialPosition.z)) {
            return;
        }
        player.setPosition(initialPosition.x, initialPosition.y, initialPosition.z);
        player.setVelocity(Vec3d.ZERO);
        velocity = Vec3d.ZERO;
        applyCollisionBox(player);
    }

    private void applyAuthoritativeScriptPosition(PlayerEntity player) {
        if (!hasScriptVelocity || serverPositionVersionApplied == sceneVersion) {
            return;
        }
        serverPositionVersionApplied = sceneVersion;
        if (!Float.isFinite(initialPosition.x) || !Float.isFinite(initialPosition.y) || !Float.isFinite(initialPosition.z)) {
            return;
        }
        player.setPosition(initialPosition.x, initialPosition.y, initialPosition.z);
        applyCollisionBox(player);
    }

    private void applyCollisionBox(PlayerEntity player) {
        if (player == null) {
            return;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        double radius = Math.max(0.05, collisionRadius);
        double height = Math.max(radius * 2.0, collisionHeight);
        player.setBoundingBox(new Box(
                x - radius, y, z - radius,
                x + radius, y + height, z + radius
        ));
    }

    private static Vec3d movementVector(float yawDeg, float moveX, float moveZ) {
        double[] d = MathUtils.yawInputToDirection(yawDeg, moveX, moveZ);
        return d[0] == 0.0 && d[1] == 0.0 ? Vec3d.ZERO : new Vec3d(d[0], 0.0, d[1]);
    }

    private static float propertyFloat(SceneSnapshot.NodeSnapshot node, String key, float fallback) {
        return ParseUtils.parseFloat(property(node, key), fallback);
    }

    private static boolean propertyBool(SceneSnapshot.NodeSnapshot node, String key, boolean fallback) {
        return ParseUtils.parseBool(property(node, key), fallback);
    }

    private static String property(SceneSnapshot.NodeSnapshot node, String key) {
        if (node == null || key == null || node.properties() == null) {
            return null;
        }
        String alias = ParseUtils.alternatePropertyKey(key);
        if (alias != null) {
            for (SceneSnapshot.Property p : node.properties()) {
                if (p != null && alias.equals(p.key())) return p.value();
            }
        }
        for (SceneSnapshot.Property p : node.properties()) {
            if (p != null && key.equals(p.key())) return p.value();
        }
        return null;
    }
}
