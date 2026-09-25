package com.meekdev.moud.mod.mixin.storage;

import com.meekdev.moud.mod.server.WorldSaving;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerStatsCounter.class, PlayerAdvancements.class})
abstract class PlayerFilesMixin {

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void moud$dropSave(CallbackInfo ci) {
        if (WorldSaving.off()) ci.cancel();
    }
}
