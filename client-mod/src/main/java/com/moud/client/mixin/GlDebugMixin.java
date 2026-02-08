package com.moud.client.mixin;

import net.minecraft.client.gl.GlDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlDebug.class)
public class GlDebugMixin {

    @Inject(method = "info", at = @At("HEAD"), cancellable = true)
    private static void moud$filterSpam(int source, int type, int id, int severity, int messageLength, long message, long l, CallbackInfo ci) {
        if (id == 1282 || id == 1281) {
            ci.cancel();
        }
    }
}
