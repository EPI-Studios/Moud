package com.moud.client.fabric.render.scene.subrender;

import com.moud.client.fabric.render.Model3DRenderer;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.net.protocol.SceneSnapshot;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4fc;

public final class FallbackMeshRenderer {
    @FunctionalInterface
    public interface NodeShaderRenderer {
        boolean render(SceneSnapshot.NodeSnapshot node,
                       Pose world,
                       Vec3d camPos,
                       Camera camera,
                       Matrix4fc frustumMatrix,
                       Matrix4fc projectionMatrix,
                       MinecraftClient client,
                       float tickDelta);
    }

    public void render(List<SceneSnapshot.NodeSnapshot> nodes,
                       Function<Long, Pose> poseResolver,
                       NodeShaderRenderer nodeShaderRenderer,
                       Function<SceneSnapshot.NodeSnapshot, Identifier> textureResolver,
                       VertexConsumerProvider.Immediate consumers,
                       MatrixStack matrices,
                       Vec3d camPos,
                       Camera camera,
                       Matrix4fc frustumMatrix,
                       Matrix4fc projectionMatrix,
                       MinecraftClient client,
                       float tickDelta,
                       Text3DRenderer textRenderer) {
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null || !NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "visible"), true)) {
                continue;
            }

            String type = node.type();
            if ("Model3D".equals(type)) {
                Pose world = poseResolver.apply(node.nodeId());
                if (world == null) {
                    continue;
                }
                int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(world.pos.x, world.pos.y, world.pos.z));
                if (light <= 0) {
                    light = 0x00F000F0;
                }
                matrices.push();
                matrices.translate(world.pos.x - camPos.x, world.pos.y - camPos.y, world.pos.z - camPos.z);
                matrices.multiply(world.rot);
                matrices.scale(world.scale.x, world.scale.y, world.scale.z);
                Model3DRenderer.render(consumers, matrices, node, light);
                matrices.pop();
                continue;
            }

            if ("Text3D".equals(type)) {
                textRenderer.render(node, poseResolver.apply(node.nodeId()), consumers, matrices, camPos, camera, client);
                continue;
            }

            if (!"MeshInstance3D".equals(type) && !"CSGBox".equals(type) && !"Sprite3D".equals(type) && !"AnimatedSprite3D".equals(type)) {
                continue;
            }
            if (isViewmodelInPlay(node)) {
                continue;
            }

            String materialPath = NodePropertyUtils.stringProp(node, "material");
            if (!"Sprite3D".equals(type) && !"AnimatedSprite3D".equals(type) && (materialPath == null || materialPath.isBlank())) {
                String textureProp = NodePropertyUtils.stringProp(node, "texture");
                boolean hasCustomTexture = textureProp != null && !textureProp.isBlank()
                        && !MoudTextures.WHITE_ID.toString().equals(textureProp)
                        && !"moud:dynamic/white".equals(textureProp);
                if (!hasCustomTexture) {
                    continue;
                }
            }

            Pose world = poseResolver.apply(node.nodeId());
            if (world == null) {
                continue;
            }

            if (nodeShaderRenderer.render(node, world, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta)) {
                continue;
            }

            if ("Sprite3D".equals(type) || "AnimatedSprite3D".equals(type)) {
                continue;
            }

            float tintR = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_tint_r"), 1.0f));
            float tintG = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_tint_g"), 1.0f));
            float tintB = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_tint_b"), 1.0f));
            int tintRi = Math.round(tintR * 255.0f);
            int tintGi = Math.round(tintG * 255.0f);
            int tintBi = Math.round(tintB * 255.0f);
            int alpha = Math.round(NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "opacity"), 1.0f)) * 255.0f);

            Identifier textureId = textureResolver.apply(node);
            RenderLayer layer = alpha < 255
                    ? RenderLayer.getEntityTranslucentCull(textureId)
                    : RenderLayer.getEntityCutout(textureId);
            VertexConsumer vertexConsumer = consumers.getBuffer(layer);
            int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(world.pos.x, world.pos.y, world.pos.z));
            if (light <= 0) {
                light = 0x00F000F0;
            }

            matrices.push();
            matrices.translate(world.pos.x - camPos.x, world.pos.y - camPos.y, world.pos.z - camPos.z);
            matrices.multiply(world.rot);
            matrices.scale(world.scale.x, world.scale.y, world.scale.z);
            matrices.translate(-0.5, -0.5, -0.5);
            renderUnitCube(vertexConsumer, matrices.peek(), light, OverlayTexture.DEFAULT_UV, tintRi, tintGi, tintBi, alpha);
            matrices.pop();
        }
    }

    private static boolean isViewmodelInPlay(SceneSnapshot.NodeSnapshot node) {
        if (!NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "viewmodel"), false)) return false;
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        return runtime != null && runtime.isActive();
    }

    private void renderUnitCube(VertexConsumer vertexConsumer, MatrixStack.Entry entry, int light, int overlay,
                                int r, int g, int b, int a) {
        quad(vertexConsumer, entry,
                0, 0, 0, 0, 1,
                0, 1, 0, 0, 0,
                1, 1, 0, 1, 0,
                1, 0, 0, 1, 1,
                light, overlay,
                0, 0, -1, r, g, b, a);
        quad(vertexConsumer, entry,
                0, 0, 1, 1, 1,
                1, 0, 1, 0, 1,
                1, 1, 1, 0, 0,
                0, 1, 1, 1, 0,
                light, overlay,
                0, 0, 1, r, g, b, a);
        quad(vertexConsumer, entry,
                0, 0, 0, 1, 1,
                0, 0, 1, 0, 1,
                0, 1, 1, 0, 0,
                0, 1, 0, 1, 0,
                light, overlay,
                -1, 0, 0, r, g, b, a);
        quad(vertexConsumer, entry,
                1, 0, 0, 0, 1,
                1, 1, 0, 0, 0,
                1, 1, 1, 1, 0,
                1, 0, 1, 1, 1,
                light, overlay,
                1, 0, 0, r, g, b, a);
        quad(vertexConsumer, entry,
                0, 0, 0, 0, 0,
                1, 0, 0, 1, 0,
                1, 0, 1, 1, 1,
                0, 0, 1, 0, 1,
                light, overlay,
                0, -1, 0, r, g, b, a);
        quad(vertexConsumer, entry,
                0, 1, 0, 0, 1,
                0, 1, 1, 0, 0,
                1, 1, 1, 1, 0,
                1, 1, 0, 1, 1,
                light, overlay,
                0, 1, 0, r, g, b, a);
    }

    private void quad(VertexConsumer vertexConsumer,
                      MatrixStack.Entry entry,
                      float x0, float y0, float z0, float u0, float v0,
                      float x1, float y1, float z1, float u1, float v1,
                      float x2, float y2, float z2, float u2, float v2,
                      float x3, float y3, float z3, float u3, float v3,
                      int light, int overlay,
                      float nx, float ny, float nz,
                      int r, int g, int b, int a) {
        vertex(vertexConsumer, entry, x0, y0, z0, u0, v0, light, overlay, nx, ny, nz, r, g, b, a);
        vertex(vertexConsumer, entry, x1, y1, z1, u1, v1, light, overlay, nx, ny, nz, r, g, b, a);
        vertex(vertexConsumer, entry, x2, y2, z2, u2, v2, light, overlay, nx, ny, nz, r, g, b, a);
        vertex(vertexConsumer, entry, x3, y3, z3, u3, v3, light, overlay, nx, ny, nz, r, g, b, a);
    }

    private void vertex(VertexConsumer vertexConsumer,
                        MatrixStack.Entry entry,
                        float x, float y, float z,
                        float u, float v,
                        int light, int overlay,
                        float nx, float ny, float nz,
                        int r, int g, int b, int a) {
        vertexConsumer.vertex(entry, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(entry, nx, ny, nz);
    }
}
