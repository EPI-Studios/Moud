package com.moud.client.mixin;

import com.moud.client.camera.CameraManager;
import net.minecraft.client.MinecraftClient;
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

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void onCameraUpdate(CallbackInfo ci) {
        if (CameraManager.isCameraActive()) {
            float td = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
            com.moud.api.math.Vector3 prevPos = CameraManager.getCurrentCamera().getPrevPosition();
            com.moud.api.math.Vector3 targetPos = CameraManager.getCurrentCamera().getTargetPosition();
            com.moud.api.math.Vector3 lerpedPos = prevPos.lerp(targetPos, td);
            this.pos = new Vec3d(lerpedPos.x, lerpedPos.y, lerpedPos.z);
            this.yaw = CameraManager.getCurrentCamera().getYaw();
            this.pitch = CameraManager.getCurrentCamera().getPitch();
            System.out.println("Debug: Custom camera updating - Pos: " + this.pos + ", Yaw: " + this.yaw + ", Pitch: " + this.pitch);
            ci.cancel();
        }
    }
}