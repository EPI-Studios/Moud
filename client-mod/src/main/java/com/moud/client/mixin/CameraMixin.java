package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import com.moud.client.api.service.ClientAPIService;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("TAIL"))
    private void moud_applyCameraPositionOverride(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!MoudClientMod.isCustomCameraActive()) {
            return;
        }

        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.camera != null) {
            var cameraService = ClientAPIService.INSTANCE.camera;
            Vector3d lockedPos = cameraService.getLockedPosition();

            if (lockedPos != null) {
                this.setPos(lockedPos.x, lockedPos.y, lockedPos.z);
            }
        }
    }
}