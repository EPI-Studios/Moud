package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import com.moud.client.api.service.ClientAPIService;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Camera camera;

    private static final Logger MOUD_DEBUG_LOGGER = LoggerFactory.getLogger("MoudMixinDebug");

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"))
    private void moud_triggerRenderEvents(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.rendering != null) {
            ClientAPIService.INSTANCE.rendering.applyPendingUniforms();
            ClientAPIService.INSTANCE.rendering.triggerRenderEvents();
        }
    }


    @Inject(method = "bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"))
    private void moud_applyCameraOverrides(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (!MoudClientMod.isCustomCameraActive()) {
            return;
        }

        if (ClientAPIService.INSTANCE == null) {
            MOUD_DEBUG_LOGGER.warn("[MIXIN-BOBVIEW] FAILED: ClientAPIService.INSTANCE is null!");
            return;
        }

        Float overrideYaw = ClientAPIService.INSTANCE.camera.getRenderYawOverride();
        Float overridePitch = ClientAPIService.INSTANCE.camera.getRenderPitchOverride();

        if (overrideYaw == null || overridePitch == null) {
            MOUD_DEBUG_LOGGER.warn("[MIXIN-BOBVIEW] SKIPPED: Override values were null.");
            return;
        }

        float currentYaw = camera.getYaw();
        float currentPitch = camera.getPitch();

        float yawDelta = overrideYaw - currentYaw;
        float pitchDelta = overridePitch - currentPitch;

        MOUD_DEBUG_LOGGER.info(String.format(
                "[MIXIN-BOBVIEW] APPLYING OVERRIDE | ScriptYaw=%.4f, VanillaYaw=%.4f, Delta=%.4f",
                overrideYaw, currentYaw, yawDelta
        ));

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDelta));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDelta));
    }
}