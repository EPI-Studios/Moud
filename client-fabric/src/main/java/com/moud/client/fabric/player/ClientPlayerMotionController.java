package com.moud.client.fabric.player;

import com.moud.client.fabric.net.ClientSessionBus;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.net.protocol.PlayerMotion;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.Vec3d;

public final class ClientPlayerMotionController {
    private static boolean anchorActive;
    private static float anchorX;
    private static float anchorY;
    private static float anchorZ;
    private static float anchorYawDeg;

    private static boolean serverPosActive;
    private static float serverX;
    private static float serverY;
    private static float serverZ;

    private ClientPlayerMotionController() {
    }

    public static void onPlayerMotion(PlayerMotion msg) {
        if (msg == null) return;
        switch (msg.mode()) {
            case PlayerMotion.MODE_VELOCITY -> applyVelocity(msg.x(), msg.y(), msg.z());
            case PlayerMotion.MODE_ANCHOR_SET -> setAnchor(msg.x(), msg.y(), msg.z(), msg.yawDeg());
            case PlayerMotion.MODE_ANCHOR_CLEAR -> clearAnchor();
            case PlayerMotion.MODE_POSITION -> applyPosition(msg.x(), msg.y(), msg.z(), msg.yawDeg());
            default -> {
            }
        }
    }

    private static boolean isClientCharacterBodyActive() {
        PlayRuntimeClient rt = PlayRuntimeBus.get();
        return rt != null && rt.isCharacterBodyDrivingMovement();
    }

    private static void applyVelocity(float vxPerSec, float vyPerSec, float vzPerSec) {
        if (isClientCharacterBodyActive()) {
            serverPosActive = false;
            return;
        }
        ClientPlayerEntity player = player();
        if (player == null) return;
        double inv = 1.0 / 20.0;
        player.setVelocity(vxPerSec * inv, vyPerSec * inv, vzPerSec * inv);
        anchorActive = false;
    }

    private static void applyPosition(float x, float y, float z, float yawDeg) {
        if (isClientCharacterBodyActive()) {
            serverPosActive = false;
            return;
        }
        ClientPlayerEntity player = player();
        if (player == null) return;
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        player.setPosition(x, y, z);
        player.prevX = x;
        player.prevY = y;
        player.prevZ = z;
        player.setVelocity(0, 0, 0);
        player.setOnGround(true);
        serverPosActive = true;
        serverX = x;
        serverY = y;
        serverZ = z;
        anchorActive = false;
    }

    private static void setAnchor(float x, float y, float z, float yawDeg) {
        anchorActive = true;
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        anchorYawDeg = yawDeg;
        ClientPlayerEntity player = player();
        if (player != null) {
            player.setPosition(x, y, z);
            player.setYaw(yawDeg);
            player.setVelocity(0, 0, 0);
        }
    }

    private static void clearAnchor() {
        anchorActive = false;
    }

    public static boolean isAnchored() {
        return anchorActive;
    }

    public static void clientTick(MinecraftClient client) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (isClientCharacterBodyActive()) {
            serverPosActive = false;
        }
        if (player != null) {
            reportVelocity(player);
        }

        if (serverPosActive && player != null && !isClientCharacterBodyActive()) {
            player.setPosition(serverX, serverY, serverZ);
            player.setOnGround(true);
        }

        if (!anchorActive) return;
        if (player == null) return;

        if (client.currentScreen != null) return;

        player.setPosition(anchorX, anchorY, anchorZ);
        player.setYaw(anchorYawDeg);
        player.setVelocity(0, 0, 0);
        GameOptions opt = client.options;
        if (opt != null) {
            opt.forwardKey.setPressed(false);
            opt.backKey.setPressed(false);
            opt.leftKey.setPressed(false);
            opt.rightKey.setPressed(false);
            opt.jumpKey.setPressed(false);
            opt.sneakKey.setPressed(false);
            opt.sprintKey.setPressed(false);
        }
    }

    public static void reset() {
        anchorActive = false;
        serverPosActive = false;
    }

    private static void reportVelocity(ClientPlayerEntity player) {
        if (isClientCharacterBodyActive()) return;
        Session session = ClientSessionBus.get();
        if (session == null || session.state() != SessionState.CONNECTED) return;
        Vec3d v = player.getVelocity();
        float vx = (float) (v.x * 20.0);
        float vy = (float) (v.y * 20.0);
        float vz = (float) (v.z * 20.0);
        session.send(Lane.INPUT, PlayerMotion.velocityReport(vx, vy, vz));
    }

    private static ClientPlayerEntity player() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null ? null : mc.player;
    }
}
