package com.moud.client.movement;

import com.moud.client.network.ClientPacketWrapper;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.player.PlayerEntity;

public class ClientMovementTracker {
    private static ClientMovementTracker instance;
    private final MinecraftClient client;

    private boolean lastForward = false;
    private boolean lastBackward = false;
    private boolean lastLeft = false;
    private boolean lastRight = false;
    private boolean lastJumping = false;
    private boolean lastSneaking = false;
    private boolean lastSprinting = false;
    private boolean lastOnGround = false;
    private float lastSpeed = 0.0f;

    private int tickCounter = 0;
    private static final int UPDATE_INTERVAL = 5;

    private ClientMovementTracker() {
        this.client = MinecraftClient.getInstance();
    }

    public static ClientMovementTracker getInstance() {
        if (instance == null) {
            instance = new ClientMovementTracker();
        }
        return instance;
    }

    public void tick() {
        if (client.player == null) return;

        tickCounter++;
        if (tickCounter < UPDATE_INTERVAL) return;
        tickCounter = 0;

        PlayerEntity player = client.player;
        GameOptions options = client.options;

        boolean forward = options.forwardKey.isPressed();
        boolean backward = options.backKey.isPressed();
        boolean left = options.leftKey.isPressed();
        boolean right = options.rightKey.isPressed();
        boolean jumping = options.jumpKey.isPressed();
        boolean sneaking = options.sneakKey.isPressed();
        boolean sprinting = player.isSprinting();
        boolean onGround = player.isOnGround();
        float speed = (float) player.getVelocity().horizontalLength();

        if (hasStateChanged(forward, backward, left, right, jumping, sneaking, sprinting, onGround, speed)) {
            sendMovementState(forward, backward, left, right, jumping, sneaking, sprinting, onGround, speed);
            updateLastState(forward, backward, left, right, jumping, sneaking, sprinting, onGround, speed);
        }
    }

    private boolean hasStateChanged(boolean forward, boolean backward, boolean left, boolean right,
                                    boolean jumping, boolean sneaking, boolean sprinting, boolean onGround, float speed) {
        return forward != lastForward || backward != lastBackward || left != lastLeft || right != lastRight ||
                jumping != lastJumping || sneaking != lastSneaking || sprinting != lastSprinting ||
                onGround != lastOnGround || Math.abs(speed - lastSpeed) > 0.1f;
    }

    private void updateLastState(boolean forward, boolean backward, boolean left, boolean right,
                                 boolean jumping, boolean sneaking, boolean sprinting, boolean onGround, float speed) {
        lastForward = forward;
        lastBackward = backward;
        lastLeft = left;
        lastRight = right;
        lastJumping = jumping;
        lastSneaking = sneaking;
        lastSprinting = sprinting;
        lastOnGround = onGround;
        lastSpeed = speed;
    }

    private void sendMovementState(boolean forward, boolean backward, boolean left, boolean right,
                                   boolean jumping, boolean sneaking, boolean sprinting, boolean onGround, float speed) {
        MoudPackets.MovementStatePacket packet = new MoudPackets.MovementStatePacket(
                forward, backward, left, right, jumping, sneaking, sprinting, onGround, speed
        );
        ClientPacketWrapper.sendToServer(packet);
    }
}