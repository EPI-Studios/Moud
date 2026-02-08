package com.moud.client.mixin;

import com.mojang.authlib.GameProfile;
import com.moud.client.mixin.accessor.LimbAnimatorAccessor;
import com.moud.client.mixin.accessor.LivingEntityAccessor;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.LimbAnimator;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OtherClientPlayerEntity.class)
public abstract class OtherClientPlayerEntityMixin extends PlayerEntity {

    private OtherClientPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile profile) {
        super(world, pos, yaw, profile);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void moud$freezeLimbAnimatorForFakeModels(CallbackInfo ci) {
        String name = this.getGameProfile().getName();

        if (name != null && name.startsWith("MoudModel_")) {
            LimbAnimator animator = ((LivingEntityAccessor) this).getLimbAnimator();

            if (animator != null) {
                LimbAnimatorAccessor acc = (LimbAnimatorAccessor) animator;
                acc.moud$setSpeedRaw(0f);
                acc.moud$setPrevSpeedRaw(0f);
                acc.moud$setPosRaw(0f);
            }
        }
    }
}