package com.moud.client.fabric.mixin;

import com.moud.client.fabric.player.PlayerHeadLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelHeadLookMixin {

    @Shadow public ModelPart head;

    @Inject(
            method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void moud$overrideHeadLook(
            LivingEntity entity,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {
        if (!PlayerHeadLook.isActive()) return;
        if (!(entity instanceof AbstractClientPlayerEntity)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || entity != mc.player) return;

        float[] target = PlayerHeadLook.computeAbsoluteYawPitch();
        if (target == null) return;

        float bodyYaw = ((AbstractClientPlayerEntity) entity).getBodyYaw();
        float headLocalYaw = MathHelper.wrapDegrees(target[0] - bodyYaw);
        headLocalYaw = MathHelper.clamp(headLocalYaw, -90f, 90f);
        head.yaw = (float) Math.toRadians(headLocalYaw);
        head.pitch = (float) Math.toRadians(target[1]);
    }
}
