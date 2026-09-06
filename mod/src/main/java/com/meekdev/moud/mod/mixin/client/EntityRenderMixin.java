package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// suppresses entityRendering and playerModel
// fabric has no event for whether an entity draws, and amnetic only decorates renderers it does not skip them
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        Feature feature = entity instanceof Player ? Feature.PLAYER_MODEL : Feature.ENTITY_RENDERING;
        if (!MoudMod.features().isOn(feature)) cir.setReturnValue(false);
    }
}
