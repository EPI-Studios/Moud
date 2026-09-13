package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.render.HeldItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class HeldItemsMixin {

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void moud$heldItems(PoseStack poses, LevelRenderState level, SubmitNodeCollector out, CallbackInfo ci) {
        HeldItems.submit(poses, out, level.cameraRenderState.pos);
    }
}
