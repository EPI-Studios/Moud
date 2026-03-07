package com.moud.client.fabric.mixin;

import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.net.protocol.RuntimeState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
    @Shadow @Final private NativeImageBackedTexture texture;
    @Shadow private NativeImage image;

    @Inject(method = "update", at = @At("TAIL"))
    private void moud$applyAmbientLight(float delta, CallbackInfo ci) {
        RuntimeState state = lastState();
        if (state == null) {
            return;
        }

        float ambient = state.ambientLight();
        if (!Float.isFinite(ambient)) {
            return;
        }
        ambient = Math.max(0.0f, Math.min(1.0f, ambient));
        if (Math.abs(ambient - 1.0f) < 1e-3f) {
            return;
        }

        NativeImage img = this.image;
        if (img == null) {
            return;
        }

        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = img.getColor(x, y);
                int a = (abgr >>> 24) & 0xFF;
                int b = (abgr >>> 16) & 0xFF;
                int g = (abgr >>> 8) & 0xFF;
                int r = abgr & 0xFF;

                r = Math.min(255, Math.round(r * ambient));
                g = Math.min(255, Math.round(g * ambient));
                b = Math.min(255, Math.round(b * ambient));

                img.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }

        texture.upload();
    }

    private static RuntimeState lastState() {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        return runtime == null ? null : runtime.lastServerState();
    }
}
