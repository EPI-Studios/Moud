package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses hand
// the first person hand is drawn straight from the game renderer, with no event and no amnetic hook in front of it
@Mixin(ItemInHandRenderer.class)
abstract class HandMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(float tickDelta, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.HAND)) ci.cancel();
    }
}
