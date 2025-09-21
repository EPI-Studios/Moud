package com.moud.client.cursor;

import com.moud.api.math.Vector3;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CursorRenderer {

    private final MinecraftClient client;
    private static final Logger LOGGER = LoggerFactory.getLogger(CursorRenderer.class);

    public CursorRenderer() {
        this.client = MinecraftClient.getInstance();
    }

    public void render(RemoteCursor cursor, MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        if (cursor.getTexture() == null) {
            LOGGER.info("Cursor texture is null for player {}", cursor.getPlayerId());
            return;
        }

        Camera camera = client.gameRenderer.getCamera();
        Vector3 camPos = new Vector3((float)camera.getPos().x, (float)camera.getPos().y, (float)camera.getPos().z);
        Vector3 cursorPos = cursor.getCurrentPosition();

        float distance = (float)Math.sqrt(
                Math.pow(cursorPos.x - camPos.x, 2) +
                        Math.pow(cursorPos.y - camPos.y, 2) +
                        Math.pow(cursorPos.z - camPos.z, 2)
        );

        LOGGER.info("RENDER: Player={}, CamPos={}, CursorPos={}, Distance={}, Scale={}",
                cursor.getPlayerId(), camPos, cursorPos, distance, cursor.getScale());

        matrices.push();

        matrices.translate(
                cursorPos.x - camPos.x,
                cursorPos.y - camPos.y,
                cursorPos.z - camPos.z
        );

        Vector3 normal = cursor.getCurrentNormal();
        matrices.translate(normal.x * 0.05f, normal.y * 0.05f, normal.z * 0.05f);

        matrices.multiply(camera.getRotation());
        matrices.multiply(new org.joml.Quaternionf().rotateY((float) Math.PI));

        float scale = cursor.getScale() * 0.3f;
        matrices.scale(scale, scale, scale);

        net.minecraft.util.Identifier textureId = cursor.getTexture();
        if (textureId == null) {
            textureId = net.minecraft.util.Identifier.of("textures/block/white_concrete.png");
        }

        RenderLayer renderLayer = RenderLayer.getEntityTranslucent(textureId);
        VertexConsumer consumer = consumers.getBuffer(renderLayer);

        renderQuad(consumer, matrices, cursor);

        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(renderLayer);
        }
    }

    private void renderQuad(VertexConsumer consumer, MatrixStack matrices, RemoteCursor cursor) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();

        float r = (float) cursor.getColor().x;
        float g = (float) cursor.getColor().y;
        float b = (float) cursor.getColor().z;
        float alpha = 1.0f;

        float size = 0.5f;
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int overlay = OverlayTexture.DEFAULT_UV;

        consumer.vertex(positionMatrix, -size, -size, 0).color(r, g, b, alpha).texture(0, 1).overlay(overlay).light(light).normal(0, 0, 1);
        consumer.vertex(positionMatrix, size, -size, 0).color(r, g, b, alpha).texture(1, 1).overlay(overlay).light(light).normal(0, 0, 1);
        consumer.vertex(positionMatrix, size, size, 0).color(r, g, b, alpha).texture(1, 0).overlay(overlay).light(light).normal(0, 0, 1);
        consumer.vertex(positionMatrix, -size, size, 0).color(r, g, b, alpha).texture(0, 0).overlay(overlay).light(light).normal(0, 0, 1);
    }
}