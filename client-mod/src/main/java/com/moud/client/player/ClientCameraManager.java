package com.moud.client.player;

import com.moud.api.math.Vector3;
import com.moud.client.MoudClientMod;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class ClientCameraManager {
    private final MinecraftClient client;
    private long lastSendTime = 0;
    private static final long SEND_INTERVAL_MS = 50;

    public ClientCameraManager() {
        this.client = MinecraftClient.getInstance();
    }

    public void tick() {
        if (client.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastSendTime > SEND_INTERVAL_MS) {
            Vector3 direction = getCameraDirectionVector();

            ClientPacketWrapper.sendToServer(new MoudPackets.ClientUpdateCameraPacket(direction));
            lastSendTime = now;
        }
    }

    private Vector3 getCameraDirectionVector() {
        Camera camera = client.gameRenderer.getCamera();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        float yawRad = (float) Math.toRadians(yaw + 90.0f);
        float pitchRad = (float) Math.toRadians(-pitch);

        float x = (float) (Math.cos(pitchRad) * Math.cos(yawRad));
        float y = (float) Math.sin(pitchRad);
        float z = (float) (Math.cos(pitchRad) * Math.sin(yawRad));

        return new Vector3(x, y, z).normalize();
    }
}