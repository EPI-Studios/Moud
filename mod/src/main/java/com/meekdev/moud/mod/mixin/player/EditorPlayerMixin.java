package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.adapter.player.EditorBody;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class EditorPlayerMixin implements EditorBody {

    @Unique
    private boolean moud$editing;

    @Override
    public boolean moud$editing() {
        return moud$editing;
    }

    @Override
    public void moud$setEditing(boolean editing) {
        Player self = (Player) (Object) this;
        moud$editing = editing;
        self.noPhysics = editing;
        self.fallDistance = 0;
        self.setDeltaMovement(Vec3.ZERO);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void moud$hold(CallbackInfo ci) {
        if (!moud$editing) return;
        Player self = (Player) (Object) this;
        self.noPhysics = true;
        self.fallDistance = 0;
        self.setDeltaMovement(Vec3.ZERO);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void moud$still(Vec3 movement, CallbackInfo ci) {
        if (moud$editing) ci.cancel();
    }

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void moud$untouchable(ServerLevel level, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (moud$editing) cir.setReturnValue(true);
    }

    @Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
    private void moud$unseen(CallbackInfoReturnable<Boolean> cir) {
        if (moud$editing) cir.setReturnValue(false);
    }

    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
    private void moud$dry(CallbackInfoReturnable<Boolean> cir) {
        if (moud$editing) cir.setReturnValue(false);
    }
}
