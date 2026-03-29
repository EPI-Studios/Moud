package com.moud.client.fabric.platform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MinecraftRenderBridge {
    private MinecraftRenderBridge() {
    }

    public static Vector3f cameraPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return null;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return null;
        }
        Vec3d pos = camera.getPos();
        return new Vector3f((float) pos.x, (float) pos.y, (float) pos.z);
    }

    public static Matrix4f viewProjection(float fovDeg, float aspect, Vector3f focusWorld) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return null;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return null;
        }

        Vec3d pos = camera.getPos();
        Matrix4f proj = new Matrix4f()
                .perspective((float) Math.toRadians(fovDeg), Math.max(0.01f, aspect), 0.05f, 512.0f);

        Quaternionf q = new Quaternionf(camera.getRotation()).conjugate();
        return new Matrix4f(proj).mul(new Matrix4f()
                .rotate(q)
                .translate((float) -pos.x, (float) -pos.y, (float) -pos.z));
    }
}
