package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Hands;
import com.meekdev.moud.mod.features.Feature;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses hand, and draws ours in its place
//
// the first person hand is drawn straight from the game renderer, with no event and no amnetic
// hook in front of it. that is also the only moment the depth buffer is clear and the projection
// is the flat seventy degrees a hand is drawn at, so it is the one place ours can go
@Mixin(ItemInHandRenderer.class)
abstract class HandMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(float tickDelta, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
        if (MoudMod.features().isOn(Feature.HAND)) return;
        // ours, from the body the client owns, before the call that would have drawn the game's
        Hands.draw(tickDelta);
        ci.cancel();
    }
}
