package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses healthRegen
@Mixin(ServerPlayer.class)
abstract class RegenMixin {

    @Inject(method = "tickRegeneration", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.HEALTH_REGEN)) ci.cancel();
    }
}
