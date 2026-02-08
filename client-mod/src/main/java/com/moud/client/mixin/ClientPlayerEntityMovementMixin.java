package com.moud.client.mixin;

import com.moud.client.movement.ClientMovementTracker;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMovementMixin {

    @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
    private void moud$cancelVanillaMovementPackets(CallbackInfo ci) {
        if (ClientMovementTracker.getInstance().isPredictionEnabled()) {
            ci.cancel();
        }
    }

}
