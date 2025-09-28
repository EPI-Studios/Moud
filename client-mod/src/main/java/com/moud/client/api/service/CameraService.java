package com.moud.client.api.service;

import com.moud.api.math.Vector3;
import com.moud.client.MoudClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.graalvm.polyglot.Context;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CameraService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraService.class);
    private final MinecraftClient client;
    private Context jsContext;

    @Nullable private Vector3d lockedPosition = null;
    @Nullable private Float overrideYaw = null;
    @Nullable private Float overridePitch = null;
    @Nullable private Float overrideRoll = null;
    private boolean smoothTransitions = false;
    private float transitionSpeed = 1.0f;
    private boolean disableViewBobbing = true;
    private boolean disableHandMovement = true;

    private final Quaternionf currentRotation = new Quaternionf();
    private final Quaternionf targetRotation = new Quaternionf();
    private final Vector3d currentPos = new Vector3d();
    private final Vector3d targetPos = new Vector3d();

    private boolean rotationDirty = false;
    private boolean positionDirty = false;
    private boolean initialized = false;

    public CameraService() {
        this.client = MinecraftClient.getInstance();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void enableCustomCamera() {
        LOGGER.debug("[CAMERA-SERVICE] Enabling custom camera.");
        MoudClientMod.setCustomCameraActive(true);
        initializeCurrentState();
    }

    public void disableCustomCamera() {
        LOGGER.debug("[CAMERA-SERVICE] Disabling custom camera.");
        MoudClientMod.setCustomCameraActive(false);
        clearRenderOverrides();
        initialized = false;
    }

    public boolean isCustomCameraActive() {
        return MoudClientMod.isCustomCameraActive();
    }

    public void setAdvancedCameraLock(Vector3 position, Vector3 rotation, boolean smoothTransitions,
                                      float transitionSpeed, boolean disableViewBobbing, boolean disableHandMovement) {
        this.lockedPosition = new Vector3d(position.x, position.y, position.z);
        this.overrideYaw = (float) rotation.x;
        this.overridePitch = (float) rotation.y;
        this.overrideRoll = (float) rotation.z;
        this.smoothTransitions = smoothTransitions;
        this.transitionSpeed = Math.max(0.1f, transitionSpeed);
        this.disableViewBobbing = disableViewBobbing;
        this.disableHandMovement = disableHandMovement;

        updateTargetRotation();
        updateTargetPosition();
        enableCustomCamera();
    }

    public void setRenderYawOverride(double yaw) {
        LOGGER.debug("[CAMERA-SERVICE] Setting Yaw Override: {}", yaw);
        this.overrideYaw = (float) yaw;
        updateTargetRotation();
    }

    public void setRenderPitchOverride(double pitch) {
        LOGGER.debug("[CAMERA-SERVICE] Setting Pitch Override: {}", pitch);
        this.overridePitch = (float) pitch;
        updateTargetRotation();
    }

    public void setRenderRollOverride(double roll) {
        LOGGER.debug("[CAMERA-SERVICE] Setting Roll Override: {}", roll);
        this.overrideRoll = (float) roll;
        updateTargetRotation();
    }

    public void setLockedPosition(Vector3 position) {
        LOGGER.debug("[CAMERA-SERVICE] Setting Locked Position: {}", position);
        this.lockedPosition = new Vector3d(position.x, position.y, position.z);
        updateTargetPosition();
    }

    private void initializeCurrentState() {
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity != null) {
            currentPos.set(cameraEntity.getX(), cameraEntity.getEyeY(), cameraEntity.getZ());
            updateRotationFromAngles(currentRotation, cameraEntity.getYaw(), cameraEntity.getPitch(), 0.0f);
            initialized = true;
        }
    }

    private void updateTargetRotation() {
        if (overrideYaw != null || overridePitch != null || overrideRoll != null) {
            float yaw = overrideYaw != null ? overrideYaw : getCurrentYaw();
            float pitch = overridePitch != null ? overridePitch : getCurrentPitch();
            float roll = overrideRoll != null ? overrideRoll : 0.0f;

            updateRotationFromAngles(targetRotation, yaw, pitch, roll);
            rotationDirty = true;
        }
    }

    private void updateTargetPosition() {
        if (lockedPosition != null) {
            targetPos.set(lockedPosition);
            positionDirty = true;
        }
    }

    private void updateRotationFromAngles(Quaternionf quat, float yaw, float pitch, float roll) {
        quat.identity();
        quat.rotateY((float) Math.toRadians(-yaw));
        quat.rotateX((float) Math.toRadians(pitch));
        quat.rotateZ((float) Math.toRadians(roll));
    }

    public void updateCamera(float tickDelta) {
        if (!initialized) {
            initializeCurrentState();
            return;
        }

        if (smoothTransitions) {
            float lerpFactor = Math.min(1.0f, transitionSpeed * tickDelta);

            if (rotationDirty) {
                currentRotation.slerp(targetRotation, lerpFactor);
                if (currentRotation.equals(targetRotation, 0.001f)) {
                    rotationDirty = false;
                }
            }

            if (positionDirty) {
                currentPos.lerp(targetPos, lerpFactor);
                if (currentPos.equals(targetPos, 0.001)) {
                    positionDirty = false;
                }
            }
        } else {
            if (rotationDirty) {
                currentRotation.set(targetRotation);
                rotationDirty = false;
            }

            if (positionDirty) {
                currentPos.set(targetPos);
                positionDirty = false;
            }
        }
    }

    public Vector3d getCurrentCameraPosition() {
        return new Vector3d(currentPos);
    }

    public Quaternionf getCurrentCameraRotation() {
        return new Quaternionf(currentRotation);
    }

    public Vector3d getLockedPosition() {
        return lockedPosition != null ? new Vector3d(lockedPosition) : null;
    }

    public boolean shouldDisableViewBobbing() {
        return disableViewBobbing;
    }

    public Float getRenderPitchOverride() {
        return overridePitch;
    }

    public Float getRenderYawOverride() {
        return overrideYaw;
    }

    public Float getRenderRollOverride() {
        return overrideRoll;
    }

    public boolean getSmoothTransitions() {
        return smoothTransitions;
    }

    public boolean getDisableViewBobbing() {
        return disableViewBobbing;
    }

    public boolean getDisableHandMovement() {
        return disableHandMovement;
    }

    public void clearRenderOverrides() {
        LOGGER.debug("[CAMERA-SERVICE] Clearing all render overrides.");
        this.lockedPosition = null;
        this.overrideYaw = null;
        this.overridePitch = null;
        this.overrideRoll = null;
        this.rotationDirty = false;
        this.positionDirty = false;
    }

    private float getCurrentYaw() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getYaw() : 0.0f;
    }

    private float getCurrentPitch() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getPitch() : 0.0f;
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
        initialized = false;
        LOGGER.info("CameraService cleaned up.");
    }
}