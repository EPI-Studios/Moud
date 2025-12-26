package com.moud.client.movement;

import com.moud.api.collision.AABB;
import com.moud.api.physics.player.CollisionWorld;
import com.moud.api.physics.player.PlayerInput;
import com.moud.api.physics.player.PlayerPhysicsController;
import com.moud.api.physics.player.PlayerPhysicsConfig;
import com.moud.api.physics.player.PlayerPhysicsControllers;
import com.moud.api.physics.player.PlayerState;
import com.moud.client.collision.ModelCollisionManager;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
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

    private final MinecraftClient client;

    private boolean predictionEnabled = false;
    private PlayerPhysicsConfig config = PlayerPhysicsConfig.defaults();
    private String controllerId = PlayerPhysicsControllers.DEFAULT_ID;
    private PlayerPhysicsController controller = PlayerPhysicsControllers.get(PlayerPhysicsControllers.DEFAULT_ID);
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
        boolean known = PlayerPhysicsControllers.has(resolvedControllerId);
        boolean canEnable = enabled && known;
        if (enabled && !known) {
            LOGGER.warn("Server requested unknown player physics controller '{}'; prediction disabled", resolvedControllerId);
        }

        predictionEnabled = canEnable;
        config = configOverride != null ? configOverride : PlayerPhysicsConfig.defaults();
        this.controllerId = resolvedControllerId;
        this.controller = PlayerPhysicsControllers.get(resolvedControllerId);
        resetPredictionState();
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
            applyToPlayer(client.player, state);
            return;
        }

        double dx = replayed.x() - state.x();
        double dy = replayed.y() - state.y();
        double dz = replayed.z() - state.z();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > SNAP_DISTANCE_SQ) {
            state = replayed;
            pendingInputs.clear();
            applyToPlayer(client.player, state);
            return;
        }

        if (distSq > RECONCILE_EPSILON_SQ) {
            state = replayed;
            applyToPlayer(client.player, state);
        }
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

            CollisionWorld world = new ClientCollisionWorld(client);
            state = controller.step(state, input, config, world, FIXED_DT_SECONDS);
            applyToPlayer(player, state);

            boolean onGround = state.onGround();
            float speed = (float) Math.hypot(state.velX(), state.velZ());
            maybeSendMovementState(forward, backward, left, right, jumping, sneaking, sprinting, onGround, speed);
            return;
        }

        boolean onGround = player.isOnGround();
        float speed = (float) player.getVelocity().horizontalLength();
        maybeSendMovementState(forward, backward, left, right, jumping, sneaking, sprinting, onGround, speed);
    }

    public void reset() {
        predictionEnabled = false;
        resetPredictionState();

        lastForward = false;
        lastBackward = false;
        lastLeft = false;
        lastRight = false;
        lastJumping = false;
        lastSneaking = false;
        lastSprinting = false;
        lastOnGround = false;
    }

    private void resetPredictionState() {
        state = null;
        nextSequenceId = 1L;
        lastServerAck = 0L;
        pendingInputs.clear();
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
            replayed = controller.step(replayed, input, config, world, FIXED_DT_SECONDS);
        }
        return replayed;
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

    private void applyToPlayer(PlayerEntity player, PlayerState state) {
        if (player == null || state == null) {
            return;
        }
        player.setPos(state.x(), state.y(), state.z());
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

            if (colliders.size() <= 1) {
                return colliders;
            }
            colliders.sort(Comparator.comparingDouble(AABB::minY));
            return colliders;
        }
    }
}
