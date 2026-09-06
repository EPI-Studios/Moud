package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.NaturalSpawner;

// suppresses mobs
@Mixin(NaturalSpawner.class)
abstract class MobSpawnMixin {

    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void moud$suppressChunk(CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.MOBS)) ci.cancel();
    }

    @Inject(method = "spawnCategoryForPosition", at = @At("HEAD"), cancellable = true)
    private static void moud$suppressPosition(CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.MOBS)) ci.cancel();
    }
}
