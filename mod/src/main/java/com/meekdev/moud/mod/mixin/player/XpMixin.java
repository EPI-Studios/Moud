package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses xp
@Mixin(Player.class)
abstract class XpMixin {

    @Inject(method = "giveExperiencePoints", at = @At("HEAD"), cancellable = true)
    private void moud$suppressPoints(int amount, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.XP)) ci.cancel();
    }

    @Inject(method = "giveExperienceLevels", at = @At("HEAD"), cancellable = true)
    private void moud$suppressLevels(int amount, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.XP)) ci.cancel();
    }
}
