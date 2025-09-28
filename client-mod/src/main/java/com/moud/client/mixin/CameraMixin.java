package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import com.moud.client.api.service.ClientAPIService;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraMixin.class);

    @Shadow private float yaw;
    @Shadow private float pitch;
    private float roll;
    @Shadow @Final private Quaternionf rotation;

    @Shadow protected abstract void setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("TAIL"))
    private void moud_applyCameraOverride(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!MoudClientMod.isCustomCameraActive()) {
            return;
        }

        if (ClientAPIService.INSTANCE == null || ClientAPIService.INSTANCE.camera == null) {
            LOGGER.warn("[CAMERA-MIXIN] Custom camera is active but API service is not available!");
            return;
        }

        var cameraService = ClientAPIService.INSTANCE.camera;

        cameraService.updateCamera(tickDelta);

        Vector3d overridePos = cameraService.getCurrentCameraPosition();
        Quaternionf overrideRotation = cameraService.getCurrentCameraRotation();

        if (overridePos != null) {
            this.setPos(overridePos.x, overridePos.y, overridePos.z);
        }

        if (overrideRotation != null) {
            this.rotation.set(overrideRotation);

            org.joml.Vector3f eulerAngles = new org.joml.Vector3f();
            overrideRotation.getEulerAnglesYXZ(eulerAngles);
            this.yaw = (float) Math.toDegrees(-eulerAngles.y);
            this.pitch = (float) Math.toDegrees(eulerAngles.x);
            this.roll = (float) Math.toDegrees(eulerAngles.z);
        }
    }
}