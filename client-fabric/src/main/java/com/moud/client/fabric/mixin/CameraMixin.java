package com.moud.client.fabric.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Camera.class)
public final class CameraMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void moud$update(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        EditorContext ctx = EditorOverlayBus.get();
        if (ctx != null) {
            MinecraftFreeflyCamera freefly = ctx.camera();
            if (freefly.isEnabled()) {
                freefly.updateRenderState();
                freefly.applyToCamera((Camera) (Object) this);
                return;
            }
        }

        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null && runtime.isActive()) {
            runtime.applyCameraOverride((Camera) (Object) this);
        }
    }
}
