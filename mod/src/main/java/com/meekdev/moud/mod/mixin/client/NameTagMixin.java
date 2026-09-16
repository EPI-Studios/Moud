package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.physics.Bodies;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class NameTagMixin {

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(Entity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (!MoudMod.features().isOn(Feature.NAME_TAGS)) {
            cir.setReturnValue(false);
            return;
        }
        InstanceTree tree = ClientScene.tree();
        if (entity instanceof Player player && tree != null && Bodies.of(tree, player.getUUID().toString()) == null) cir.setReturnValue(false);
    }
}
