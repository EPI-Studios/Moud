package com.moud.client.player;

import com.moud.api.math.Vector3;
import com.moud.client.MoudClientMod;
import com.moud.network.MoudPackets;
import com.moud.client.network.ClientPacketWrapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class ClientCameraManager {
    private final MinecraftClient client;
    private long lastSendTime = 0;
    private static final long SEND_INTERVAL_MS = 50;

    public ClientCameraManager() {
        this.client = MinecraftClient.getInstance();
    }

    public void tick() {
        if (MoudClientMod.isCustomCameraActive()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSendTime > SEND_INTERVAL_MS) {
            if (client.player != null) {
                Vec3d lookVector = client.player.getRotationVector();
                Vector3 direction = new Vector3((float)lookVector.x, (float)lookVector.y, (float)lookVector.z);

                ClientPacketWrapper.sendToServer(new MoudPackets.ClientUpdateCameraPacket(direction));
                lastSendTime = now;
            }
        }
    }
}