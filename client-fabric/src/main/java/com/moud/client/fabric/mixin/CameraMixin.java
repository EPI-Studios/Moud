package com.moud.client.fabric.mixin;

import com.moud.client.fabric.platform.MinecraftFreeflyCamera;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public final class CameraMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void moud$update(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        EditorContext ctx = EditorOverlayBus.get();
        if (ctx == null) {
            return;
        }
        MinecraftFreeflyCamera freefly = ctx.camera();
        if (!freefly.isEnabled()) {
            return;
        }
        freefly.updateRenderState();
        freefly.applyToCamera((Camera) (Object) this);
    }
}
