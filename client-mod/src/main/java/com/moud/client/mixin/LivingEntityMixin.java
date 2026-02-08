package com.moud.client.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow @Final
    public LimbAnimator limbAnimator;

    @Inject(method = "updateLimbs(F)V", at = @At("HEAD"), cancellable = true)
    private void moud$freezeLegsForPlayerModel(float posDelta, CallbackInfo ci) {
        if ((Object) this instanceof OtherClientPlayerEntity player) {
            GameProfile profile = player.getGameProfile();

            if (profile != null && profile.getName().startsWith("MoudModel_")) {
                ci.cancel();

                this.limbAnimator.updateLimbs(0.0F, 0.4F);
            }
        }
    }
}
