package com.moud.client.mixin;

import com.moud.client.movement.ClientMovementTracker;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMovementMixin {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void moud$cancelVanillaTravel(Vec3d movementInput, CallbackInfo ci) {
        // cancel mouvement when prediction is enabled
        if ((Object) this instanceof ClientPlayerEntity) {
            if (ClientMovementTracker.getInstance().isPredictionEnabled()) {
                ci.cancel();
            }
        }
    }
}
