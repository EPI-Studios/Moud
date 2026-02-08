package com.moud.client.editor.scene.blueprint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.rendering.DebugLineRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class BlueprintPreviewRenderer implements WorldRenderEvents.AfterEntities {
    private static final BlueprintPreviewRenderer INSTANCE = new BlueprintPreviewRenderer();

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(INSTANCE);
    }

    private BlueprintPreviewRenderer() {}

    @Override
    public void afterEntities(WorldRenderContext context) {
        BlueprintPreviewManager.PreviewState preview = BlueprintPreviewManager.getInstance().getCurrent();
        if (preview == null || preview.boxes.isEmpty()) {
            return;
        }
        Vec3d camera = context.camera().getPos();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        context.matrixStack().push();
        context.matrixStack().translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
        BufferBuilder buffer = DebugLineRenderer.begin();
        for (BlueprintPreviewManager.RelativeBox rel : preview.boxes) {
            Box worldBox = transform(rel, preview);
            addBox(buffer, matrix, worldBox, 0.4f, 0.9f, 0.9f, 0.95f);
        }
        DebugLineRenderer.draw(buffer);
        context.matrixStack().pop();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void addBox(BufferBuilder buffer, Matrix4f matrix, Box box, float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        addLine(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        addLine(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        addLine(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        addLine(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        addLine(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        addLine(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        addLine(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        addLine(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private void addLine(BufferBuilder buffer, Matrix4f matrix,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float r, float g, float b, float a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
    }

    private Box transform(BlueprintPreviewManager.RelativeBox rel, BlueprintPreviewManager.PreviewState state) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        double[] xs = {rel.minX, rel.maxX};
        double[] ys = {rel.minY, rel.maxY};
        double[] zs = {rel.minZ, rel.maxZ};

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    double[] transformed = transformPoint(x, y, z, state);
                    minX = Math.min(minX, transformed[0]);
                    minY = Math.min(minY, transformed[1]);
                    minZ = Math.min(minZ, transformed[2]);
                    maxX = Math.max(maxX, transformed[0]);
                    maxY = Math.max(maxY, transformed[1]);
                    maxZ = Math.max(maxZ, transformed[2]);
                }
            }
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private double[] transformPoint(double x, double y, double z, BlueprintPreviewManager.PreviewState state) {
        if (state.blueprint != null && state.blueprint.blocks != null) {
            int rotationSteps = Math.floorMod(Math.round(state.rotation[1] / 90.0f), 4);
            boolean mirrorX = state.scale[0] < 0.0f;
            boolean mirrorZ = state.scale[2] < 0.0f;

            var local = BlueprintSchematicTransform.transformPosition(
                    state.blueprint.blocks,
                    x,
                    y,
                    z,
                    rotationSteps,
                    mirrorX,
                    mirrorZ
            );

            double ox = Math.floor(state.position[0]);
            double oy = Math.floor(state.position[1]);
            double oz = Math.floor(state.position[2]);
            return new double[]{local.x + ox, local.y + oy, local.z + oz};
        }

        double sx = x * state.scale[0];
        double sy = y * state.scale[1];
        double sz = z * state.scale[2];

        double pitch = Math.toRadians(state.rotation[0]);
        double yaw = Math.toRadians(state.rotation[1]);
        double roll = Math.toRadians(state.rotation[2]);


        double cosR = Math.cos(roll);
        double sinR = Math.sin(roll);
        double rx1 = sx * cosR - sy * sinR;
        double ry1 = sx * sinR + sy * cosR;
        double rz1 = sz;

        double cosP = Math.cos(pitch);
        double sinP = Math.sin(pitch);
        double rx2 = rx1;
        double ry2 = ry1 * cosP - rz1 * sinP;
        double rz2 = ry1 * sinP + rz1 * cosP;

        double cosY = Math.cos(yaw);
        double sinY = Math.sin(yaw);
        double rx3 = rx2 * cosY + rz2 * sinY;
        double ry3 = ry2;
        double rz3 = -rx2 * sinY + rz2 * cosY;

        return new double[]{
                rx3 + state.position[0],
                ry3 + state.position[1],
                rz3 + state.position[2]
        };
    }
}
