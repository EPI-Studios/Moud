package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.input.Autopilot;
import com.meekdev.moud.mod.client.input.Controls;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
abstract class PilotMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void moud$pilot(CallbackInfo ci) {
        Controls.apply((ClientInput) (Object) this);
        Autopilot.apply((ClientInput) (Object) this);
    }
}
