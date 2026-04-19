package com.moud.client.fabric.render.scene.subrender;

import com.moud.client.fabric.mixin.accessor.GameRendererAccessor;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.net.protocol.SceneSnapshot;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

public final class ViewmodelRenderer {
    public void render(List<SceneSnapshot.NodeSnapshot> nodes,
                       VertexConsumerProvider.Immediate consumers,
                       MatrixStack matrices,
                       Camera camera) {
        if (nodes == null || nodes.isEmpty() || camera == null) return;
        // Skip the viewmodel pass entirely in editor mode - the cube should
        // render in world space so authors can position / tweak it, and we
        // don't want a floating gun bolted to the editor camera.
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive()) return;

        Quaternionf camRot = new Quaternionf(camera.getRotation());
        float gameFov = -1f;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.gameRenderer != null) {
                gameFov = (float) ((GameRendererAccessor) mc.gameRenderer).moud$getFov(camera, 1.0f, true);
            }
        } catch (Throwable ignored) {
        }
        boolean drewAny = false;
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null) continue;
            if (!"MeshInstance3D".equals(node.type())) continue;
            if (!NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "viewmodel"), false)) continue;
            if (!NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "visible"), true)) continue;

            if (!drewAny) {
                consumers.draw();
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                drewAny = true;
            }

            float offsetX = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "viewmodel_offset_x"), 0.25f);
            float offsetY = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "viewmodel_offset_y"), -0.22f);
            float offsetZ = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "viewmodel_offset_z"), -0.45f);

            float rx = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "rx"), 0f);
            float ry = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "ry"), 0f);
            float rz = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "rz"), 0f);

            float sx = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "sx"), 1f);
            float sy = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "sy"), 1f);
            float sz = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "sz"), 1f);
            float vmFov = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "viewmodel_fov"), 0f);
            float fovScale = 1f;
            if (gameFov > 1f && vmFov > 1f && Math.abs(vmFov - gameFov) > 0.5f) {
                double tanGame = Math.tan(Math.toRadians(gameFov * 0.5f));
                double tanVm = Math.tan(Math.toRadians(vmFov * 0.5f));
                if (tanVm > 1.0e-4) {
                    fovScale = (float) (tanGame / tanVm);
                }
            }

            float tintR = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_tint_r"), 1f));
            float tintG = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_tint_g"), 1f));
            float tintB = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_tint_b"), 1f));
            float opacity = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "opacity"), 1f));
            int r = Math.round(tintR * 255f);
            int g = Math.round(tintG * 255f);
            int b = Math.round(tintB * 255f);
            int a = Math.round(opacity * 255f);

            Identifier tex = MoudTextures.resolve(NodePropertyUtils.stringProp(node, "texture"));
            RenderLayer layer = a < 255
                    ? RenderLayer.getEntityTranslucentCull(tex)
                    : RenderLayer.getEntityCutout(tex);
            VertexConsumer vc = consumers.getBuffer(layer);

            matrices.push();
            // Align the matrix with the camera frame so subsequent translate
            // moves in camera-local coordinates (x=right, y=up, -z=forward).
            matrices.multiply(camRot);
            matrices.translate(offsetX, offsetY, offsetZ);
            // Additional node rotation (for kick / bob animation)
            if (Math.abs(rz) > 0.001f) matrices.multiply(axis(0, 0, 1, rz));
            if (Math.abs(ry) > 0.001f) matrices.multiply(axis(0, 1, 0, ry));
            if (Math.abs(rx) > 0.001f) matrices.multiply(axis(1, 0, 0, rx));
            // Uniform scale that approximates rendering the mesh at `viewmodel_fov`
            // instead of the game FOV - keeps the weapon size constant when the
            // user changes FOV or when a script pulses camera:setFov().
            matrices.scale(sx * fovScale, sy * fovScale, sz * fovScale);
            matrices.translate(-0.5f, -0.5f, -0.5f);

            int light = 0x00F000F0;
            renderUnitCube(vc, matrices.peek(), light, OverlayTexture.DEFAULT_UV, r, g, b, a);

            matrices.pop();
        }

        if (drewAny) {
            consumers.draw();
        }
    }

    private static org.joml.Quaternionf axis(float ax, float ay, float az, float deg) {
        return new org.joml.Quaternionf().rotateAxis((float) Math.toRadians(deg), ax, ay, az);
    }

    private static void renderUnitCube(VertexConsumer vc, MatrixStack.Entry entry, int light, int overlay,
                                       int r, int g, int b, int a) {
        quad(vc, entry,
                0, 0, 0, 0, 1,   0, 1, 0, 0, 0,   1, 1, 0, 1, 0,   1, 0, 0, 1, 1,
                light, overlay, 0, 0, -1, r, g, b, a);
        quad(vc, entry,
                0, 0, 1, 1, 1,   1, 0, 1, 0, 1,   1, 1, 1, 0, 0,   0, 1, 1, 1, 0,
                light, overlay, 0, 0, 1, r, g, b, a);
        quad(vc, entry,
                0, 0, 0, 1, 1,   0, 0, 1, 0, 1,   0, 1, 1, 0, 0,   0, 1, 0, 1, 0,
                light, overlay, -1, 0, 0, r, g, b, a);
        quad(vc, entry,
                1, 0, 0, 0, 1,   1, 1, 0, 0, 0,   1, 1, 1, 1, 0,   1, 0, 1, 1, 1,
                light, overlay, 1, 0, 0, r, g, b, a);
        quad(vc, entry,
                0, 0, 0, 0, 0,   1, 0, 0, 1, 0,   1, 0, 1, 1, 1,   0, 0, 1, 0, 1,
                light, overlay, 0, -1, 0, r, g, b, a);
        quad(vc, entry,
                0, 1, 0, 0, 1,   0, 1, 1, 0, 0,   1, 1, 1, 1, 0,   1, 1, 0, 1, 1,
                light, overlay, 0, 1, 0, r, g, b, a);
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry entry,
                             float x0, float y0, float z0, float u0, float v0,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             int light, int overlay,
                             float nx, float ny, float nz,
                             int r, int g, int b, int a) {
        v(vc, entry, x0, y0, z0, u0, v0, light, overlay, nx, ny, nz, r, g, b, a);
        v(vc, entry, x1, y1, z1, u1, v1, light, overlay, nx, ny, nz, r, g, b, a);
        v(vc, entry, x2, y2, z2, u2, v2, light, overlay, nx, ny, nz, r, g, b, a);
        v(vc, entry, x3, y3, z3, u3, v3, light, overlay, nx, ny, nz, r, g, b, a);
    }

    private static void v(VertexConsumer vc, MatrixStack.Entry entry,
                          float x, float y, float z, float u, float vUv,
                          int light, int overlay, float nx, float ny, float nz,
                          int r, int g, int b, int a) {
        vc.vertex(entry, x, y, z)
                .color(r, g, b, a)
                .texture(u, vUv)
                .overlay(overlay)
                .light(light)
                .normal(entry, nx, ny, nz);
    }
}
