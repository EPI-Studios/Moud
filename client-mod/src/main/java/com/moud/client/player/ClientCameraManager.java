package com.moud.client.player;

import com.moud.api.math.Vector3;
import com.moud.client.network.MoudPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;

public class ClientCameraManager {
    private final MinecraftClient client;
    private long lastSendTime = 0;
    private static final long SEND_INTERVAL_MS = 50;

    public ClientCameraManager() {
        this.client = MinecraftClient.getInstance();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastSendTime > SEND_INTERVAL_MS) {
            if (client.player != null && ClientPlayNetworking.canSend(MoudPackets.ClientUpdateCameraPacket.ID)) {
                Camera camera = client.gameRenderer.getCamera();
                org.joml.Vector3f forward = new org.joml.Vector3f(0, 0, -1).rotate(camera.getRotation());
                Vector3 direction = new Vector3(forward.x, forward.y, forward.z);
                ClientPlayNetworking.send(new MoudPackets.ClientUpdateCameraPacket(direction));
                lastSendTime = now;
            }
        }
    }
}