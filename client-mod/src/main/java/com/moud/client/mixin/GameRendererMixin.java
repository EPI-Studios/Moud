package com.moud.client.mixin;

import com.moud.client.api.service.ClientAPIService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Camera camera;
    @Shadow @Final private MinecraftClient client;
    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
    private void moud_overrideFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (com.moud.client.MoudClientMod.isCustomCameraActive() && ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.camera != null) {
            Double fov = ClientAPIService.INSTANCE.camera.getFovInternal();
            if (fov != null) {
                cir.setReturnValue(fov);
            }
        }
    }

    @Inject(method = "bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"), cancellable = true)
    private void moudCameraTransformations(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
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



}