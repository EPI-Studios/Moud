package com.moud.client.api.service;

import com.moud.client.MoudClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.graalvm.polyglot.Context;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CameraService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraService.class);
    private final MinecraftClient client;
    private Context jsContext;

    @Nullable private Float overrideYaw = null;
    @Nullable private Float overridePitch = null;

    public CameraService() {
        this.client = MinecraftClient.getInstance();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void enableCustomCamera() {
        MoudClientMod.setCustomCameraActive(true);
    }

    public void disableCustomCamera() {
        MoudClientMod.setCustomCameraActive(false);
        clearRenderOverrides();
    }

    public boolean isCustomCameraActive() {
        return MoudClientMod.isCustomCameraActive();
    }

    public void setRenderYawOverride(double yaw) {
        this.overrideYaw = (float) yaw;
    }

    public void setRenderPitchOverride(double pitch) {
        this.overridePitch = (float) pitch;
    }

    public void clearRenderOverrides() {
        this.overrideYaw = null;
        this.overridePitch = null;
    }

    @Nullable
    public Float getRenderYawOverride() {
        return this.overrideYaw;
    }

    @Nullable
    public Float getRenderPitchOverride() {
        return this.overridePitch;
    }

    public float getPitch() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getPitch() : 0.0f;
    }

    public float getYaw() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getYaw() : 0.0f;
    }

    public double getX() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getX() : 0.0;
    }

    public double getY() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getY() : 0.0;
    }

    public double getZ() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getZ() : 0.0;
    }

    public void setPosition(double x, double y, double z) {
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity != null) {
            cameraEntity.setPosition(x, y, z);
        }
    }

    public void addRotation(double pitchDelta, double yawDelta) {
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity != null) {
            float newPitch = MathHelper.clamp(cameraEntity.getPitch() + (float)pitchDelta, -90.0f, 90.0f);
            float newYaw = cameraEntity.getYaw() + (float)yawDelta;
            cameraEntity.setPitch(newPitch);
            cameraEntity.setYaw(newYaw);
        }
    }

    public void setPitch(double pitch) {
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity != null) {
            cameraEntity.setPitch(MathHelper.clamp((float)pitch, -90.0f, 90.0f));
        }
    }

    public void setYaw(double yaw) {
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity != null) {
            cameraEntity.setYaw((float)yaw);
        }
    }

    public float getFov() {
        return (float) client.options.getFov().getValue();
    }

    public void setFov(double fov) {
        client.options.getFov().setValue((int) fov);
    }

    public boolean isThirdPerson() {
        return client.options.getPerspective() != Perspective.FIRST_PERSON;
    }

    public void setThirdPerson(boolean thirdPerson) {
        if (thirdPerson != isThirdPerson()) {
            client.options.setPerspective(client.options.getPerspective().next());
        }
    }

    public void lookAt(double targetX, double targetY, double targetZ) {
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return;

        double currentX = cameraEntity.getX();
        double currentY = cameraEntity.getEyeY();
        double currentZ = cameraEntity.getZ();

        double deltaX = targetX - currentX;
        double deltaY = targetY - currentY;
        double deltaZ = targetZ - currentZ;

        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(-deltaX, deltaZ)));
        float pitch = (float) (Math.toDegrees(Math.atan2(-deltaY, distance)));

        cameraEntity.setYaw(yaw);
        cameraEntity.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
    }

    public void cleanUp() {
        this.jsContext = null;
        this.clearRenderOverrides();
        MoudClientMod.setCustomCameraActive(false);
        LOGGER.info("CameraService cleaned up.");
    }
}