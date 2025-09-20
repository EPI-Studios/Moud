package com.moud.client.api.service;

import com.moud.api.math.Vector3;
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

    @Nullable private Vector3 lockedPosition = null;
    @Nullable private Float overrideYaw = null;
    @Nullable private Float overridePitch = null;
    @Nullable private Float overrideRoll = null;
    private boolean smoothTransitions = false;
    private float transitionSpeed = 1.0f;
    private boolean disableViewBobbing = true;
    private boolean disableHandMovement = true;

    public CameraService() {
        this.client = MinecraftClient.getInstance();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void enableCustomCamera() {
        LOGGER.debug("[CAMERA-SERVICE] Enabling custom camera.");
        MoudClientMod.setCustomCameraActive(true);
    }

    public void disableCustomCamera() {
        LOGGER.debug("[CAMERA-SERVICE] Disabling custom camera.");
        MoudClientMod.setCustomCameraActive(false);
        clearRenderOverrides();
    }

    public boolean isCustomCameraActive() {
        return MoudClientMod.isCustomCameraActive();
    }

    public void setAdvancedCameraLock(Vector3 position, Vector3 rotation, boolean smoothTransitions,
                                      float transitionSpeed, boolean disableViewBobbing, boolean disableHandMovement) {
        this.lockedPosition = position;
        this.overrideYaw = (float) rotation.x;
        this.overridePitch = (float) rotation.y;
        this.overrideRoll = (float) rotation.z;
        this.smoothTransitions = smoothTransitions;
        this.transitionSpeed = transitionSpeed;
        this.disableViewBobbing = disableViewBobbing;
        this.disableHandMovement = disableHandMovement;
        enableCustomCamera();
    }

    public void setRenderYawOverride(double yaw) {
        LOGGER.debug("[CAMERA-SERVICE] Setting Yaw Override: {}", yaw);
        this.overrideYaw = (float) yaw;
    }

    public void setRenderPitchOverride(double pitch) {
        LOGGER.debug("[CAMERA-SERVICE] Setting Pitch Override: {}", pitch);
        this.overridePitch = (float) pitch;
    }

    public void setRenderRollOverride(double roll) {
        LOGGER.debug("[CAMERA-SERVICE] Setting Roll Override: {}", roll);
        this.overrideRoll = (float) roll;
    }

    public void setLockedPosition(Vector3 position) {
        LOGGER.debug("[CAMERA-SERVICE] Setting Locked Position: {}", position);
        this.lockedPosition = position;
    }

    public void clearRenderOverrides() {
        LOGGER.debug("[CAMERA-SERVICE] Clearing all render overrides.");
        this.overrideYaw = null;
        this.overridePitch = null;
        this.overrideRoll = null;
        this.lockedPosition = null;
        this.smoothTransitions = false;
        this.disableViewBobbing = true;
        this.disableHandMovement = true;
    }

    @Nullable
    public Float getRenderYawOverride() {
        return this.overrideYaw;
    }

    @Nullable
    public Float getRenderPitchOverride() {
        return this.overridePitch;
    }

    @Nullable
    public Float getRenderRollOverride() {
        return this.overrideRoll;
    }

    @Nullable
    public Vector3 getLockedPosition() {
        return this.lockedPosition;
    }

    public boolean shouldDisableViewBobbing() {
        return disableViewBobbing && isCustomCameraActive();
    }

    public boolean shouldDisableHandMovement() {
        return disableHandMovement && isCustomCameraActive();
    }

    public boolean isUsingSmoothing() {
        return smoothTransitions;
    }

    public float getTransitionSpeed() {
        return transitionSpeed;
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