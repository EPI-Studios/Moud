package com.moud.client.rendering.mesh;

import com.moud.api.math.Vector3;
import com.moud.api.rendering.mesh.Mesh;
import com.moud.api.rendering.mesh.MeshPart;
import com.moud.api.rendering.mesh.MeshVertex;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class ClientMeshRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientMeshRenderer.class);
    private static final Identifier VEIL_BUFFER_ID = Identifier.of("moud", "mesh_proxies");

    private boolean veilBuffersEnabled;

    public void beginFrame() {
        if (!veilBuffersEnabled) {
            try {
                VeilRenderSystem.renderer().enableBuffers(VEIL_BUFFER_ID,
                        DynamicBufferType.ALBEDO, DynamicBufferType.NORMAL);
                veilBuffersEnabled = true;
            } catch (Exception e) {
                LOGGER.warn("Failed to enable Veil buffers for mesh rendering", e);
            }
        }
    }

    public void disableVeilBuffers() {
        if (veilBuffersEnabled) {
            try {
                VeilRenderSystem.renderer().disableBuffers(VEIL_BUFFER_ID,
                        DynamicBufferType.ALBEDO, DynamicBufferType.NORMAL);
            } catch (Exception e) {
                LOGGER.warn("Failed to disable Veil buffers for mesh rendering", e);
            }
            veilBuffersEnabled = false;
        }
    }

    public void render(ClientMeshInstance instance, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        beginFrame();

        Mesh mesh = instance.getMesh();
        if (mesh == null) {
            return;
        }

        Vector3 rotation = instance.getRotation();
        Vector3 scale = instance.getScale();

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation.x));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation.y));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation.z));
        matrices.scale(scale.x, scale.y, scale.z);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();

        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getSolid());

        for (MeshPart part : mesh.getParts()) {
            emitPart(consumer, positionMatrix, normalMatrix, light, part.getVertices(), part.getIndices());
        }

        matrices.pop();
    }

    private void emitPart(VertexConsumer consumer, Matrix4f matrix, Matrix3f normalMatrix, int light,
                          List<MeshVertex> vertices, int[] indices) {
        if (vertices.isEmpty()) {
            return;
        }

        if (indices != null && indices.length >= 3) {
            int max = indices.length - (indices.length % 3);
            for (int i = 0; i < max; i += 3) {
                emitVertex(consumer, matrix, normalMatrix, light, vertices.get(indices[i]));
                emitVertex(consumer, matrix, normalMatrix, light, vertices.get(indices[i + 1]));
                emitVertex(consumer, matrix, normalMatrix, light, vertices.get(indices[i + 2]));
            }
        } else {
            int max = vertices.size() - (vertices.size() % 3);
            for (int i = 0; i < max; i += 3) {
                emitVertex(consumer, matrix, normalMatrix, light, vertices.get(i));
                emitVertex(consumer, matrix, normalMatrix, light, vertices.get(i + 1));
                emitVertex(consumer, matrix, normalMatrix, light, vertices.get(i + 2));
            }
        }
    }

    private void emitVertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normalMatrix, int light, MeshVertex vertex) {
        Vector3 position = vertex.getPosition();
        Vector3 normal = vertex.getNormal();
        Vector3f transformedNormal = new Vector3f(normal.x, normal.y, normal.z);
        transformedNormal.mul(normalMatrix);
        consumer.vertex(matrix, position.x, position.y, position.z)
                .color(255, 255, 255, 255)
                .texture(vertex.getU(), vertex.getV())
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(transformedNormal.x(), transformedNormal.y(), transformedNormal.z());
    }
}
