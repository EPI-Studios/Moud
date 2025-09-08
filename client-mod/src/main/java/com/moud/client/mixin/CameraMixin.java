package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import com.moud.client.api.service.ClientAPIService;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf; // Use the correct Quaternion class from JOML
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private float yaw;
    @Shadow private float pitch;
    @Shadow @Final private Quaternionf rotation;

    @Inject(method = "update", at = @At("TAIL"))
    private void moud_applyCameraOverride(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {


        if (MoudClientMod.isCustomCameraActive() && ClientAPIService.INSTANCE != null) {
            Float overridePitch = ClientAPIService.INSTANCE.camera.getRenderPitchOverride();
            Float overrideYaw = ClientAPIService.INSTANCE.camera.getRenderYawOverride();

            if (overridePitch != null || overrideYaw != null) {

                if (overrideYaw != null) {
                    this.yaw = overrideYaw;
                }
                if (overridePitch != null) {
                    this.pitch = overridePitch;
                }


                this.rotation.set(0.0f, 0.0f, 0.0f, 1.0f);
                this.rotation.rotateY((float) Math.toRadians(-this.yaw));
                this.rotation.rotateX((float) Math.toRadians(this.pitch));
            }
        }
    }
}