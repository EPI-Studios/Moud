package com.moud.client.animation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public class PlayerModelRenderer {

    public void render(AnimatedPlayerModel animatedModel, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float partialTick) {
        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();

        animatedModel.setupAnim(partialTick);

        matrices.push();

        var player = animatedModel.getFakePlayer();
        matrices.translate(player.getX(), player.getY(), player.getZ());

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - player.getYaw()));

        dispatcher.render(player, 0, 0, 0, 0, partialTick, matrices, vertexConsumers, light);

        matrices.pop();
    }
}