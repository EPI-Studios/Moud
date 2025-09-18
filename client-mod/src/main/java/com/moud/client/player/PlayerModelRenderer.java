package com.moud.client.player;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
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
        Map<Long, ? extends ManagedPlayerModel> models = modelManager.getAllModels();
        for (ManagedPlayerModel model : models.values()) {
            renderModel(model, matrices, vertexConsumers, tickDelta);
        }
    }

    private void renderModel(ManagedPlayerModel model, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (client.world == null || client.player == null) {
            return;
        }

        matrices.push();

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
//        Vec3d modelPos = modelet moi mtn.getPosition();
//        matrices.translate(
//                modelPos.x - cameraPos.x,
//                modelPos.y - cameraPos.y,
//                modelPos.z - cameraPos.z
//        );

//        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-model.getYaw() + 180));
//
//        PlayerEntityModel<PlayerEntity> playerModel = model.getModel();
//        Identifier skinTexture = model.getSkinTexture();
//
//        if (playerModel == null || skinTexture == null) {
//            LOGGER.warn("Modèle ou texture invalide pour l'ID : {}", model.getId());
//            matrices.pop();
//            return;
//        }
//
//        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
//        matrices.translate(0, -1.5, 0);
//
//        Float scale = model.getScale();
//        if (scale != null) {
//            matrices.scale(scale, scale, scale);
//        }
//
//        float limbAngle = model.getLimbAngle() != null ? model.getLimbAngle() : 0.0F;
//        float limbDistance = model.getLimbDistance() != null ? model.getLimbDistance() : 0.0F;
//        float ageInTicks = client.player.age + tickDelta;
//        float headYaw = model.getYaw();
//        Float headPitch = model.getPitch();
//
//        playerModel.handSwingProgress = 0.0f;
//        playerModel.riding = false;
//        playerModel.child = false;
//
//        // **** LA CORRECTION EST ICI ****
//        // On remplace 'null' par 'client.player' pour éviter le crash.
//        playerModel.setAngles(client.player, limbAngle, limbDistance, ageInTicks, headYaw, headPitch != null ? headPitch : 0.0F);
//
//        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(skinTexture));
//        int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(modelPos));
//
//        playerModel.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);

        matrices.pop();
    }
}