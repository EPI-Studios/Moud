package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// suppresses itemDrops
// blocks, mobs and a player throwing something all end at one item entity reaching the level,
// which is the only cut that catches every one of them
@Mixin(ServerLevel.class)
abstract class DropsMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ItemEntity && !MoudMod.features().isOn(Feature.ITEM_DROPS)) {
            cir.setReturnValue(false);
        }
    }
}
