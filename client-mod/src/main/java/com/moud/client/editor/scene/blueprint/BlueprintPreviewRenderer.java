package com.moud.client.editor.scene.blueprint;

import com.moud.client.editor.EditorModeManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class BlueprintPreviewRenderer implements WorldRenderEvents.AfterEntities {
    private static final BlueprintPreviewRenderer INSTANCE = new BlueprintPreviewRenderer();

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(INSTANCE);
    }

    private BlueprintPreviewRenderer() {}

    @Override
    public void afterEntities(WorldRenderContext context) {
        if (!EditorModeManager.getInstance().isActive()) {
            return;
        }
        BlueprintPreviewManager.PreviewState preview = BlueprintPreviewManager.getInstance().getCurrent();
        if (preview == null || preview.boxes.isEmpty()) {
            return;
        }
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
        Vec3d camera = context.camera().getPos();
        context.matrixStack().push();
        context.matrixStack().translate(-camera.x, -camera.y, -camera.z);
        for (BlueprintPreviewManager.RelativeBox rel : preview.boxes) {
            Box worldBox = transform(rel, preview);
            WorldRenderer.drawBox(context.matrixStack(), buffer, worldBox, 0.4f, 0.9f, 0.9f, 1.0f);
        }
        context.matrixStack().pop();
    }

    private Box transform(BlueprintPreviewManager.RelativeBox rel, BlueprintPreviewManager.PreviewState state) {
        double yaw = Math.toRadians(state.yawDegrees);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
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
                    double rx = x * cos - z * sin;
                    double rz = x * sin + z * cos;
                    double ry = y;
                    rx += state.position[0];
                    ry += state.position[1];
                    rz += state.position[2];
                    minX = Math.min(minX, rx);
                    minY = Math.min(minY, ry);
                    minZ = Math.min(minZ, rz);
                    maxX = Math.max(maxX, rx);
                    maxY = Math.max(maxY, ry);
                    maxZ = Math.max(maxZ, rz);
                }
            }
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
