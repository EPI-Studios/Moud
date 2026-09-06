package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses hunger
@Mixin(FoodData.class)
abstract class HungerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(ServerPlayer player, CallbackInfo ci) {
        if (MoudMod.features().isOn(Feature.HUNGER)) return;
        FoodData food = (FoodData) (Object) this;
        if (food.getFoodLevel() < 20) food.setFoodLevel(20);
        if (food.getSaturationLevel() < 5.0f) food.setSaturation(5.0f);
        ci.cancel();
    }

    @Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
    private void moud$suppressExhaustion(float amount, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.HUNGER)) ci.cancel();
    }
}
