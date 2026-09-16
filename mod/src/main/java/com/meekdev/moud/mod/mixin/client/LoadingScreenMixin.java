package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.screens.LoadingLook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
abstract class LoadingScreenMixin {

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
    private void moud$placeLoading(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        boolean loading = self instanceof LevelLoadingScreen || self instanceof GenericMessageScreen || self instanceof ProgressScreen || self instanceof ConnectScreen;
        if (!loading || !LoadingLook.active()) return;
        LoadingLook.draw(graphics, self.width, self.height);
        ci.cancel();
    }
}
