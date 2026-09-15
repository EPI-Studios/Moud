package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.gl.Textures;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public class TextureCacheMixin {

    @Inject(method = "_bindTexture", at = @At("HEAD"))
    private static void moud$bindForReal(int texture, CallbackInfo ci) {
        Textures.bindDirectly(texture);
    }

    @Inject(method = "_activeTexture", at = @At("HEAD"))
    private static void moud$activateForReal(int unit, CallbackInfo ci) {
        Textures.activateDirectly(unit);
    }
}
