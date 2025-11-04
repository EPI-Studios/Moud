package com.moud.client.model;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class ModelRenderer {
    private static final int FLOATS_PER_VERTEX = 8; // position(3) + uv(2) + normal(3)

    public void render(RenderableModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta) {
        if (consumers == null || !model.hasMeshData()) {
            return;
        }

        RenderLayer renderLayer = ModelRenderLayers.getTriangleLayer(model.getTexture());
        VertexConsumer vertexConsumer = consumers.getBuffer(renderLayer);

        matrices.push();

        Quaternion interpolatedRot = model.getInterpolatedRotation(tickDelta);
        Quaternionf modelRotation = new Quaternionf(interpolatedRot.x, interpolatedRot.y, interpolatedRot.z, interpolatedRot.w);
        matrices.multiply(modelRotation);

        Vector3 scale = model.getScale();
        matrices.scale(scale.x, scale.y, scale.z);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();
        float[] vertices = model.getVertices();
        int[] indices = model.getIndices();

        for (int index : indices) {
            int base = index * FLOATS_PER_VERTEX;
            float x = vertices[base];
            float y = vertices[base + 1];
            float z = vertices[base + 2];
            float u = vertices[base + 3];
            float v = vertices[base + 4];
            float nx = vertices[base + 5];
            float ny = vertices[base + 6];
            float nz = vertices[base + 7];

            vertexConsumer.vertex(positionMatrix, x, y, z)
                    .color(1.0f, 1.0f, 1.0f, 1.0f)
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, nx, ny, nz);

        }

        matrices.pop();
    }
}
