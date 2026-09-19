package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.core.character.Appearance;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.FirstPerson;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.FirstPersonView;
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
        if (FirstPersonView.draw(pose, collector, player, light)) {
            ci.cancel();
            return;
        }
        Character mine = ClientScene.own();
        Appearance look = mine == null ? null : Rig.appearance(mine);
        boolean forced = MoudMod.features().isOn(Feature.HAND);
        FirstPerson wanted = look == null ? FirstPerson.NONE : look.firstPerson;
        if (wanted == FirstPerson.BODY || !forced && wanted == FirstPerson.NONE) ci.cancel();
    }
}
