package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class RandomTickMixin {

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(LevelChunk chunk, int speed, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.TERRAIN)) ci.cancel();
    }
}
