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
            Vector3 direction;

            if (MoudClientMod.isCustomCameraActive()) {
                direction = computeMouseRayDirection();
            } else {
                Vec3d lookVector = client.player.getRotationVector();
                direction = new Vector3((float)lookVector.x, (float)lookVector.y, (float)lookVector.z);
            }

            ClientPacketWrapper.sendToServer(new MoudPackets.ClientUpdateCameraPacket(direction));
            lastSendTime = now;
        }
    }

    private Vector3 computeMouseRayDirection() {
        GameRenderer gameRenderer = client.gameRenderer;
        Camera camera = gameRenderer.getCamera();

        Matrix4f projectionMatrix = new Matrix4f(gameRenderer.getBasicProjectionMatrix(client.options.getFov().getValue()));
        Matrix4f viewMatrix = new Matrix4f(camera.getRotation());

        Matrix4f inversePV = new Matrix4f();
        projectionMatrix.mul(viewMatrix, inversePV);
        inversePV.invert();

        double mouseX = client.mouse.getX();
        double mouseY = client.mouse.getY();
        float ndcX = (float) (2.0 * mouseX / client.getWindow().getScaledWidth() - 1.0);
        float ndcY = (float) (1.0 - 2.0 * mouseY / client.getWindow().getScaledHeight());

        Vector4f screenPos = new Vector4f(ndcX, ndcY, -1.0f, 1.0f);
        Vector4f worldPos = new Vector4f();

        screenPos.mul(inversePV);
        worldPos.w = 1.0f / worldPos.w;
        worldPos.x *= worldPos.w;
        worldPos.y *= worldPos.w;
        worldPos.z *= worldPos.w;

        return new Vector3(worldPos.x(), worldPos.y(), worldPos.z()).normalize();
    }
}