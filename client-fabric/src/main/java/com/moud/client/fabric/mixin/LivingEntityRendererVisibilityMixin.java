package com.moud.client.fabric.mixin;

import com.moud.client.fabric.player.PlayerBodyScale;
import com.moud.client.fabric.player.PlayerBodyVisibility;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererVisibilityMixin {

    @Unique private boolean[] moud$savedVisible;
    @Unique private float[] moud$savedPartScale;
    @Unique private boolean moud$pushedMatrix;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <T extends LivingEntity> void moud$beforeRender(
            T entity, float yaw, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;
        String uuid = player.getUuidAsString();

        if (!PlayerBodyVisibility.isBodyVisible(uuid)) {
            ci.cancel();
            return;
        }

        float[] whole = PlayerBodyScale.getWhole(uuid);
        if (whole != null) {
            matrices.push();
            matrices.scale(whole[0], whole[1], whole[2]);
            moud$pushedMatrix = true;
        }

        Object rawModel = ((LivingEntityRenderer<?, ?>)(Object) this).getModel();
        if (!(rawModel instanceof PlayerEntityModel model)) return;

        ModelPart[] parts = moud$parts(model);
        String[] names = moud$partNames();
        moud$savedVisible = new boolean[parts.length];
        moud$savedPartScale = new float[parts.length * 3];

        for (int i = 0; i < parts.length; i++) {
            moud$savedVisible[i] = parts[i].visible;
            moud$savedPartScale[i * 3]     = parts[i].xScale;
            moud$savedPartScale[i * 3 + 1] = parts[i].yScale;
            moud$savedPartScale[i * 3 + 2] = parts[i].zScale;

            if (!PlayerBodyVisibility.isPartVisible(uuid, names[i])) {
                parts[i].visible = false;
            }

            float s = PlayerBodyScale.partScale(uuid, names[i]);
            if (s != 1f) {
                parts[i].xScale *= s;
                parts[i].yScale *= s;
                parts[i].zScale *= s;
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <T extends LivingEntity> void moud$afterRender(
            T entity, float yaw, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            CallbackInfo ci) {
        if (moud$pushedMatrix) {
            matrices.pop();
            moud$pushedMatrix = false;
        }

        if (moud$savedVisible == null) return;

        if (!(entity instanceof AbstractClientPlayerEntity)) {
            moud$savedVisible = null;
            moud$savedPartScale = null;
            return;
        }

        Object rawModel = ((LivingEntityRenderer<?, ?>)(Object) this).getModel();
        if (rawModel instanceof PlayerEntityModel model) {
            ModelPart[] parts = moud$parts(model);
            int n = Math.min(parts.length, moud$savedVisible.length);
            for (int i = 0; i < n; i++) {
                parts[i].visible = moud$savedVisible[i];
                parts[i].xScale = moud$savedPartScale[i * 3];
                parts[i].yScale = moud$savedPartScale[i * 3 + 1];
                parts[i].zScale = moud$savedPartScale[i * 3 + 2];
            }
        }

        moud$savedVisible = null;
        moud$savedPartScale = null;
    }

    @Unique
    private static ModelPart[] moud$parts(PlayerEntityModel m) {
        return new ModelPart[]{
                m.head, m.hat, m.body,
                m.rightArm, m.leftArm,
                m.rightLeg, m.leftLeg,
                m.jacket, m.rightSleeve, m.leftSleeve,
                m.rightPants, m.leftPants
        };
    }

    @Unique
    private static String[] moud$partNames() {
        return new String[]{
                "head", "hat", "body",
                "right_arm", "left_arm",
                "right_leg", "left_leg",
                "jacket", "right_sleeve", "left_sleeve",
                "right_pants", "left_pants"
        };
    }
}