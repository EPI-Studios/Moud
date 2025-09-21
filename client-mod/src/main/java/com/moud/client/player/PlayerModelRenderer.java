package com.moud.client.player;

import com.moud.client.animation.AnimationController;
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
import java.util.concurrent.ConcurrentHashMap;

public class PlayerModelRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerModelRenderer.class);
    private final ClientPlayerModelManager modelManager;
    private final MinecraftClient client;
    private final Map<Long, AnimationController> animationControllers = new ConcurrentHashMap<>();

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
        Map<Long, ClientPlayerModelManager.ManagedPlayerModel> models = modelManager.getAllModels();
        for (ClientPlayerModelManager.ManagedPlayerModel model : models.values()) {
            renderModel(model, matrices, vertexConsumers, tickDelta);
        }
    }

    private void renderModel(ClientPlayerModelManager.ManagedPlayerModel model, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (client.world == null || client.player == null) {
            return;
        }

        AnimationController animController = animationControllers.computeIfAbsent(
                model.getModelId(),
                id -> new AnimationController(model)
        );

        animController.tick(tickDelta);

        matrices.push();

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        Vec3d modelPos = model.getPosition();
        matrices.translate(
                modelPos.x - cameraPos.x,
                modelPos.y - cameraPos.y,
                modelPos.z - cameraPos.z
        );

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-model.getYaw() + 180));

        PlayerEntityModel<PlayerEntity> playerModel = model.getModel();
        Identifier skinTexture = model.getSkinTexture();

        if (playerModel == null || skinTexture == null) {
            LOGGER.warn("Model or texture invalid for ID: {}", model.getModelId());
            matrices.pop();
            return;
        }

        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
        matrices.translate(0, -1.5, 0);

        Float scale = model.getScale();
        if (scale != null) {
            matrices.scale(scale, scale, scale);
        }

        float limbAngle = model.getLimbAngle() != null ? model.getLimbAngle() : 0.0F;
        float limbDistance = model.getLimbDistance() != null ? model.getLimbDistance() : 0.0F;
        float ageInTicks = client.player.age + tickDelta;
        float headYaw = model.getYaw();
        Float headPitch = model.getPitch();

        playerModel.handSwingProgress = 0.0f;
        playerModel.riding = false;
        playerModel.child = false;

        PlayerEntity dummyPlayer = client.player;
        playerModel.setAngles(dummyPlayer, limbAngle, limbDistance, ageInTicks, headYaw, headPitch != null ? headPitch : 0.0F);

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(skinTexture));
        int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(modelPos));

        playerModel.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);

        matrices.pop();
    }

    public void onAnimationReceived(long modelId, String animationName) {
        AnimationController controller = animationControllers.get(modelId);
        if (controller != null) {
            controller.playAnimation(animationName);
        }
    }

    public void onModelRemoved(long modelId) {
        animationControllers.remove(modelId);
    }
}