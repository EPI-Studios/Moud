package com.meekdev.moud.mod.mixin.storage;

import com.mojang.serialization.Dynamic;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// no level.dat
// the level is described by the place, so minecraft's copy would only be a second source of truth
// that drifts. all four overloads are named because a bare method name only binds to one of them
@Mixin(LevelStorageSource.LevelStorageAccess.class)
abstract class LevelDataMixin {

    @Inject(method = "saveDataTag(Lnet/minecraft/world/level/storage/WorldData;)V", at = @At("HEAD"), cancellable = true)
    private void moud$dropDataTag(WorldData data, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "saveDataTag(Lnet/minecraft/world/level/storage/WorldData;Ljava/util/UUID;)V", at = @At("HEAD"), cancellable = true)
    private void moud$dropDataTagForPlayer(WorldData data, UUID player, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "saveLevelData(Lcom/mojang/serialization/Dynamic;)V", at = @At("HEAD"), cancellable = true)
    private void moud$dropLevelData(Dynamic<?> data, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "saveLevelData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"), cancellable = true)
    private void moud$dropLevelTag(CompoundTag tag, CallbackInfo ci) {
        ci.cancel();
    }
}
