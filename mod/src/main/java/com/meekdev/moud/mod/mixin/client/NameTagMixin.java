package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class NameTagMixin {

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(CallbackInfoReturnable<Boolean> cir) {
        if (!MoudMod.features().isOn(Feature.NAME_TAGS)) cir.setReturnValue(false);
    }
}
