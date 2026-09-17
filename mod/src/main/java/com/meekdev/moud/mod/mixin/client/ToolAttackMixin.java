package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.tool.ClientTools;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class ToolAttackMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void moud$toolClick(CallbackInfoReturnable<Boolean> cir) {
        if (ClientTools.holding()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void moud$toolHold(boolean down, CallbackInfo ci) {
        if (ClientTools.holding()) ci.cancel();
    }
}
