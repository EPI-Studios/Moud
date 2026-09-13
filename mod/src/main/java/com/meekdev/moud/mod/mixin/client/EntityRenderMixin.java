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

@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(EntityRenderState state, CameraRenderState camera,
            double x, double y, double z, PoseStack poses, SubmitNodeCollector out,
            CallbackInfo ci) {
        Feature feature = state instanceof AvatarRenderState
                ? Feature.PLAYER_MODEL : Feature.ENTITY_RENDERING;
        if (MoudMod.features().isOn(feature)) return;

        if (MoudMod.features().isOn(Feature.BLOB_SHADOWS) && !state.shadowPieces.isEmpty()) {
            poses.pushPose();
            poses.translate(x, y, z);
            out.submitShadow(poses, state.shadowRadius, state.shadowPieces);
            poses.popPose();
        }

        if (state.nameTag != null || state.scoreText != null) {
            poses.pushPose();
            poses.translate(x, y, z);
            if (state.scoreText != null) {
                out.submitNameTag(poses, state.nameTagAttachment, 0, state.scoreText,
                        !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
                poses.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
            }
            if (state.nameTag != null) {
                out.submitNameTag(poses, state.nameTagAttachment, 0, state.nameTag,
                        !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
            }
            poses.popPose();
        }
        ci.cancel();
    }
}
