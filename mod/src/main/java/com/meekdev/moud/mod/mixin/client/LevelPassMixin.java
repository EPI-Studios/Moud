package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.gl.RenderState;
import com.meekdev.moud.mod.adapter.render.EditorView;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class LevelPassMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void moud$enterLevel(CallbackInfo ci) {
        EditorView.level(true);
        RenderState.geometryLines(EditorView.lines());
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void moud$leaveLevel(CallbackInfo ci) {
        EditorView.level(false);
        RenderState.geometryLines(false);
    }
}
