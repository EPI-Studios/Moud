package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.editor.ImGuiFrames;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class ImGuiFrameMixin {

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void moud$imguiFrame(boolean advanceGameTime, CallbackInfo ci) {
        ImGuiFrames.renderFrame();
    }
}
