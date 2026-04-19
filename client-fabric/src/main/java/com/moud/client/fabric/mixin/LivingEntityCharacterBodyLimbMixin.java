package com.moud.client.fabric.mixin;

import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCharacterBodyLimbMixin {

    @Inject(method = "updateLimbs(Z)V", at = @At("HEAD"))
    private void moud$fakePrevPosForLimbs(boolean flutter, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || self != mc.player) {
            return;
        }
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isCharacterBodyDriving()) {
            return;
        }
        double vx = runtime.characterBodyVelX();
        double vy = runtime.characterBodyVelY();
        double vz = runtime.characterBodyVelZ();
        self.prevX = self.getX() - vx;
        self.prevZ = self.getZ() - vz;
        if (flutter) {
            self.prevY = self.getY() - vy;
        }
    }
}
