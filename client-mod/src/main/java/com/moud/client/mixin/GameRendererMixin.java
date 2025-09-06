package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

//@Mixin(GameRenderer.class)
//public class GameRendererMixin {
//
//    @ModifyArg(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/RotationAxis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 2))
//    private float modifyYawRotation(float yaw) {
//        if (MoudClientMod.isSmoothCameraActive()) {
//            return MoudClientMod.getSmoothYaw();
//        }
//        return yaw;
//    }
//
//    @ModifyArg(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/RotationAxis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 3))
//    private float modifyPitchRotation(float pitch) {
//        if (MoudClientMod.isSmoothCameraActive()) {
//            return MoudClientMod.getSmoothPitch();
//        }
//        return pitch;
//    }
//}
