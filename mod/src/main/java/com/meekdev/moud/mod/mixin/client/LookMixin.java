package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.EditMode;
import com.meekdev.moud.mod.client.input.Controls;
import com.meekdev.moud.mod.client.input.Devices;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class LookMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void moud$look(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && (!Controls.look() || Devices.lookSunk()) && !EditMode.editing()) ci.cancel();
    }
}
