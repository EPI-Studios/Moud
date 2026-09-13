package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.features.Features;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class DamageMixin {

    @Inject(method = "isInvulnerableToBase", at = @At("RETURN"), cancellable = true)
    private void moud$suppress(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        Features features = MoudMod.features();
        boolean allowed = true;
        if (source.is(DamageTypeTags.IS_FALL)) allowed = features.isOn(Feature.FALL_DAMAGE);
        else if (source.is(DamageTypeTags.IS_FIRE)) allowed = features.isOn(Feature.FIRE_DAMAGE);
        else if (source.is(DamageTypeTags.IS_DROWNING)) allowed = features.isOn(Feature.DROWNING);
        if (!allowed) cir.setReturnValue(true);
    }
}
