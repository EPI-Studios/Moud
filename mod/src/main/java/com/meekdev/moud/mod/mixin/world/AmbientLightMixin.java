package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionType.class)
abstract class AmbientLightMixin {

    @Inject(method = "ambientLight", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(CallbackInfoReturnable<Float> cir) {
        if (!MoudMod.features().isOn(Feature.AMBIENT_LIGHT)) cir.setReturnValue(0.0f);
    }
}
