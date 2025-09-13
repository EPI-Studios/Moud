package com.moud.client.player;

import com.moud.api.math.Vector3;
import com.moud.client.network.MoudPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientCameraManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCameraManager.class);
    private final MinecraftClient client;
    private long lastSendTime = 0;
    private static final long SEND_INTERVAL_MS = 50;

    public ClientCameraManager() {
        this.client = MinecraftClient.getInstance();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastSendTime > SEND_INTERVAL_MS) {
            if (client.player != null) {
                Vec3d lookVector = client.player.getRotationVector();
                Vector3 direction = new Vector3((float)lookVector.x, (float)lookVector.y, (float)lookVector.z);

                ClientPlayNetworking.send(new MoudPackets.ClientUpdateCameraPacket(direction));
                lastSendTime = now;
            }
        }
    }
}