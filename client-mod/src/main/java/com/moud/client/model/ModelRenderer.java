package com.moud.client.model;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public class ModelRenderer {

    private static final Identifier RENDER_TYPE_ID = Identifier.of("moud", "model");
    private static final Identifier SHADER_ID = Identifier.of("moud", "model");

    public void render(RenderableModel model, MatrixStack matrices, float tickDelta) {
        VertexArray vertexArray = model.getVertexArray();
        if (vertexArray == null) {
            return;
        }

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vector3 interpolatedPos = model.getInterpolatedPosition(tickDelta);
        double x = interpolatedPos.x - camera.getPos().x;
        double y = interpolatedPos.y - camera.getPos().y;
        double z = interpolatedPos.z - camera.getPos().z;

        matrices.push();
        matrices.translate(x, y, z);

        Quaternion interpolatedRot = model.getInterpolatedRotation(tickDelta);
        matrices.multiply(new org.joml.Quaternionf(interpolatedRot.x, interpolatedRot.y, interpolatedRot.z, interpolatedRot.w));

        Vector3 scale = model.getScale();
        matrices.scale(scale.x, scale.y, scale.z);

        RenderLayer renderType = VeilRenderType.get(RENDER_TYPE_ID, "minecraft:textures/block/white_concrete.png");
        if (renderType == null) {
            matrices.pop();
            return;
        }

        ShaderProgram shader = VeilRenderSystem.setShader(SHADER_ID);
        if (shader != null) {
            Matrix4f viewMatrix = new Matrix4f().rotation(camera.getRotation());

            Matrix4f modelMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
            Matrix4f modelViewMatrix = new Matrix4f(viewMatrix).mul(modelMatrix);

            ShaderUniform modelViewUniform = shader.getUniform("ModelViewMat");
            if (modelViewUniform != null) {
                modelViewUniform.setMatrix(modelViewMatrix, false);
            }

            ShaderUniform normalMatUniform = shader.getUniform("NormalMat");
            if (normalMatUniform != null) {
                org.joml.Matrix3f normalMatrix = new org.joml.Matrix3f(modelViewMatrix).invert().transpose();
                normalMatUniform.setMatrix(normalMatrix, false);
            }

            ShaderUniform projMatUniform = shader.getUniform("ProjMat");
            if (projMatUniform != null) {
                projMatUniform.setMatrix(RenderSystem.getProjectionMatrix(), false);
            }

            int light = WorldRenderer.getLightmapCoordinates(MinecraftClient.getInstance().world, model.getBlockPos());
            ShaderUniform lightmapUniform = shader.getUniform("LightmapCoords");
            if (lightmapUniform != null) {
                lightmapUniform.setInt(light);
            }
            // todo: fix that the model moves with the camera
        }


        vertexArray.bind();
        vertexArray.drawWithRenderType(renderType);
        VertexArray.unbind();

        if (shader != null) {
            ShaderProgram.unbind();
        }

        matrices.pop();
    }
}