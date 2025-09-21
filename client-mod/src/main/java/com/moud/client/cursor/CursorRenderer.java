package com.moud.client.cursor;

import com.moud.api.math.Vector3;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CursorRenderer {

    private final MinecraftClient client;
    private static final Logger LOGGER = LoggerFactory.getLogger(CursorRenderer.class);
    private static final Identifier FALLBACK_TEXTURE = Identifier.of("minecraft", "textures/block/white_concrete.png");

    public CursorRenderer() {
        this.client = MinecraftClient.getInstance();
    }

    public void render(RemoteCursor cursor, MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();

        Vector3 interpolatedPos = cursor.getInterpolatedPosition(tickDelta);
        if (interpolatedPos.equals(Vector3.zero())) {
            return;
        }

        Vec3d worldPos = new Vec3d(interpolatedPos.x, interpolatedPos.y, interpolatedPos.z);
        Vector3f relativePos = worldPos.subtract(cameraPos).toVector3f();

        double distance = cameraPos.distanceTo(worldPos);
        if (distance > 100.0) {
            return;
        }

        matrices.push();

        matrices.translate(relativePos.x, relativePos.y, relativePos.z);

        Vector3 normal = cursor.getInterpolatedNormal(tickDelta);
        matrices.translate(normal.x * 0.02f, normal.y * 0.02f, normal.z * 0.02f);

        matrices.multiply(camera.getRotation());
        matrices.multiply(new org.joml.Quaternionf().rotateY((float) Math.PI));

        float scale = cursor.getScale() * 1.0f;
        matrices.scale(scale, scale, scale);

        Identifier textureId = cursor.getTexture();
        if (textureId == null) {
            textureId = FALLBACK_TEXTURE;
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

        Vector3 color = cursor.getColor();
        float r = (float) color.x;
        float g = (float) color.y;
        float b = (float) color.z;
        float alpha = 0.9f;

        float size = 0.5f;
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int overlay = OverlayTexture.DEFAULT_UV;

        consumer.vertex(positionMatrix, -size, -size, 0).color(r, g, b, alpha).texture(0, 1).overlay(overlay).light(light).normal(0, 0, 1);
        consumer.vertex(positionMatrix, size, -size, 0).color(r, g, b, alpha).texture(1, 1).overlay(overlay).light(light).normal(0, 0, 1);
        consumer.vertex(positionMatrix, size, size, 0).color(r, g, b, alpha).texture(1, 0).overlay(overlay).light(light).normal(0, 0, 1);
        consumer.vertex(positionMatrix, -size, size, 0).color(r, g, b, alpha).texture(0, 0).overlay(overlay).light(light).normal(0, 0, 1);
    }
}