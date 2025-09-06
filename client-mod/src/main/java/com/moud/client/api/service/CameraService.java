package com.moud.client.api.service;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public final class CameraService {
    private final MinecraftClient client;

    public CameraService() {
        this.client = MinecraftClient.getInstance();
    }

    public float getPitch() {
        Entity camera = client.getCameraEntity();
        return camera != null ? camera.getPitch() : 0.0f;
    }

    public void setPitch(float pitch) {
        Entity camera = client.getCameraEntity();
        if (camera != null) {
            camera.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
        }
    }

    public float getYaw() {
        Entity camera = client.getCameraEntity();
        return camera != null ? camera.getYaw() : 0.0f;
    }

    public void setYaw(float yaw) {
        Entity camera = client.getCameraEntity();
        if (camera != null) {
            camera.setYaw(yaw);
        }
    }

    public double getX() {
        Entity camera = client.getCameraEntity();
        return camera != null ? camera.getX() : 0.0;
    }

    public double getY() {
        Entity camera = client.getCameraEntity();
        return camera != null ? camera.getY() : 0.0;
    }

    public double getZ() {
        Entity camera = client.getCameraEntity();
        return camera != null ? camera.getZ() : 0.0;
    }

    public void setPosition(double x, double y, double z) {
        Entity camera = client.getCameraEntity();
        if (camera != null) {
            camera.setPosition(x, y, z);
        }
    }

    public void addRotation(float pitchDelta, float yawDelta) {
        Entity camera = client.getCameraEntity();
        if (camera != null) {
            float newPitch = MathHelper.clamp(camera.getPitch() + pitchDelta, -90.0f, 90.0f);
            float newYaw = camera.getYaw() + yawDelta;
            camera.setPitch(newPitch);
            camera.setYaw(newYaw);
        }
    }

    public float getFov() {
        return (float) client.options.getFov().getValue();
    }

    public void setFov(double fov) {
        client.options.getFov().setValue((int) fov);
    }

    public boolean isThirdPerson() {
        return !client.options.getPerspective().isFirstPerson();
    }

    public void setThirdPerson(boolean thirdPerson) {
        if (thirdPerson && client.options.getPerspective().isFirstPerson()) {
            client.options.setPerspective(client.options.getPerspective().next());
        } else if (!thirdPerson && !client.options.getPerspective().isFirstPerson()) {
            client.options.setPerspective(client.options.getPerspective().next());
        }
    }

    public void lookAt(double targetX, double targetY, double targetZ) {
        Entity camera = client.getCameraEntity();
        if (camera != null) {
            double deltaX = targetX - camera.getX();
            double deltaY = targetY - camera.getY();
            double deltaZ = targetZ - camera.getZ();

            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            float yaw = (float) (Math.atan2(-deltaX, deltaZ) * 180.0 / Math.PI);
            float pitch = (float) (Math.atan2(-deltaY, distance) * 180.0 / Math.PI);

            camera.setYaw(yaw);
            camera.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
        }
    }
}