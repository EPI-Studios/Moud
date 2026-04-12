package com.moud.client.fabric.mixin;

import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityCharacterBodyMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void moud$travelWithCharacterBody(Vec3d movementInput, CallbackInfo ci) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null && runtime.handleCharacterBodyTravel((PlayerEntity) (Object) this, movementInput)) {
            ci.cancel();
        }
    }
}
