package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.cursor.ClientCursorManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Camera camera;

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"))
    private void moud_triggerRenderEvents(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ClientAPIService.INSTANCE != null) {
            if (ClientAPIService.INSTANCE.rendering != null) {
                ClientAPIService.INSTANCE.rendering.applyPendingUniforms();
                ClientAPIService.INSTANCE.rendering.triggerRenderEvents();
            }
            if (ClientAPIService.INSTANCE.lighting != null) {
                ClientAPIService.INSTANCE.lighting.tick();
            }
        }
    }

    @Inject(method = "bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"), cancellable = true)
    private void moud_suppressViewBobbing(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (MoudClientMod.isCustomCameraActive() && ClientAPIService.INSTANCE != null &&
                ClientAPIService.INSTANCE.camera != null && ClientAPIService.INSTANCE.camera.shouldDisableViewBobbing()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
    private void moud_renderCursors(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ClientAPIService.INSTANCE != null) {
            MatrixStack matrices = new MatrixStack();
            ClientCursorManager.getInstance().renderCursors(matrices, null, tickCounter.getTickDelta(true));
        }
    }
}