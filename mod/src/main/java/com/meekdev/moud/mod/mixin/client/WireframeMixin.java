package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.gl.RenderState;
import com.meekdev.moud.mod.adapter.render.EditorView;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public class WireframeMixin {

    @Inject(method = "_polygonMode", at = @At("HEAD"), cancellable = true)
    private static void moud$polygonMode(int face, int mode, CallbackInfo ci) {
        RenderState.polygonMode(face, mode, EditorView.lines());
        ci.cancel();
    }
}
