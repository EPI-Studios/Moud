package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.ClientPlace;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRenderMixin {

    @Inject(method = "update", at = @At("HEAD"))
    private void moud$frame(DeltaTracker tracker, boolean advanceGameTime, CallbackInfo ci) {
        if (!advanceGameTime || Minecraft.getInstance().level == null) return;
        ClientPlace.frame(tracker.getGameTimeDeltaPartialTick(true));
    }
}
