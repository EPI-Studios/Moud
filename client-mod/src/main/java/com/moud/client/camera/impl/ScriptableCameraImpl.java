package com.moud.client.camera.impl;

import com.moud.client.camera.CameraAPI;
import net.minecraft.client.render.Camera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class ScriptableCameraImpl extends CameraAPI {

    @Override
    public void initialize() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            Vec3d playerPos = mc.player.getEyePos();
            position = new com.moud.api.math.Vector3((float)playerPos.x, (float)playerPos.y, (float)playerPos.z);
            prevPosition = position;
            targetPosition = position;
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
        }
    }

    @Override
    public void tick(float partialTicks) {
        if (!enabled) return;

        if (isTrackingEntity() && trackedEntity != null) {
            prevPosition = targetPosition;
            Vec3d entityPos = trackedEntity.getEyePos();
            targetPosition = new com.moud.api.math.Vector3((float)entityPos.x, (float)entityPos.y, (float)entityPos.z);
            setPosition(targetPosition.x, targetPosition.y, targetPosition.z);
        }
    }

    @Override
    public void applyTransformation(Camera camera) {
        if (!enabled) return;

        try {
            java.lang.reflect.Field posField = Camera.class.getDeclaredField("pos");
            posField.setAccessible(true);
            posField.set(camera, new net.minecraft.util.math.Vec3d(position.x, position.y, position.z));
            java.lang.reflect.Field yawField = Camera.class.getDeclaredField("yaw");
            yawField.setAccessible(true);
            yawField.set(camera, yaw);

            java.lang.reflect.Field pitchField = Camera.class.getDeclaredField("pitch");
            pitchField.setAccessible(true);
            pitchField.set(camera, pitch);

        } catch (Exception e) {

        }
    }

    @Override
    public void handleInput(double deltaX, double deltaY, double deltaZ) {
        if (!enabled) return;

        yaw += (float) deltaX * 0.15f;
        pitch += (float) deltaY * 0.15f;
        pitch = Math.max(-90.0f, Math.min(90.0f, pitch));

        if (deltaZ != 0) {
            float newFov = fov + (float) deltaZ * 2.0f;
            setFOV(newFov);
        }

    }
}