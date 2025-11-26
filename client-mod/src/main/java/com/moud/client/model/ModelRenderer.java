package com.moud.client.model;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.rendering.MeshBuffer;
import com.moud.client.rendering.ModelRenderLayers;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class ModelRenderer {

    private static final boolean USE_VERTEX_BUFFERS = false;

    public void render(RenderableModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta) {
        if (consumers == null || !model.hasMeshData()) {
            return;
        }

        if (USE_VERTEX_BUFFERS && model.hasMeshBuffer()) {
            renderWithVertexBuffer(model, matrices, consumers, light, tickDelta);
        } else {

            renderImmediateMode(model, matrices, consumers, light, tickDelta);
        }
    }

    private void renderWithVertexBuffer(RenderableModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta) {
        MeshBuffer meshBuffer = model.getMeshBuffer();
        if (meshBuffer == null || !meshBuffer.isUploaded()) {
            return;
        }

        matrices.push();

        Quaternion interpolatedRot = model.getInterpolatedRotation(tickDelta);
        Quaternionf modelRotation = new Quaternionf(interpolatedRot.x, interpolatedRot.y, interpolatedRot.z, interpolatedRot.w);
        matrices.multiply(modelRotation);

        Vector3 scale = model.getInterpolatedScale(tickDelta);
        matrices.scale(scale.x, scale.y, scale.z);

        Identifier shaderId = Identifier.of("moud", "model_phong");
        ShaderProgram shader = VeilRenderSystem.renderer().getShaderManager().getShader(shaderId);

        if (shader != null) {
            RenderLayer renderLayer = ModelRenderLayers.getModelLayer(model.getTexture());

            renderLayer.startDrawing();

            shader.bind();

            Matrix4f modelViewMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
            Matrix4f projectionMatrix = new Matrix4f();
            projectionMatrix.setPerspective(
                    (float) Math.toRadians(70.0f),
                    (float) MinecraftClient.getInstance().getWindow().getFramebufferWidth() /
                            (float) MinecraftClient.getInstance().getWindow().getFramebufferHeight(),
                    0.05f,
                    1000.0f
            );

            shader.setDefaultUniforms(VertexFormat.DrawMode.TRIANGLES, modelViewMatrix, projectionMatrix);

            VertexBuffer vertexBuffer = meshBuffer.getVertexBuffer();
            vertexBuffer.bind();
            vertexBuffer.draw();
            VertexBuffer.unbind();

            ShaderProgram.unbind();

            renderLayer.endDrawing();
        }

        matrices.pop();
    }

    private void renderImmediateMode(RenderableModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta) {
        matrices.push();

        Quaternion interpolatedRot = model.getInterpolatedRotation(tickDelta);
        Quaternionf modelRotation = new Quaternionf(interpolatedRot.x, interpolatedRot.y, interpolatedRot.z, interpolatedRot.w);
        matrices.multiply(modelRotation);

        Vector3 scale = model.getInterpolatedScale(tickDelta);
        matrices.scale(scale.x, scale.y, scale.z);

        RenderLayer renderLayer = ModelRenderLayers.getModelLayer(model.getTexture());
        VertexConsumer vertexConsumer = consumers.getBuffer(renderLayer);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();

        float[] vertices = model.getVertices();
        int[] indices = model.getIndices();

        if (vertices != null && indices != null) {
            final int stride = RenderableModel.FLOATS_PER_VERTEX;
            final int overlayUV = net.minecraft.client.render.OverlayTexture.DEFAULT_UV;

            for (int i = 0; i < indices.length; i++) {
                int base = indices[i] * stride;

                float x = vertices[base];
                float y = vertices[base + 1];
                float z = vertices[base + 2];
                float u = vertices[base + 3];
                float v = vertices[base + 4];
                float nx = vertices[base + 5];
                float ny = vertices[base + 6];
                float nz = vertices[base + 7];

                vertexConsumer.vertex(positionMatrix, x, y, z)
                        .color(255, 255, 255, 255)
                        .texture(u, v)
                        .overlay(overlayUV)
                        .light(light)
                        .normal(entry, nx, ny, nz);
            }
        }

        matrices.pop();
    }
}
