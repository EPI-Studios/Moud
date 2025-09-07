package com.moud.client.mixin;

import com.moud.client.camera.CameraManager;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {

    @Shadow private Vec3d pos;
    @Shadow private float yaw;
    @Shadow private float pitch;

    @Inject(method = "update", at = @At("TAIL"))
    private void onCameraUpdate(CallbackInfo ci) {
        if (CameraManager.isCameraActive()) {
            com.moud.api.math.Vector3 customPos = CameraManager.getCurrentCamera().getPosition();
            this.pos = new Vec3d(customPos.x, customPos.y, customPos.z);
            this.yaw = CameraManager.getCurrentCamera().getYaw();
            this.pitch = CameraManager.getCurrentCamera().getPitch();
        }
    }
}