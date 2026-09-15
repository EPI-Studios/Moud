package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.adapter.player.EditorBody;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class EditorLivingMixin {

    @Inject(method = {"isPushable", "canBeSeenByAnyone", "canBeSeenAsEnemy"}, at = @At("HEAD"), cancellable = true)
    private void moud$hidden(CallbackInfoReturnable<Boolean> cir) {
        if (EditorBody.editing((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
