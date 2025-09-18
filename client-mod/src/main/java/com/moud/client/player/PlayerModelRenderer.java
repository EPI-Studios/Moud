package com.moud.client.player;

import com.moud.client.player.ClientPlayerModelManager.ManagedPlayerModel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.LightType;
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

        matrices.translate(
                model.getPosition().x - client.gameRenderer.getCamera().getPos().x,
                model.getPosition().y - client.gameRenderer.getCamera().getPos().y,
                model.getPosition().z - client.gameRenderer.getCamera().getPos().z
        );

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-model.getYaw()));

        matrices.scale(1.0f, 1.0f, 1.0f);

        PlayerEntityModel<PlayerEntity> playerModel = model.getModel();
        Identifier skinTexture = model.getSkinTexture();

        int blockX = (int) model.getPosition().x;
        int blockY = (int) model.getPosition().y;
        int blockZ = (int) model.getPosition().z;

        int blockLight = client.world.getLightLevel(LightType.BLOCK,
                new net.minecraft.util.math.BlockPos(blockX, blockY, blockZ));
        int skyLight = client.world.getLightLevel(LightType.SKY,
                new net.minecraft.util.math.BlockPos(blockX, blockY, blockZ));

        int packedLight = LightmapTextureManager.pack(blockLight, skyLight);

        if (playerModel != null) {
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(skinTexture));

            playerModel.render(matrices, vertexConsumer, packedLight, OverlayTexture.DEFAULT_UV);
        }

        matrices.pop();
    }
}