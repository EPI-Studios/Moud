package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses blockEntities
// they still exist as data in the place, they just never tick, so a chest is a block and nothing more
@Mixin(Level.class)
abstract class BlockEntitiesMixin {

    @Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.BLOCK_ENTITIES)) ci.cancel();
    }
}
