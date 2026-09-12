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
        if (MoudMod.features().isOn(feature)) return;

        // the blob is submitted inside this method, past the point we cancel, so suppressing the
        // model takes the shadow with it. a place that wants the game's flat circle rather than
        // the one our own body casts gets it resubmitted here, from the game's own state
        //
        // at the head the stack has not been translated yet, so this does what the method does:
        // for a player the render offset is added and taken off again before the shadow, which
        // leaves it at exactly the position handed in
        if (MoudMod.features().isOn(Feature.BLOB_SHADOWS) && !state.shadowPieces.isEmpty()) {
            poses.pushPose();
            poses.translate(x, y, z);
            out.submitShadow(poses, state.shadowRadius, state.shadowPieces);
            poses.popPose();
        }
        ci.cancel();
    }
}
