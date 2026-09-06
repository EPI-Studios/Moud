package com.meekdev.moud.mod.mixin.storage;

import net.minecraft.server.PlayerAdvancements;
import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// no stats or advancements on disk
// both are a bare save() with no arguments, so one cut covers the pair
@Mixin({ServerStatsCounter.class, PlayerAdvancements.class})
abstract class PlayerFilesMixin {

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void moud$dropSave(CallbackInfo ci) {
        ci.cancel();
    }
}
