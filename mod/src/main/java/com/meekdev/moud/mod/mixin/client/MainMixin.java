package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.ClassWarmup;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
abstract class MainMixin {

    @Inject(method = "main", at = @At("HEAD"))
    private static void moud$warmup(String[] args, CallbackInfo ci) {
        ClassWarmup.start();
    }
}
