package com.moud.client.api.service;

import com.moud.api.math.Vector3;
import com.moud.client.MoudClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CameraService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraService.class);
    private final MinecraftClient client;
    private Context jsContext;

    @Nullable private volatile Vector3d lockedPosition = null;
    @Nullable private volatile Float overrideRoll = null;
    private volatile boolean disableViewBobbing = true;

    public CameraService() {
        this.client = MinecraftClient.getInstance();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    @HostAccess.Export
    public void enableCustomCamera() {
        LOGGER.debug("[CAMERA-SERVICE] Enabling custom camera mode.");
        MoudClientMod.setCustomCameraActive(true);
    }

    @HostAccess.Export
    public void disableCustomCamera() {
        LOGGER.debug("[CAMERA-SERVICE] Disabling custom camera mode.");
        MoudClientMod.setCustomCameraActive(false);
        this.overrideRoll = null;
        this.lockedPosition = null;
    }

    @HostAccess.Export
    public boolean isCustomCameraActive() {
        return MoudClientMod.isCustomCameraActive();
    }

    @HostAccess.Export
    public void setLockedPosition(Vector3 position) {

        if (position != null) {
            this.lockedPosition = new Vector3d(position.x, position.y, position.z);
        } else {
            this.lockedPosition = null;
        }
    }

    @Nullable
    public Vector3d getLockedPosition() {
        return this.lockedPosition;
    }

    @HostAccess.Export
    public void setRenderRollOverride(double roll) {
        if (isCustomCameraActive()) {
            this.overrideRoll = (float) roll;
        }
    }

    @HostAccess.Export
    public Float getRenderRollOverride() {
        return overrideRoll;
    }

    public boolean shouldDisableViewBobbing() {
        return isCustomCameraActive() && disableViewBobbing;
    }

    @HostAccess.Export
    public double getX() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getX() : 0.0;
    }

    @HostAccess.Export
    public double getY() {
        Entity cameraEntity = client.getCameraEntity();

        if (isCustomCameraActive() && lockedPosition != null) {
            return lockedPosition.y;
        }
        return cameraEntity != null ? cameraEntity.getEyeY() : 0.0;
    }

    @HostAccess.Export
    public double getZ() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getZ() : 0.0;
    }

    @HostAccess.Export
    public Vector3 createVector3(double x, double y, double z) {
        return new Vector3(x, y, z);
    }

    public void cleanUp() {
        this.jsContext = null;
        if (MoudClientMod.isCustomCameraActive()) {
            this.disableCustomCamera();
        }
        LOGGER.info("CameraService cleaned up.");
    }
}