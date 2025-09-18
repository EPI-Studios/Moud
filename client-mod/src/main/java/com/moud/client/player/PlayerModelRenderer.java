package com.moud.client.player;

import com.moud.client.player.ClientPlayerModelManager.ManagedPlayerModel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PlayerModelRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerModelRenderer.class);
    private final ClientPlayerModelManager modelManager;
    private final MinecraftClient client;

    public PlayerModelRenderer() {
        this.modelManager = ClientPlayerModelManager.getInstance();
        this.client = MinecraftClient.getInstance();
        registerRenderEvents();
    }

    private void registerRenderEvents() {
        WorldRenderEvents.AFTER_ENTITIES.register((context) -> {
            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider vertexConsumers = context.consumers();
            float tickDelta = context.tickCounter().getTickDelta(true);

            renderAllModels(matrices, vertexConsumers, tickDelta);
        });
    }

    private void renderAllModels(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        Map<Long, ManagedPlayerModel> models = modelManager.getAllModels();

        for (ManagedPlayerModel model : models.values()) {
            renderModel(model, matrices, vertexConsumers, tickDelta);
        }
    }

    private void renderModel(ManagedPlayerModel model, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (client.world == null || client.player == null) return;

        matrices.push();

        matrices.translate(model.getPosition().x, model.getPosition().y, model.getPosition().z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-model.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(model.getPitch()));

        PlayerEntityModel<PlayerEntity> playerModel = model.getModel();
        Identifier skinTexture = model.getSkinTexture();

        int light = client.world.getLightLevel(
                net.minecraft.util.math.BlockPos.ofFloored(model.getPosition().x, model.getPosition().y, model.getPosition().z)
        );

        playerModel.render(matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntitySolid(skinTexture)),
                light,
                OverlayTexture.DEFAULT_UV
        );

        matrices.pop();
    }
}