package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.mod.server.VoidLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.dedicated.DedicatedServerProperties$WorldDimensionData")
abstract class VoidServerMixin {

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private void moud$placeWorld(HolderLookup.Provider registries, CallbackInfoReturnable<WorldDimensions> cir) {
        if (PlaceToml.chosenAtLaunch()) cir.setReturnValue(VoidLevel.dimensions(registries));
    }
}
