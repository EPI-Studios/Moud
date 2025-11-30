package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.api.service.CameraService;
import com.moud.client.editor.camera.EditorCameraController;
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
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Shadow private boolean ready;
    @Shadow private Entity focusedEntity;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void moud_applyCameraOverrides(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (this.ready) {
            this.focusedEntity = focusedEntity;
        }

        EditorCameraController controller = EditorCameraController.getInstance();
        controller.updateRenderState();
        if (controller.isActive() && controller.applyToCamera((Camera) (Object) this)) {
            ci.cancel();
            return;
        }

        if (!MoudClientMod.isCustomCameraActive()) {
            return;
        }

        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.camera != null) {
            CameraService cameraService = ClientAPIService.INSTANCE.camera;

            cameraService.updateCamera(tickDelta);

            Vector3d pos = cameraService.getPosition();
            if (pos != null) {
                //MoudClientMod.getLogger().info("Custom camera applying pos={}, yaw={}, pitch={}", pos, cameraService.getYaw(), cameraService.getPitch());
                this.setPos(pos.x, pos.y, pos.z);
            }

            Float yaw = cameraService.getYaw();
            Float pitch = cameraService.getPitch();
            if (yaw != null && pitch != null) {
                this.setRotation(yaw, pitch);
            }
        }

        ci.cancel();
    }

    @Shadow public abstract float getYaw();
    @Shadow public abstract float getPitch();
}
