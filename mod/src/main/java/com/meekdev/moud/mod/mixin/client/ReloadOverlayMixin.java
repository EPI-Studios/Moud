package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.screens.LoadingLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
abstract class ReloadOverlayMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private long fadeOutStart;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void moud$skipFade(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (fadeOutStart < 0 || !LoadingLook.active()) return;
        minecraft.setOverlay(null);
        if (minecraft.screen != null) minecraft.screen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }
}
