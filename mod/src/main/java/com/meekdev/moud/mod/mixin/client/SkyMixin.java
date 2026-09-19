package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.core.render.Sky;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Environment;
import com.meekdev.moud.mod.features.Feature;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void moud$suppress(ClientLevel level, float partial, Camera camera, SkyRenderState state, CallbackInfo ci) {
        Sky sky = Environment.sky();
        if (!MoudMod.features().isOn(Feature.SKY) || (sky != null && sky.faced())) {
            state.skybox = DimensionType.Skybox.NONE;
            state.shouldRenderDarkDisc = false;
            state.skyColor = 0;
            state.sunriseAndSunsetColor = 0;
        }
        if (!MoudMod.features().isOn(Feature.STARS) || (sky != null && !sky.celestialBodiesShown)) {
            state.starBrightness = 0.0f;
        }
        state.skyColor = Environment.skyColor(state.skyColor);
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void moud$hideBodies(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle,
                                 MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        Sky sky = Environment.sky();
        if (sky != null && !sky.celestialBodiesShown) ci.cancel();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void moud$hideSun(float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
        Sky sky = Environment.sky();
        if (sky != null && !sky.sunTextureId.isEmpty()) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void moud$hideMoon(MoonPhase moonPhase, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
        Sky sky = Environment.sky();
        if (sky != null && !sky.moonTextureId.isEmpty()) ci.cancel();
    }

    @Redirect(method = "renderSun", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;scale(FFF)Lorg/joml/Matrix4f;"))
    private Matrix4f moud$sunSize(Matrix4fStack stack, float x, float y, float z) {
        float scale = Environment.sunScale(x);
        return stack.scale(x * scale, y, z * scale);
    }

    @Redirect(method = "renderMoon", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;scale(FFF)Lorg/joml/Matrix4f;"))
    private Matrix4f moud$moonSize(Matrix4fStack stack, float x, float y, float z) {
        float scale = Environment.moonScale(x);
        return stack.scale(x * scale, y, z * scale);
    }

    @ModifyArg(method = "renderStars",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V"), index = 2)
    private int moud$starCount(int indices) {
        return Environment.starIndices(indices);
    }
}
