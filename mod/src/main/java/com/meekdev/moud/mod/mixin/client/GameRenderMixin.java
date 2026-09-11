package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.ClientPlace;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the place gets its say before the frame is built rather than while it is being drawn
//
// GameRenderer.update runs mainCamera.update and then levelRenderer.update(mainCamera), and that
// second call is where chunk sections are culled. the render events fire later still, inside
// LevelRenderer.renderLevel -- so a camera written from one of those was always describing a
// frame that had already decided what it could see, and the world was culled and extracted
// against the pose from the frame before
@Mixin(GameRenderer.class)
abstract class GameRenderMixin {

    @Inject(method = "update", at = @At("HEAD"))
    private void moud$frame(DeltaTracker tracker, boolean advanceGameTime, CallbackInfo ci) {
        // the same condition the game renders a level under, so the place is stepped exactly when
        // it used to be and no more often
        if (!advanceGameTime || Minecraft.getInstance().level == null) return;
        ClientPlace.frame(tracker.getGameTimeDeltaPartialTick(true));
    }
}
