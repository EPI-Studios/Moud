package com.moud.client.fabric.mixin;

import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.net.protocol.RuntimeState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Shadow
    private static float red;

    @Shadow
    private static float green;

    @Shadow
    private static float blue;

    @Inject(method = "render", at = @At("TAIL"))
    private static void moud$render(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        RuntimeState state = lastState();
        if (state == null || !state.fogEnabled()) {
            return;
        }
        float r = clamp01(state.fogColorR());
        float g = clamp01(state.fogColorG());
        float b = clamp01(state.fogColorB());
        red = r;
        green = g;
        blue = b;
        RenderSystem.clearColor(r, g, b, 0.0f);
    }

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void moud$applyFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {
        RuntimeState state = lastState();
        if (state == null || !state.fogEnabled()) {
            return;
        }
        if (camera != null && camera.getSubmersionType() != CameraSubmersionType.NONE) {
            return;
        }

        float density = MathHelper.clamp(state.fogDensity(), 0.0f, 1.0f);
        if (density <= 0.0f) {
            return;
        }

        float baseEnd = Math.max(1.0f, viewDistance);
        float minEnd = Math.min(baseEnd, 6.0f);
        float end = MathHelper.lerp(density, baseEnd, minEnd);
        float startFrac = MathHelper.lerp(density, 0.9f, 0.0f);
        float start = end * startFrac;

        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
        RenderSystem.setShaderFogShape(FogShape.CYLINDER);
    }

    @Inject(method = "applyFogColor", at = @At("TAIL"))
    private static void moud$applyFogColor(CallbackInfo ci) {
        RuntimeState state = lastState();
        if (state == null || !state.fogEnabled()) {
            return;
        }
        RenderSystem.setShaderFogColor(
                clamp01(state.fogColorR()),
                clamp01(state.fogColorG()),
                clamp01(state.fogColorB())
        );
    }

    private static RuntimeState lastState() {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        return runtime == null ? null : runtime.lastServerState();
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}

