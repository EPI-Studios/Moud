package com.meekdev.moud.mod.mixin.storage;

import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// no level.dat
// the level is described by the place, so writing minecraft's copy would only give us a second
// source of truth that drifts
@Mixin(LevelStorageSource.LevelStorageAccess.class)
abstract class LevelDataMixin {

    @Inject(method = "saveDataTag", at = @At("HEAD"), cancellable = true)
    private void moud$dropDataTag(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "saveLevelData", at = @At("HEAD"), cancellable = true)
    private void moud$dropLevelData(CallbackInfo ci) {
        ci.cancel();
    }
}
