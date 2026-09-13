package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NaturalSpawner.class)
abstract class MobSpawnMixin {

    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void moud$suppressChunk(CallbackInfo ci) {
        off(ci);
    }

    @Inject(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"), cancellable = true)
    private static void moud$suppressPosition(CallbackInfo ci) {
        off(ci);
    }

    @Inject(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At("HEAD"), cancellable = true)
    private static void moud$suppressPositionInChunk(CallbackInfo ci) {
        off(ci);
    }

    private static void off(CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.MOBS)) ci.cancel();
    }
}
