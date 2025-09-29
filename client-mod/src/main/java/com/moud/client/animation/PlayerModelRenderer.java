package com.moud.client.animation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerModelRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerModelRenderer.class);

    public void render(AnimatedPlayerModel animatedModel, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float partialTick) {
        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();

        var player = animatedModel.getFakePlayer();
        player.setPos(
                animatedModel.getInterpolatedX(partialTick),
                animatedModel.getInterpolatedY(partialTick),
                animatedModel.getInterpolatedZ(partialTick)
        );
        player.setYaw(animatedModel.getInterpolatedYaw(partialTick));
        player.setPitch(animatedModel.getInterpolatedPitch(partialTick));
        player.headYaw = player.getYaw();
        player.bodyYaw = player.getYaw();

        animatedModel.setupAnim(partialTick);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - animatedModel.getInterpolatedYaw(partialTick)));

        try {
            EntityRenderer<?> renderer = dispatcher.getRenderer(player);
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                playerRenderer.render(player, 0, partialTick, matrices, vertexConsumers, light);
            } else {
                dispatcher.render(player, 0, 0, 0, 0, partialTick, matrices, vertexConsumers, light);
            }
        } catch (Exception e) {
            LOGGER.error("Error rendering player model", e);
        }

        matrices.pop();
    }
}