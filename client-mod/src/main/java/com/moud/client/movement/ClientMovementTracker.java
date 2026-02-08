package com.moud.client.movement;

import com.moud.api.collision.AABB;
import com.moud.api.physics.player.CollisionWorld;
import com.moud.api.physics.player.PlayerController;
import com.moud.api.physics.player.PlayerInput;
import com.moud.api.physics.player.PlayerPhysicsController;
import com.moud.api.physics.player.PlayerPhysicsConfig;
import com.moud.api.physics.player.PlayerPhysicsControllers;
import com.moud.api.physics.player.PlayerState;
import com.moud.client.collision.ClientCollisionManager;
import com.moud.client.collision.CollisionMesh;
import com.moud.client.collision.CollisionResult;
import com.moud.client.collision.MeshCollider;
import com.moud.client.collision.ModelCollisionManager;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.client.physics.ClientPhysicsWorld;
import com.moud.client.primitives.ClientPrimitiveCollisionBounds;
import com.moud.client.primitives.ClientPrimitiveManager;
import com.moud.client.primitives.PrimitiveMeshCollisionManager;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ClientMovementTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientMovementTracker.class);
    private static ClientMovementTracker instance;

    private static final float FIXED_DT_SECONDS = 0.05f;
    private static final int MAX_HISTORY = 256;
    private static final double SNAP_DISTANCE_SQ = 16.0;
    private static final double RECONCILE_EPSILON_SQ = 0.0004;
    private static final double IDLE_RECONCILE_EPSILON_SQ = 0.0225;
    private static final double SMOOTH_CORRECTION_MAX_SQ = 1.0;
    private static final float SMOOTH_CORRECTION_RATE = 0.15f;
    private static final float MAX_FRAME_DT = 0.25f;
    private static final double CORRECTION_EPS = 1.0e-6;
    private static final double MESH_GROUND_PROBE = 0.05;

    private final MinecraftClient client;

    private boolean predictionEnabled = false;
    private PlayerPhysicsConfig config = PlayerPhysicsConfig.defaults();
    private String controllerId = PlayerPhysicsControllers.DEFAULT_ID;
    private PlayerPhysicsController controller = PlayerPhysicsControllers.get(PlayerPhysicsControllers.DEFAULT_ID);

    private boolean requestedPredictionEnabled = false;
    private String requestedControllerId = PlayerPhysicsControllers.DEFAULT_ID;
    private PlayerPhysicsConfig requestedConfig = PlayerPhysicsConfig.defaults();

    private PlayerState state;
    private long nextSequenceId = 1L;
    private long lastServerAck = 0L;
    private final ArrayDeque<PlayerInput> pendingInputs = new ArrayDeque<>();

    private boolean lastForward = false;
    private boolean lastBackward = false;
    private boolean lastLeft = false;
    private boolean lastRight = false;
    private boolean lastJumping = false;
    private boolean lastSneaking = false;
    private boolean lastSprinting = false;
    private boolean lastOnGround = false;

    private double pendingMouseDx = 0.0;
    private double pendingMouseDy = 0.0;

    private long lastFrameTimeNs = 0L;

    private double pendingCorrectionX = 0.0;
    private double pendingCorrectionY = 0.0;
    private double pendingCorrectionZ = 0.0;

    private ClientMovementTracker() {
        this.client = MinecraftClient.getInstance();
    }

    public static ClientMovementTracker getInstance() {
        if (instance == null) {
            instance = new ClientMovementTracker();
        }
        return instance;
    }

    public boolean isPredictionEnabled() {
        return predictionEnabled;
    }

    public void setPredictionMode(boolean enabled, String controllerId, PlayerPhysicsConfig configOverride) {
        String resolvedControllerId = controllerId != null && !controllerId.isBlank()
                ? controllerId
                : PlayerPhysicsControllers.DEFAULT_ID;
        requestedPredictionEnabled = enabled;
        requestedControllerId = resolvedControllerId;
        requestedConfig = configOverride != null ? configOverride : PlayerPhysicsConfig.defaults();

        boolean known = PlayerPhysicsControllers.has(resolvedControllerId);
        boolean canEnable = enabled && known;
        if (enabled && !known) {
            LOGGER.warn(
                    "Server requested unknown player physics controller '{}'; prediction disabled",
                    resolvedControllerId
            );
        }

        predictionEnabled = canEnable;
        config = requestedConfig;
        this.controllerId = resolvedControllerId;
        this.controller = PlayerPhysicsControllers.get(resolvedControllerId);
        resetPredictionState();
    }

    public void tryEnablePendingPrediction() {
        if (predictionEnabled) {
            return;
        }
        if (!requestedPredictionEnabled) {
            return;
        }
        if (!PlayerPhysicsControllers.has(requestedControllerId)) {
            return;
        }

        predictionEnabled = true;
        controllerId = requestedControllerId;
        config = requestedConfig != null ? requestedConfig : PlayerPhysicsConfig.defaults();
        controller = PlayerPhysicsControllers.get(requestedControllerId);
        resetPredictionState();
        LOGGER.info("Prediction controller '{}' registered; prediction enabled", requestedControllerId);
    }

    public void handleSnapshot(MoudPackets.PlayerSnapshotPacket snapshot) {
        if (!predictionEnabled || snapshot == null) {
            return;
        }
        if (client.player == null || client.world == null) {
            return;
        }
        if (snapshot.lastProcessedSeq() <= lastServerAck) {
            return;
        }

        long ack = snapshot.lastProcessedSeq();
        lastServerAck = ack;
        dropAckedInputs(ack);

        PlayerState serverState = new PlayerState(
                snapshot.x(),
                snapshot.y(),
                snapshot.z(),
                snapshot.velX(),
                snapshot.velY(),
                snapshot.velZ(),
                snapshot.onGround(),
                false
        );

        PlayerState replayed = replay(serverState);
        if (state == null) {
            state = replayed;
            clearPendingCorrection();
            applyToPlayer(client.player, state);
            return;
        }

        double dx = replayed.x() - state.x();
        double dy = replayed.y() - state.y();
        double dz = replayed.z() - state.z();
        double distSq = dx * dx + dy * dy + dz * dz;

        double effectiveEpsilonSq = isEffectivelyIdle() ? IDLE_RECONCILE_EPSILON_SQ : RECONCILE_EPSILON_SQ;

        if (distSq > SNAP_DISTANCE_SQ) {
            state = replayed;
            pendingInputs.clear();
            clearPendingCorrection();
            applyToPlayer(client.player, state);
            return;
        }

        if (distSq <= effectiveEpsilonSq) {
            clearPendingCorrection();
            return;
        }
        if (distSq <= SMOOTH_CORRECTION_MAX_SQ) {
            pendingCorrectionX = dx;
            pendingCorrectionY = dy;
            pendingCorrectionZ = dz;
            state = new PlayerState(
                    state.x(), state.y(), state.z(),
                    replayed.velX(), replayed.velY(), replayed.velZ(),
                    replayed.onGround(),
                    replayed.collidingHorizontally()
            );
        } else {
            state = replayed;
            clearPendingCorrection();
            applyToPlayer(client.player, state);
        }
    }

    public void frameTick() {
        if (client.player == null || client.options == null || client.world == null) {
            return;
        }
        if (!predictionEnabled) {
            return;
        }

        long now = System.nanoTime();
        if (lastFrameTimeNs == 0L) {
            lastFrameTimeNs = now;
            return;
        }
        float frameDt = (now - lastFrameTimeNs) / 1_000_000_000f;
        lastFrameTimeNs = now;

        if (frameDt > MAX_FRAME_DT) {
            frameDt = MAX_FRAME_DT;
        }
        if (frameDt < 0.001f) {
            return;
        }

        PlayerEntity player = client.player;
        GameOptions options = client.options;

        boolean forward = options.forwardKey.isPressed();
        boolean backward = options.backKey.isPressed();
        boolean left = options.leftKey.isPressed();
        boolean right = options.rightKey.isPressed();
        boolean jumping = options.jumpKey.isPressed();
        boolean sneaking = options.sneakKey.isPressed();
        boolean sprinting = options.sprintKey.isPressed();
        float yaw = player.getYaw();
        float pitch = player.getPitch();

        if (state == null) {
            Vec3d vel = player.getVelocity();
            state = new PlayerState(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    (float) vel.x,
                    (float) vel.y,
                    (float) vel.z,
                    player.isOnGround(),
                    false
            );
        }

        applyPendingCorrection(frameDt);
        state = applyMeshGroundProbeToState(state);

        PlayerInput frameInput = new PlayerInput(
                0L,
                forward,
                backward,
                left,
                right,
                jumping,
                sprinting,
                sneaking,
                yaw,
                pitch
        );

        CollisionWorld world = new ClientCollisionWorld(client);
        PlayerState physicsState;
        try {
            physicsState = controller.step(state, frameInput, config, world, frameDt);
        } catch (Throwable t) {
            LOGGER.warn("Prediction controller '{}' failed; disabling prediction", controllerId, t);
            predictionEnabled = false;
            controllerId = PlayerPhysicsControllers.DEFAULT_ID;
            controller = PlayerPhysicsControllers.get(PlayerPhysicsControllers.DEFAULT_ID);
            config = PlayerPhysicsConfig.defaults();
            resetPredictionState();
            return;
        }
        state = applyMeshCollisionToState(state, physicsState);

        double oldX = player.getX();
        double oldY = player.getY();
        double oldZ = player.getZ();

        player.setPos(state.x(), state.y(), state.z());
        player.prevX = state.x();
        player.prevY = state.y();
        player.prevZ = state.z();
        player.lastRenderX = state.x();
        player.lastRenderY = state.y();
        player.lastRenderZ = state.z();
        player.setVelocity(state.velX(), state.velY(), state.velZ());
        player.setOnGround(state.onGround());

        float dx = (float)(state.x() - oldX);
        float dz = (float)(state.z() - oldZ);
        float horizontalSpeed = (float)Math.sqrt(dx * dx + dz * dz);
        player.limbAnimator.updateLimbs(horizontalSpeed * 4.0f, 1.0f);
    }

    public void tick() {
        if (client.player == null || client.options == null) {
            return;
        }

        PlayerEntity player = client.player;
        GameOptions options = client.options;

        boolean forward = options.forwardKey.isPressed();
        boolean backward = options.backKey.isPressed();
        boolean left = options.leftKey.isPressed();
        boolean right = options.rightKey.isPressed();
        boolean jumping = options.jumpKey.isPressed();
        boolean sneaking = options.sneakKey.isPressed();
        boolean sprinting = options.sprintKey.isPressed();

        if (predictionEnabled && client.world != null) {
            long seq = nextSequenceId++;
            int bits = encodeInputBits(forward, backward, left, right, jumping, sneaking, sprinting);
            float yaw = player.getYaw();
            float pitch = player.getPitch();

            PlayerInput input = new PlayerInput(
                    seq,
                    forward,
                    backward,
                    left,
                    right,
                    jumping,
                    sprinting,
                    sneaking,
                    yaw,
                    pitch
            );

            pendingInputs.addLast(input);
            while (pendingInputs.size() > MAX_HISTORY) {
                pendingInputs.removeFirst();
            }

            ClientPacketWrapper.sendToServer(new MoudPackets.PlayerInputPacket(seq, yaw, pitch, bits));
            flushMouseDelta();

            boolean onGround = state != null && state.onGround();
            float speed = state != null ? (float) Math.hypot(state.velX(), state.velZ()) : 0f;
            maybeSendMovementState(forward, backward, left, right, jumping, sneaking, sprinting, onGround, speed);
            return;
        }

        boolean onGround = player.isOnGround();
        float speed = (float) player.getVelocity().horizontalLength();
        maybeSendMovementState(forward, backward, left, right, jumping, sneaking, sprinting, onGround, speed);
        flushMouseDelta();
    }

    public void queueMouseDelta(double dx, double dy) {
        if (Double.isFinite(dx)) {
            pendingMouseDx += dx;
        }
        if (Double.isFinite(dy)) {
            pendingMouseDy += dy;
        }
    }

    private void flushMouseDelta() {
        double dx = pendingMouseDx;
        double dy = pendingMouseDy;
        pendingMouseDx = 0.0;
        pendingMouseDy = 0.0;

        if (Math.abs(dx) <= 0.01 && Math.abs(dy) <= 0.01) {
            return;
        }
        ClientPacketWrapper.sendToServer(new MoudPackets.MouseMovementPacket((float) dx, (float) dy));
    }

    public void reset() {
        predictionEnabled = false;
        requestedPredictionEnabled = false;
        requestedControllerId = PlayerPhysicsControllers.DEFAULT_ID;
        requestedConfig = PlayerPhysicsConfig.defaults();
        resetPredictionState();

        lastForward = false;
        lastBackward = false;
        lastLeft = false;
        lastRight = false;
        lastJumping = false;
        lastSneaking = false;
        lastSprinting = false;
        lastOnGround = false;
        pendingMouseDx = 0.0;
        pendingMouseDy = 0.0;
    }

    private void resetPredictionState() {
        state = null;
        nextSequenceId = 1L;
        lastServerAck = 0L;
        pendingInputs.clear();
        lastFrameTimeNs = 0L;
        clearPendingCorrection();
    }

    private void clearPendingCorrection() {
        pendingCorrectionX = 0.0;
        pendingCorrectionY = 0.0;
        pendingCorrectionZ = 0.0;
    }

    private boolean isEffectivelyIdle() {
        if (client == null || client.options == null) {
            return false;
        }
        if (state == null) {
            return false;
        }

        GameOptions options = client.options;
        boolean wantsMovement = options.forwardKey.isPressed()
                || options.backKey.isPressed()
                || options.leftKey.isPressed()
                || options.rightKey.isPressed()
                || options.jumpKey.isPressed();

        if (wantsMovement) {
            return false;
        }

        if (!state.onGround()) {
            return false;
        }

        float velX = state.velX();
        float velY = state.velY();
        float velZ = state.velZ();
        float horizontalSpeedSq = velX * velX + velZ * velZ;

        return horizontalSpeedSq < 1.0f && Math.abs(velY) < 0.25f;
    }

    private void applyPendingCorrection(float frameDt) {
        if (state == null) {
            return;
        }

        double absX = Math.abs(pendingCorrectionX);
        double absY = Math.abs(pendingCorrectionY);
        double absZ = Math.abs(pendingCorrectionZ);
        if (absX < CORRECTION_EPS && absY < CORRECTION_EPS && absZ < CORRECTION_EPS) {
            clearPendingCorrection();
            return;
        }

        float clampedDt = Math.max(0.0f, Math.min(frameDt, MAX_FRAME_DT));
        double t = clampedDt / FIXED_DT_SECONDS;
        double alpha = 1.0 - Math.pow(1.0 - SMOOTH_CORRECTION_RATE, t);
        if (alpha <= 0.0) {
            return;
        }

        double moveX = pendingCorrectionX * alpha;
        double moveY = pendingCorrectionY * alpha;
        double moveZ = pendingCorrectionZ * alpha;

        PlayerState before = state;

        CollisionWorld world = new ClientCollisionWorld(client);
        PlayerState afterBlocks = PlayerController.applyTranslation(before, config, world, moveX, moveY, moveZ);
        PlayerState afterMeshes = applyMeshCollisionToTranslation(before, afterBlocks);
        state = afterMeshes;

        double appliedX = state.x() - before.x();
        double appliedY = state.y() - before.y();
        double appliedZ = state.z() - before.z();

        pendingCorrectionX -= appliedX;
        pendingCorrectionY -= appliedY;
        pendingCorrectionZ -= appliedZ;
    }
    private PlayerState applyMeshCollisionToTranslation(PlayerState prevState, PlayerState translatedState) {
        double moveX = translatedState.x() - prevState.x();
        double moveY = translatedState.y() - prevState.y();
        double moveZ = translatedState.z() - prevState.z();

        ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
        if (physics.isInitialized() && physics.hasStaticBodies()) {
            float width = config.width() > 0 ? config.width() : 0.6f;
            float height = config.height() > 0 ? config.height() : 1.8f;
            float stepHeight = config.stepHeight();
            boolean allowStep = prevState.onGround() && moveY <= 0.02;

            Vector3f currentFeet = new Vector3f((float) prevState.x(), (float) prevState.y(), (float) prevState.z());
            Vector3f desired = new Vector3f((float) moveX, (float) moveY, (float) moveZ);
            Vector3f actual = physics.movePlayer(currentFeet, desired, FIXED_DT_SECONDS, width, height, stepHeight, allowStep);

            double finalX = prevState.x() + actual.x;
            double finalY = prevState.y() + actual.y;
            double finalZ = prevState.z() + actual.z;

            boolean blockedY = axisBlocked(moveY, actual.y);
            boolean onGround = translatedState.onGround();
            boolean landedOnMesh = !onGround && moveY < 0 && blockedY;
            if (landedOnMesh) {
                onGround = true;
            }

            float velY = translatedState.velY();
            if (velY > 0.02f) {
                onGround = false;
            }
            if (onGround && velY < 0.0f) {
                velY = 0.0f;
            }
            boolean horizontalCollision = axisBlocked(moveX, actual.x) || axisBlocked(moveZ, actual.z);

            return new PlayerState(
                    finalX, finalY, finalZ,
                    translatedState.velX(), velY, translatedState.velZ(),
                    onGround,
                    translatedState.collidingHorizontally() || horizontalCollision
            );
        }

        if (Math.abs(moveX) < 1e-9 && Math.abs(moveY) < 1e-9 && Math.abs(moveZ) < 1e-9) {
            return translatedState;
        }

        double halfWidth = config.width() > 0 ? config.width() * 0.5 : 0.3;
        double height = config.height() > 0 ? config.height() : 1.8;
        Box playerBox = new Box(
                prevState.x() - halfWidth, prevState.y(), prevState.z() - halfWidth,
                prevState.x() + halfWidth, prevState.y() + height, prevState.z() + halfWidth
        );

        Vec3d movement = new Vec3d(moveX, moveY, moveZ);
        Box queryBox = playerBox.union(playerBox.offset(movement)).expand(0.5);
        List<CollisionMesh> meshes = new ArrayList<>();
        meshes.addAll(ClientCollisionManager.getMeshesNear(queryBox));

        if (meshes.isEmpty()) {
            return translatedState;
        }

        Vec3d finalMovement = movement;
        boolean horizontalCollision = false;
        float stepHeight = prevState.onGround() ? config.stepHeight() : 0.0f;
        for (CollisionMesh mesh : meshes) {
            Vec3d before = finalMovement;
            CollisionResult result = MeshCollider.collideWithStepUp(playerBox, finalMovement, mesh, stepHeight);
            finalMovement = result.allowedMovement();
            horizontalCollision |= axisBlocked(before.x, finalMovement.x) || axisBlocked(before.z, finalMovement.z);
        }

        double finalX = prevState.x() + finalMovement.x;
        double finalY = prevState.y() + finalMovement.y;
        double finalZ = prevState.z() + finalMovement.z;

        boolean onGround = translatedState.onGround();
        boolean landedOnMesh = !onGround && moveY < 0 && axisBlocked(moveY, finalMovement.y);
        if (landedOnMesh) {
            onGround = true;
        }

        return new PlayerState(
                finalX, finalY, finalZ,
                translatedState.velX(), translatedState.velY(), translatedState.velZ(),
                onGround,
                translatedState.collidingHorizontally() || horizontalCollision
        );
    }

    private void dropAckedInputs(long ackSeq) {
        while (!pendingInputs.isEmpty() && pendingInputs.peekFirst().sequenceId() <= ackSeq) {
            pendingInputs.removeFirst();
        }
    }

    private PlayerState replay(PlayerState serverState) {
        PlayerState replayed = serverState;
        CollisionWorld world = new ClientCollisionWorld(client);
        for (PlayerInput input : pendingInputs) {
            replayed = applyMeshGroundProbeToState(replayed);
            PlayerState physicsState = controller.step(replayed, input, config, world, FIXED_DT_SECONDS);
            replayed = applyMeshCollisionToState(replayed, physicsState);
        }
        return replayed;
    }

    private PlayerState applyMeshCollisionToState(PlayerState prevState, PlayerState physicsState) {
        double moveX = physicsState.x() - prevState.x();
        double moveY = physicsState.y() - prevState.y();
        double moveZ = physicsState.z() - prevState.z();

        ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
        if (physics.isInitialized() && physics.hasStaticBodies()) {
            float width = config.width() > 0 ? config.width() : 0.6f;
            float height = config.height() > 0 ? config.height() : 1.8f;
            float stepHeight = config.stepHeight();
            boolean allowStep = prevState.onGround() && physicsState.velY() <= 0.02f;

            Vector3f currentFeet = new Vector3f((float) prevState.x(), (float) prevState.y(), (float) prevState.z());
            Vector3f desired = new Vector3f((float) moveX, (float) moveY, (float) moveZ);
            Vector3f actual = physics.movePlayer(currentFeet, desired, FIXED_DT_SECONDS, width, height, stepHeight, allowStep);

            double finalX = prevState.x() + actual.x;
            double finalY = prevState.y() + actual.y;
            double finalZ = prevState.z() + actual.z;

            boolean blockedX = axisBlocked(moveX, actual.x);
            boolean blockedY = axisBlocked(moveY, actual.y);
            boolean blockedZ = axisBlocked(moveZ, actual.z);

            float finalVelX = physicsState.velX();
            float finalVelY = physicsState.velY();
            float finalVelZ = physicsState.velZ();

            if (blockedX) finalVelX = 0;
            if (blockedY) finalVelY = 0;
            if (blockedZ) finalVelZ = 0;

            boolean onGround = physicsState.onGround();
            boolean landedOnMesh = !onGround && moveY < 0 && blockedY;
            if (landedOnMesh) {
                onGround = true;
            }
            if (finalVelY > 0.02f) {
                onGround = false;
            }
            if (onGround && finalVelY < 0.0f) {
                finalVelY = 0.0f;
            }
            boolean horizontalCollision = blockedX || blockedZ;

            return new PlayerState(
                    finalX, finalY, finalZ,
                    finalVelX, finalVelY, finalVelZ,
                    onGround,
                    physicsState.collidingHorizontally() || horizontalCollision
            );
        }

        if (Math.abs(moveX) < 1e-9 && Math.abs(moveY) < 1e-9 && Math.abs(moveZ) < 1e-9) {
            return physicsState;
        }

        double halfWidth = config.width() > 0 ? config.width() * 0.5 : 0.3;
        double height = config.height() > 0 ? config.height() : 1.8;
        Box playerBox = new Box(
                prevState.x() - halfWidth, prevState.y(), prevState.z() - halfWidth,
                prevState.x() + halfWidth, prevState.y() + height, prevState.z() + halfWidth
        );

        Vec3d movement = new Vec3d(moveX, moveY, moveZ);
        Box queryBox = playerBox.union(playerBox.offset(movement)).expand(0.5);
        List<CollisionMesh> meshes = new ArrayList<>();
        meshes.addAll(ClientCollisionManager.getMeshesNear(queryBox));

        if (meshes.isEmpty()) {
            return physicsState;
        }

        Vec3d finalMovement = movement;
        boolean horizontalCollision = false;
        float stepHeight = prevState.onGround() ? config.stepHeight() : 0.0f;
        for (CollisionMesh mesh : meshes) {
            Vec3d before = finalMovement;
            CollisionResult result = MeshCollider.collideWithStepUp(
                    playerBox,
                    finalMovement,
                    mesh,
                    stepHeight
            );
            finalMovement = result.allowedMovement();
            horizontalCollision |= axisBlocked(before.x, finalMovement.x) || axisBlocked(before.z, finalMovement.z);
        }

        double finalX = prevState.x() + finalMovement.x;
        double finalY = prevState.y() + finalMovement.y;
        double finalZ = prevState.z() + finalMovement.z;

        float finalVelX = physicsState.velX();
        float finalVelY = physicsState.velY();
        float finalVelZ = physicsState.velZ();

        if (axisBlocked(moveX, finalMovement.x)) finalVelX = 0;
        if (axisBlocked(moveY, finalMovement.y)) finalVelY = 0;
        if (axisBlocked(moveZ, finalMovement.z)) finalVelZ = 0;

        boolean onGround = physicsState.onGround();
        boolean landedOnMesh = !onGround && moveY < 0 && axisBlocked(moveY, finalMovement.y);
        if (landedOnMesh) {
            onGround = true;
        }

        return new PlayerState(
                finalX, finalY, finalZ,
                finalVelX, finalVelY, finalVelZ,
                onGround,
                physicsState.collidingHorizontally() || horizontalCollision
        );
    }

    private PlayerState applyMeshGroundProbeToState(PlayerState state) {
        if (state == null || config == null) {
            return state;
        }
        if (state.onGround()) {
            return state;
        }
        if (state.velY() > 0.02f) {
            return state;
        }

        ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
        if (physics.isInitialized() && physics.hasStaticBodies()) {
            float width = config.width() > 0 ? config.width() : 0.6f;
            float height = config.height() > 0 ? config.height() : 1.8f;

            Vector3f currentFeet = new Vector3f((float) state.x(), (float) state.y(), (float) state.z());
            Vector3f desired = new Vector3f(0.0f, (float) -MESH_GROUND_PROBE, 0.0f);
            Vector3f actual = physics.movePlayer(currentFeet, desired, FIXED_DT_SECONDS, width, height, 0.0f, false);

            boolean grounded = axisBlocked(desired.y, actual.y);
            if (!grounded && physics.isPlayerOnGround()) {
                grounded = Math.abs(actual.x) > 1.0e-6f || Math.abs(actual.z) > 1.0e-6f;
            }

            if (grounded) {
                float velY = state.velY();
                if (velY < 0.0f) {
                    velY = 0.0f;
                }
                return new PlayerState(
                        state.x(), state.y(), state.z(),
                        state.velX(), velY, state.velZ(),
                        true,
                        state.collidingHorizontally()
                );
            }
        }

        double halfWidth = config.width() > 0 ? config.width() * 0.5 : 0.3;
        double height = config.height() > 0 ? config.height() : 1.8;
        Box playerBox = new Box(
                state.x() - halfWidth, state.y(), state.z() - halfWidth,
                state.x() + halfWidth, state.y() + height, state.z() + halfWidth
        );

        Vec3d probe = new Vec3d(0.0, -MESH_GROUND_PROBE, 0.0);
        Box queryBox = playerBox.union(playerBox.offset(probe)).expand(0.5);
        List<CollisionMesh> meshes = new ArrayList<>();
        meshes.addAll(ClientCollisionManager.getMeshesNear(queryBox));
        if (meshes.isEmpty()) {
            return state;
        }

        Vec3d allowed = probe;
        for (CollisionMesh mesh : meshes) {
            CollisionResult result = MeshCollider.collideWithStepUp(playerBox, allowed, mesh, 0.0f);
            allowed = result.allowedMovement();
        }

        if (!axisBlocked(probe.y, allowed.y)) {
            return state;
        }

        float velY = state.velY();
        if (velY < 0.0f) {
            velY = 0.0f;
        }

        return new PlayerState(
                state.x(), state.y(), state.z(),
                state.velX(), velY, state.velZ(),
                true,
                state.collidingHorizontally()
        );
    }

    private static int encodeInputBits(boolean forward, boolean backward, boolean left, boolean right,
                                       boolean jump, boolean sneak, boolean sprint) {
        int bits = 0;
        if (forward) bits |= InputBits.FORWARD;
        if (backward) bits |= InputBits.BACKWARD;
        if (left) bits |= InputBits.LEFT;
        if (right) bits |= InputBits.RIGHT;
        if (jump) bits |= InputBits.JUMP;
        if (sneak) bits |= InputBits.SNEAK;
        if (sprint) bits |= InputBits.SPRINT;
        return bits;
    }

    private static boolean axisBlocked(double requested, double allowed) {
        if (Math.abs(requested) <= 1e-9) {
            return false;
        }
        double diff = allowed - requested;
        if (Math.abs(diff) <= 1e-6) {
            return false;
        }
        if (allowed * requested < -1e-9) {
            return true;
        }
        return Math.signum(allowed) == Math.signum(requested) && Math.abs(allowed) < Math.abs(requested) - 1e-6;
    }

    private void applyToPlayer(PlayerEntity player, PlayerState state) {
        if (player == null || state == null) {
            return;
        }
        player.setPos(state.x(), state.y(), state.z());
        player.prevX = state.x();
        player.prevY = state.y();
        player.prevZ = state.z();
        player.lastRenderX = state.x();
        player.lastRenderY = state.y();
        player.lastRenderZ = state.z();
        player.setVelocity(state.velX(), state.velY(), state.velZ());
    }

    private void maybeSendMovementState(boolean forward, boolean backward, boolean left, boolean right,
                                        boolean jumping, boolean sneaking, boolean sprinting,
                                        boolean onGround, float speed) {
        boolean anyMovementKey = forward || backward || left || right || jumping;
        boolean stateChanged = forward != lastForward
                || backward != lastBackward
                || left != lastLeft
                || right != lastRight
                || jumping != lastJumping
                || sneaking != lastSneaking
                || sprinting != lastSprinting
                || onGround != lastOnGround;

        if (!anyMovementKey && !stateChanged) {
            return;
        }

        MoudPackets.MovementStatePacket packet = new MoudPackets.MovementStatePacket(
                forward, backward, left, right, jumping, sneaking, sprinting, onGround, speed
        );
        ClientPacketWrapper.sendToServer(packet);

        lastForward = forward;
        lastBackward = backward;
        lastLeft = left;
        lastRight = right;
        lastJumping = jumping;
        lastSneaking = sneaking;
        lastSprinting = sprinting;
        lastOnGround = onGround;
    }

    private static final class InputBits {
        private static final int FORWARD = 1 << 0;
        private static final int BACKWARD = 1 << 1;
        private static final int LEFT = 1 << 2;
        private static final int RIGHT = 1 << 3;
        private static final int JUMP = 1 << 4;
        private static final int SNEAK = 1 << 5;
        private static final int SPRINT = 1 << 6;

        private InputBits() {
        }
    }

    private static final class ClientCollisionWorld implements CollisionWorld {
        private static final double COLLISION_QUERY_EPS = 1.0e-9;

        private final MinecraftClient client;

        private ClientCollisionWorld(MinecraftClient client) {
            this.client = client;
        }

        @Override
        public List<AABB> getCollisions(AABB query) {
            if (query == null || client == null) {
                return List.of();
            }
            ClientWorld world = client.world;
            if (world == null) {
                return List.of();
            }

            int minX = (int) Math.floor(query.minX());
            int minY = (int) Math.floor(query.minY());
            int minZ = (int) Math.floor(query.minZ());

            int maxX = (int) Math.floor(query.maxX() - COLLISION_QUERY_EPS);
            int maxY = (int) Math.floor(query.maxY() - COLLISION_QUERY_EPS);
            int maxZ = (int) Math.floor(query.maxZ() - COLLISION_QUERY_EPS);

            int bottomY = world.getBottomY();
            int topY = world.getTopY() - 1;
            minY = Math.max(minY, bottomY);
            maxY = Math.min(maxY, topY);

            List<AABB> colliders = new ArrayList<>();
            BlockPos.Mutable pos = new BlockPos.Mutable();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        pos.set(x, y, z);
                        var state = world.getBlockState(pos);
                        if (state.isAir() || !state.getFluidState().isEmpty()) {
                            continue;
                        }
                        VoxelShape shape = state.getCollisionShape(world, pos);
                        if (shape == null || shape.isEmpty()) {
                            continue;
                        }
                        Box bb = shape.getBoundingBox();
                        if ((bb.maxX - bb.minX) <= 0.0 || (bb.maxY - bb.minY) <= 0.0 || (bb.maxZ - bb.minZ) <= 0.0) {
                            continue;
                        }
                        colliders.add(new AABB(
                                x + bb.minX,
                                y + bb.minY,
                                z + bb.minZ,
                                x + bb.maxX,
                                y + bb.maxY,
                                z + bb.maxZ
                        ));
                    }
                }
            }

            Box region = new Box(query.minX(), query.minY(), query.minZ(), query.maxX(), query.maxY(), query.maxZ());
            for (Box bounds : ModelCollisionManager.getInstance().collectBounds(region)) {
                colliders.add(new AABB(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ));
            }

            for (var primitive : ClientPrimitiveManager.getInstance().getPrimitives()) {
                AABB bounds = ClientPrimitiveCollisionBounds.computeAabb(primitive);
                if (bounds != null && bounds.intersects(query)) {
                    colliders.add(bounds);
                }
            }

            if (colliders.size() <= 1) {
                return colliders;
            }
            colliders.sort(Comparator.comparingDouble(AABB::minY));
            return colliders;
        }
    }
}
