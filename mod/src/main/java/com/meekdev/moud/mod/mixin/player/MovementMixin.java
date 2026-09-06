package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses vanillaMovement
// gravity lives in travel too, so with the switch down a player holds position rather than falling,
// which is what we want until bkun drives the character
@Mixin(Player.class)
abstract class MovementMixin {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(Vec3 movement, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.VANILLA_MOVEMENT)) ci.cancel();
    }
}
