package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import com.moud.client.editor.camera.EditorCameraController;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
//    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
//    private void moud_allowEntityRenderingInCameraMode(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
//        if (MoudClientMod.isCustomCameraActive() || EditorCameraController.getInstance().isActive()) {
//            cir.setReturnValue(true);
//        }
//    }
}
