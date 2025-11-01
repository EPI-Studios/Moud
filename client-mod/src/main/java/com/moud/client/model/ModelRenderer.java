package com.moud.client.model;

import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRenderer.class);
    private static final Identifier RENDER_TYPE_ID = Identifier.of("moud", "model");
    private static final Identifier SHADER_ID = Identifier.of("moud", "model");

    public void render(RenderableModel model, MatrixStack matrices, float tickDelta) {
        VertexArray vertexArray = model.getVertexArray();
        if (vertexArray == null) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = mc.gameRenderer.getCamera();
        GameRenderer gameRenderer = mc.gameRenderer;


        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._depthFunc(GL11.GL_LEQUAL);

        matrices.push();

        Quaternion interpolatedRot = model.getInterpolatedRotation(tickDelta);
        matrices.multiply(new Quaternionf(interpolatedRot.x, interpolatedRot.y, interpolatedRot.z, interpolatedRot.w));

        Vector3 scale = model.getScale();
        matrices.scale(scale.x, scale.y, scale.z);

        RenderLayer renderType = VeilRenderType.get(RENDER_TYPE_ID, "minecraft:textures/block/white_concrete.png");
        if (renderType == null) {
            matrices.pop();
            return;
        }

        ShaderProgram shader = VeilRenderSystem.setShader(SHADER_ID);
        if (shader != null) {

            Matrix4f cleanViewMatrix = new Matrix4f();
            cleanViewMatrix.rotate(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            cleanViewMatrix.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

            cleanViewMatrix.translate((float)-camera.getPos().x, (float)-camera.getPos().y, (float)-camera.getPos().z);

            Matrix4f modelMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
            Matrix4f modelViewMatrix = new Matrix4f(cleanViewMatrix).mul(modelMatrix);

            ShaderUniform modelViewUniform = shader.getUniform("ModelViewMat");
            if (modelViewUniform != null) {
                modelViewUniform.setMatrix(modelViewMatrix, false);
            }

            ShaderUniform normalMatUniform = shader.getUniform("NormalMat");
            if (normalMatUniform != null) {
                Matrix3f normalMatrix = new Matrix3f(modelViewMatrix).invert().transpose();
                normalMatUniform.setMatrix(normalMatrix, false);
            }


            double fov = mc.options.getFov().getValue();
            float aspectRatio = (float) mc.getWindow().getFramebufferWidth() / (float) mc.getWindow().getFramebufferHeight();
            float viewDistance = gameRenderer.getViewDistance();

            Matrix4f cleanProjMatrix = new Matrix4f().setPerspective(
                (float) Math.toRadians(fov),
                aspectRatio,
                GameRenderer.CAMERA_DEPTH,
                viewDistance
            );

            ShaderUniform projMatUniform = shader.getUniform("ProjMat");
            if (projMatUniform != null) {
                projMatUniform.setMatrix(cleanProjMatrix, false);
            }

            int light = WorldRenderer.getLightmapCoordinates(MinecraftClient.getInstance().world, model.getBlockPos());
            ShaderUniform lightmapUniform = shader.getUniform("LightmapCoords");
            if (lightmapUniform != null) {
                lightmapUniform.setInt(light);
            }
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