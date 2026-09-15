package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.adapter.player.EditorBody;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EditorEntityMixin {

    @Unique
    private boolean moud$editor() {
        return EditorBody.editing((Entity) (Object) this);
    }

    @Inject(method = "onBelowWorld", at = @At("HEAD"), cancellable = true)
    private void moud$noVoid(CallbackInfo ci) {
        if (moud$editor()) ci.cancel();
    }

    @Inject(method = "isNoGravity", at = @At("HEAD"), cancellable = true)
    private void moud$floats(CallbackInfoReturnable<Boolean> cir) {
        if (moud$editor()) cir.setReturnValue(true);
    }

    @Inject(method = {"isPickable", "isPushable", "isAttackable"}, at = @At("HEAD"), cancellable = true)
    private void moud$intangible(CallbackInfoReturnable<Boolean> cir) {
        if (moud$editor()) cir.setReturnValue(false);
    }

    @Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
    private void moud$passThrough(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (moud$editor()) cir.setReturnValue(false);
    }

    @Inject(method = "isIgnoringBlockTriggers", at = @At("HEAD"), cancellable = true)
    private void moud$noTriggers(CallbackInfoReturnable<Boolean> cir) {
        if (moud$editor()) cir.setReturnValue(true);
    }
}
