package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses entityRendering and playerModel
//
// on the submit, because that is where an entity is actually drawn now. shouldRender is still on
// the dispatcher and nothing calls it any more: cancelling there suppressed nothing at all, and
// the vanilla body kept being drawn straight through our own
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(EntityRenderState state, CameraRenderState camera,
            double x, double y, double z, PoseStack poses, SubmitNodeCollector out,
            CallbackInfo ci) {
        Feature feature = state instanceof AvatarRenderState
                ? Feature.PLAYER_MODEL : Feature.ENTITY_RENDERING;
        if (!MoudMod.features().isOn(feature)) ci.cancel();
    }
}
