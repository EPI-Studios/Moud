package com.moud.client.mixin;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.editor.camera.EditorCameraController;
import com.moud.client.editor.rendering.SelectionHighlightRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Camera camera;
    @Shadow @Final private MinecraftClient client;

    @Unique
    private Vector3f moud$cameraBobOffset = new Vector3f();
    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
    private void moud_overrideFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        EditorCameraController controller = EditorCameraController.getInstance();
        if (controller.isActive()) {
            cir.setReturnValue(controller.getFov());
            return;
        }
        if (com.moud.client.MoudClientMod.isCustomCameraActive() && ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.camera != null) {
            Double fov = ClientAPIService.INSTANCE.camera.getFovInternal();
            if (fov != null) {
                cir.setReturnValue(fov);
            }
        }
        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.camera != null
            && ClientAPIService.INSTANCE.camera.isScriptableCameraActive()) {
            double fovOffset = ClientAPIService.INSTANCE.camera.getScriptableFovOffset();
            if (Math.abs(fovOffset) > 0.001) {
                double currentFov = cir.getReturnValue();
                double newFov = Math.max(1, Math.min(170, currentFov + fovOffset));
                cir.setReturnValue(newFov);
            }
        }
    }

    @Inject(method = "bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"), cancellable = true)
    private void moudCameraTransformations(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        EditorCameraController controller = EditorCameraController.getInstance();
        if (controller.isActive()) {
            ci.cancel();
            return;
        }
        if (com.moud.client.MoudClientMod.isCustomCameraActive() && ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.camera != null) {
            var cameraService = ClientAPIService.INSTANCE.camera;

            Float roll = cameraService.getRoll();
            if (roll != null && Math.abs(roll) > 0.001f) {
                matrices.multiply(new Quaternionf().rotateZ((float) Math.toRadians(roll)));
            }

            if (cameraService.shouldDisableViewBobbing()) {
                ci.cancel();
            }
        }

        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.camera != null
            && ClientAPIService.INSTANCE.camera.isScriptableCameraActive()) {

            var cameraService = ClientAPIService.INSTANCE.camera;

            double rollOffset = cameraService.getScriptableRollOffset();
            if (Math.abs(rollOffset) > 0.001) {
                matrices.multiply(new Quaternionf().rotateZ((float) Math.toRadians(rollOffset)));
            }
            if (cameraService.isCinematicBobEnabled()) {
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"))
    private void moud_beforeRenderWorld(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.events != null) {
            MatrixStack matrixStack = new MatrixStack();
            ClientAPIService.INSTANCE.events.dispatch("render:beforeWorld", matrixStack, tickCounter.getTickDelta(true));
        }
    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
    private void moud_afterRenderWorld(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.events != null) {
            MatrixStack matrixStack = new MatrixStack();
            ClientAPIService.INSTANCE.events.dispatch("render:afterWorld", matrixStack, tickCounter.getTickDelta(true));
        }
    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"))
    private void moud_triggerRenderEvents(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ClientAPIService.INSTANCE != null) {
            if (ClientAPIService.INSTANCE.rendering != null) {
                ClientAPIService.INSTANCE.rendering.applyPendingUniforms();
            }
            if (ClientAPIService.INSTANCE.lighting != null) {
                ClientAPIService.INSTANCE.lighting.tick();
            }
        }
    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    private void moud_renderSelectionHighlight(RenderTickCounter tickCounter, CallbackInfo ci) {
        EditorCameraController controller = EditorCameraController.getInstance();
        if (controller.isActive()) {
            MatrixStack matrices = new MatrixStack();
            SelectionHighlightRenderer.getInstance().render(matrices, camera);
        }
    }

}
