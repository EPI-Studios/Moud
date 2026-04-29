package com.moud.server.minestom.scripting.physics;

import com.moud.core.scene.Node;
import com.moud.core.util.MathUtils;
import com.moud.core.util.ParseUtils;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.physics.rapier.RapierScenePhysicsWorld;
import com.moud.server.minestom.scripting.lang.RuntimeScriptUtil;
import com.moud.server.minestom.scripting.player.PlayerInputState;
import com.moud.server.minestom.scripting.player.PlayerNetworkSink;
import com.moud.server.minestom.scripting.player.PlayerStateManager;
import com.moud.server.minestom.scripting.scene.SceneMutator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class CharacterBodySimulator {
    private static final double CHARACTER_TICKS_PER_SECOND = 20.0;
    private static final double INPUT_EPSILON = 1.0e-6;
    private static final double VECTOR_EPSILON = 1.0e-8;
    private static final double FLOOR_EPSILON = 1.0e-5;
    private static final int JUMP_BUFFER_TICKS = 6;
    private static final int COYOTE_TICKS = 4;
    private static final int JUMP_LOCK_TICKS = 3;
    private static final double WALL_CONTACT_DURATION_SECONDS = 0.15;
    private static final double WALL_NORMAL_DOT = 0.5;

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
        if (scene == null) return;
        double dt = Double.isFinite(dtSeconds) && dtSeconds > 0.0 ? dtSeconds : 1.0 / CHARACTER_TICKS_PER_SECOND;
        ArrayList<Node> stack = new ArrayList<>();
        stack.add(scene.engine().sceneTree().root());
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            if (node == null) continue;

            if (isCharacterBody(scene, node)) {
                boolean enabled = propBool(node, "enabled", true);
                boolean playerCtrl = propBool(node, "player_controlled", false);
                boolean scriptCtrl = propBool(node, "script_controlled", false);
                if (enabled && playerCtrl && !scriptCtrl) {
                    tickCharacterBody(scene, node, dt);
                }
            }

            for (int i = node.children().size() - 1; i >= 0; i--) {
                Node child = node.children().get(i);
                if (child != null) stack.add(child);
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
        RapierScenePhysicsWorld physics = scene.physics();
        if (physics == null) {
            return new double[]{0.0, 0.0, 0.0};
        }
        double dt = Double.isFinite(deltaSeconds) && deltaSeconds > 0.0 ? deltaSeconds : 1.0 / CHARACTER_TICKS_PER_SECOND;
        long nodeId = node.nodeId();
        double startX = propNumber(node, "x", 0.0);
        double startY = propNumber(node, "y", 0.0);
        double startZ = propNumber(node, "z", 0.0);

        RapierScenePhysicsWorld.CharacterTickResult r = physics.tickCharacter(node, startX, startY, startZ, vx, vy, vz, dt);
        if (r == null) return new double[]{0.0, 0.0, 0.0};

        boolean onFloor = r.onGround();
        boolean onCeiling = !onFloor && r.groundNy() < -WALL_NORMAL_DOT;
        boolean onWall = !onFloor && !onCeiling && r.onSteepGround();
        double wallNx = onWall ? r.groundNx() : 0.0;
        double wallNz = onWall ? r.groundNz() : 0.0;

        CharacterState state = stateFor(nodeId);
        state.floorY = onFloor ? r.y() : Double.NEGATIVE_INFINITY;

        double actualVx = dt > VECTOR_EPSILON ? (r.x() - startX) / dt : 0.0;
        double actualVy = onFloor ? 0.0 : (dt > VECTOR_EPSILON ? (r.y() - startY) / dt : 0.0);
        double actualVz = dt > VECTOR_EPSILON ? (r.z() - startZ) / dt : 0.0;

        mutator.queueSet(nodeId, "x", RuntimeScriptUtil.trimFloat((float) r.x()));
        mutator.queueSet(nodeId, "y", RuntimeScriptUtil.trimFloat((float) r.y()));
        mutator.queueSet(nodeId, "z", RuntimeScriptUtil.trimFloat((float) r.z()));
        mutator.queueSet(nodeId, "on_floor", Boolean.toString(onFloor));
        mutator.queueSet(nodeId, "on_wall", Boolean.toString(onWall));
        mutator.queueSet(nodeId, "on_ceiling", Boolean.toString(onCeiling));
        mutator.queueSet(nodeId, "wall_normal_x", RuntimeScriptUtil.trimFloat((float) wallNx));
        mutator.queueSet(nodeId, "wall_normal_z", RuntimeScriptUtil.trimFloat((float) wallNz));
        if (onFloor) mutator.queueSet(nodeId, "velocity_y", "0");

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
        if (node == null) return new double[]{0.0, 0.0, 0.0};
        double nx = propNumber(node, "wall_normal_x", 0.0);
        double nz = propNumber(node, "wall_normal_z", 0.0);
        return new double[]{nx, 0.0, nz};
    }

    public double[] inputDirection(Node node) {
        PlayerInputState input = playerState.resolveInputFor(node);
        if (input == null) return new double[]{0.0, 0.0, 0.0};
        double[] dir = MathUtils.yawInputToDirection(input.yawDeg(), input.moveX(), input.moveZ());
        return new double[]{dir[0], 0.0, dir[1]};
    }

    public static double[] movementVector(float yawDeg, float moveX, float moveZ) {
        return MathUtils.yawInputToDirection(yawDeg, moveX, moveZ);
    }

    private void tickCharacterBody(ServerScene scene, Node node, double dt) {
        PlayerInputState input = playerState.resolveInputFor(node);
        if (input == null) return;
        RapierScenePhysicsWorld physics = scene.physics();
        if (physics == null) return;

        long nodeId = node.nodeId();
        CharacterState state = stateFor(nodeId);

        double speed = Math.max(0.0, propNumber(node, "speed", 5.0));
        double acceleration = Math.max(0.0, propNumber(node, "acceleration", 40.0));
        double deceleration = Math.max(0.0, propNumber(node, "deceleration", 30.0));
        double airControl = Math.max(0.0, Math.min(1.0, propNumber(node, "air_control", 0.3)));
        double jumpVelocity = Math.max(0.0, propNumber(node, "jump_velocity", 10.0));
        double groundFriction = Math.max(0.0, propNumber(node, "ground_friction", 70.0));

        double posX = propNumber(node, "x", 0.0);
        double posY = propNumber(node, "y", 0.0);
        double posZ = propNumber(node, "z", 0.0);
        double prevVx = propNumber(node, "velocity_x", 0.0);
        double prevVy = propNumber(node, "velocity_y", 0.0);
        double prevVz = propNumber(node, "velocity_z", 0.0);
        boolean wasOnFloor = propBool(node, "on_floor", false);

        boolean jumpDown = input.jump();
        boolean sprintDown = input.sprint();
        boolean sneakDown = input.sneak();
        if (jumpDown && !state.jumpDown) state.jumpBufferTicks = JUMP_BUFFER_TICKS;

        double[] wish = MathUtils.yawInputToDirection(input.yawDeg(), input.moveX(), input.moveZ());
        boolean hasInput = Math.abs(wish[0]) > INPUT_EPSILON || Math.abs(wish[1]) > INPUT_EPSILON;
        double wishVx = wish[0] * speed;
        double wishVz = wish[1] * speed;
        double control = wasOnFloor ? 1.0 : airControl;
        double accelRate = acceleration * control * dt;
        double decelRate = deceleration * control * dt;
        double vx = MathUtils.approach(prevVx, wishVx, hasInput ? accelRate : decelRate);
        double vz = MathUtils.approach(prevVz, wishVz, hasInput ? accelRate : decelRate);
        if (wasOnFloor && !hasInput) {
            vx = MathUtils.approach(vx, 0.0, groundFriction * dt);
            vz = MathUtils.approach(vz, 0.0, groundFriction * dt);
        }
        double vy = prevVy;

        boolean canJump = wasOnFloor || state.coyoteTicks > 0 || posY <= state.floorY + FLOOR_EPSILON;
        if (state.jumpBufferTicks > 0 && canJump && state.jumpLockTicks <= 0) {
            vy = jumpVelocity;
            state.jumpBufferTicks = 0;
            state.coyoteTicks = 0;
            state.jumpLockTicks = JUMP_LOCK_TICKS;
        }

        RapierScenePhysicsWorld.CharacterTickResult r = physics.tickCharacter(node, posX, posY, posZ, vx, vy, vz, dt);
        if (r == null) {
            // no mouvement
            return;
        }

        boolean onFloor = r.onGround();
        boolean onCeiling = !onFloor && r.groundNy() < -WALL_NORMAL_DOT && r.vy() > -FLOOR_EPSILON;
        boolean onWall = !onFloor && !onCeiling && r.onSteepGround();
        double wallNx = onWall ? r.groundNx() : 0.0;
        double wallNz = onWall ? r.groundNz() : 0.0;

        if (onFloor) state.coyoteTicks = COYOTE_TICKS;
        else if (state.coyoteTicks > 0) state.coyoteTicks -= 1;
        if (state.jumpLockTicks > 0) state.jumpLockTicks -= 1;
        if (state.jumpBufferTicks > 0) state.jumpBufferTicks -= 1;

        state.jumpDown = jumpDown;
        state.sprintDown = sprintDown;
        state.sneakDown = sneakDown;
        state.floorY = onFloor ? r.y() : Double.NEGATIVE_INFINITY;
        state.wallContactSeconds = onWall
                ? WALL_CONTACT_DURATION_SECONDS
                : Math.max(0.0, state.wallContactSeconds - dt);

        mutator.queueSet(nodeId, "x", RuntimeScriptUtil.trimFloat((float) r.x()));
        mutator.queueSet(nodeId, "y", RuntimeScriptUtil.trimFloat((float) r.y()));
        mutator.queueSet(nodeId, "z", RuntimeScriptUtil.trimFloat((float) r.z()));
        mutator.queueSet(nodeId, "velocity_x", RuntimeScriptUtil.trimFloat((float) r.vx()));
        mutator.queueSet(nodeId, "velocity_y", RuntimeScriptUtil.trimFloat((float) r.vy()));
        mutator.queueSet(nodeId, "velocity_z", RuntimeScriptUtil.trimFloat((float) r.vz()));
        mutator.queueSet(nodeId, "on_floor", Boolean.toString(onFloor));
        mutator.queueSet(nodeId, "on_wall", Boolean.toString(onWall));
        mutator.queueSet(nodeId, "on_ceiling", Boolean.toString(onCeiling));
        mutator.queueSet(nodeId, "wall_normal_x", RuntimeScriptUtil.trimFloat((float) wallNx));
        mutator.queueSet(nodeId, "wall_normal_z", RuntimeScriptUtil.trimFloat((float) wallNz));

        networkSink.sendPlayerPosition(input.playerUuid(), (float) r.x(), (float) r.y(), (float) r.z(), input.yawDeg());
        networkSink.sendPlayerVelocity(input.playerUuid(), (float) r.vx(), (float) r.vy(), (float) r.vz());
    }

    private CharacterState stateFor(long nodeId) {
        return characterStates.computeIfAbsent(nodeId, ignored -> new CharacterState());
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
        if (node == null || key == null || key.isBlank()) return null;
        String alias = ParseUtils.alternatePropertyKey(key);
        if (alias != null) {
            String pendingAlias = mutator.getPending(node.nodeId(), alias);
            if (pendingAlias != null) return pendingAlias;
            String aliasValue = node.getProperty(alias);
            if (aliasValue != null) return aliasValue;
        }
        String pending = mutator.getPending(node.nodeId(), key);
        if (pending != null) return pending;
        return node.getProperty(key);
    }

    private static final class CharacterState {
        private double floorY = Double.NEGATIVE_INFINITY;
        private boolean jumpDown;
        private boolean sprintDown;
        private boolean sneakDown;
        private double wallContactSeconds;
        private int jumpLockTicks;
        private int jumpBufferTicks;
        private int coyoteTicks;
    }
}
