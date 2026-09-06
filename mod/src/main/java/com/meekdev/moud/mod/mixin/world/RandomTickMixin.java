package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses the random tick, which follows terrain
// it had an off switch while the switches were gamerules and lost it when they became mixins,
// so every loaded section was still being walked for grass, ice and crops in a place with none
@Mixin(ServerLevel.class)
abstract class RandomTickMixin {

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(LevelChunk chunk, int speed, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.TERRAIN)) ci.cancel();
    }
}
