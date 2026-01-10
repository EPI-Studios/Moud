package com.moud.client.mixin;

import com.moud.client.ui.loading.MoudPreloadState;
import com.moud.client.ui.screen.MoudPreloadScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MoudPreloadScreenMixin {
    private static boolean swapping;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void moud$overrideDownloadingTerrain(Screen screen, CallbackInfo ci) {
        if (swapping) {
            return;
        }
        if (!MoudPreloadState.isActive()) {
            return;
        }
        if (!(screen instanceof DownloadingTerrainScreen)) {
            return;
        }

        swapping = true;
        try {
            ((MinecraftClient) (Object) this).setScreen(new MoudPreloadScreen());
        } finally {
            swapping = false;
        }
        ci.cancel();
    }
}
