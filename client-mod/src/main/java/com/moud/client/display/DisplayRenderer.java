package com.moud.client.display;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class DisplayRenderer {
    private static final float EPSILON = 1.0e-4f;

    public void render(DisplaySurface surface, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta) {
        Identifier texture = surface.resolveTexture(tickDelta);
        if (texture == null || consumers == null) {
            return;
        }

        RenderLayer layer = DisplayRenderLayers.getLayer(texture);
        VertexConsumer vertexConsumer = consumers.getBuffer(layer);

        matrices.push();

        Quaternion rotation = surface.getRotation();
        Quaternionf rotationQuat = new Quaternionf(rotation.x, rotation.y, rotation.z, rotation.w);
        matrices.multiply(rotationQuat);

        Vector3 scale = surface.getScale();
        float sx = Math.max(scale.x, EPSILON);
        float sy = Math.max(scale.y, EPSILON);
        float sz = Math.max(scale.z, EPSILON);
        matrices.scale(sx, sy, sz);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();

        addQuad(vertexConsumer, positionMatrix, entry, light);

        matrices.pop();
    }

    private void addQuad(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry, int light) {
        float halfWidth = 0.5f;
        float halfHeight = 0.5f;
        float depth = 0.0f;

        // Vertex order: TL, TR, BR, BL
        writeVertex(consumer, matrix, entry, -halfWidth, halfHeight, depth, 0.0f, 0.0f, light);
        writeVertex(consumer, matrix, entry, halfWidth, halfHeight, depth, 1.0f, 0.0f, light);
        writeVertex(consumer, matrix, entry, halfWidth, -halfHeight, depth, 1.0f, 1.0f, light);
        writeVertex(consumer, matrix, entry, -halfWidth, -halfHeight, depth, 0.0f, 1.0f, light);
    }

    private void writeVertex(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                             float x, float y, float z, float u, float v, int light) {
        consumer.vertex(matrix, x, y, z)
                .color(1.0f, 1.0f, 1.0f, 1.0f)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0.0f, 0.0f, -1.0f);
    }
}
