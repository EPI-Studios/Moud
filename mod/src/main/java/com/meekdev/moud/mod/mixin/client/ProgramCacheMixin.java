package com.meekdev.moud.mod.mixin.client;

import com.mojang.blaze3d.opengl.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class ProgramCacheMixin {

    @Shadow
    private GlProgram lastProgram;

    @Inject(method = "trySetup", at = @At("HEAD"))
    private void moud$rebindProgram(CallbackInfoReturnable<Boolean> cir) {
        lastProgram = null;
    }
}
