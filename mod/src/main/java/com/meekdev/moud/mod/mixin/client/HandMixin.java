package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.core.instance.Appearance;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.FirstPerson;
import com.meekdev.moud.core.instance.Rig;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Hands;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.features.Feature;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class HandMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(float tickDelta, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
        Character mine = ClientScene.own();
        Appearance look = mine == null ? null : Rig.appearance(mine);

        if (look == null) {
            if (MoudMod.features().isOn(Feature.HAND)) return;
            ci.cancel();
            return;
        }

        if (look.firstPerson == FirstPerson.HAND) return;

        if (look.firstPerson == FirstPerson.ARM
                && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) {
            return;
        }

        if (look.firstPerson == FirstPerson.ARM) Hands.draw(tickDelta);

        ci.cancel();
    }
}
