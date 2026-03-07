package com.moud.client.fabric.mixin;

import com.moud.client.fabric.env.WorldEnvironmentClient;
import com.moud.client.fabric.env.WorldEnvironmentClient.Mode;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererSkyCloudOverrideMixin {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void moud$cancelSky(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable runnable, CallbackInfo ci) {
        if (WorldEnvironmentClient.current().skyMode() != Mode.VANILLA) {
            ci.cancel();
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void moud$cancelClouds(MatrixStack matrices, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float tickDelta, double x, double y, double z, CallbackInfo ci) {
        if (WorldEnvironmentClient.current().cloudsMode() != Mode.VANILLA) {
            ci.cancel();
        }
    }
}
