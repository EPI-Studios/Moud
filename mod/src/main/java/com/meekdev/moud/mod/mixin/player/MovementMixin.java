package com.meekdev.moud.mod.mixin.player;

import com.meekdev.bkun.physics.Physics;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses vanillaMovement
// gravity lives in travel too, so with the switch down a player with no character holds position
// rather than falling, which is what an empty place wants
//
// a player who has one is left alone here: bkun drives movement from LivingEntity.travel, and
// Player.travel is the override that calls it, so cancelling at this head would cancel bkun too
@Mixin(Player.class)
abstract class MovementMixin {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(Vec3 movement, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (Physics.hasProfile(self)) return;
        if (!MoudMod.features().isOn(Feature.VANILLA_MOVEMENT)) ci.cancel();
    }
}
