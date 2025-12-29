package com.moud.client.editor.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.editor.picking.RaycastPicker;
import com.moud.client.editor.runtime.RuntimeObject;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.rendering.DebugLineRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class SelectionHighlightRenderer {
    private static final SelectionHighlightRenderer INSTANCE = new SelectionHighlightRenderer();

    private static final float[] HOVER_COLOR = {0.5f, 0.7f, 1.0f, 0.4f};
    private static final float[] SELECT_COLOR = {0.7f, 0.9f, 1.0f, 0.6f};

    private SelectionHighlightRenderer() {}

    public static SelectionHighlightRenderer getInstance() {
        return INSTANCE;
    }

    public void render(MatrixStack matrices, Camera camera) {
        if (!SceneEditorOverlay.getInstance().isSelectionBoundsVisible()) {
            return;
        }
        RaycastPicker picker = RaycastPicker.getInstance();
        RuntimeObject hovered = picker.getHoveredObject();
        RuntimeObject selected = picker.getSelectedObject();

        if (hovered == null && selected == null) {
            return;
        }

        Vec3d cameraPos = camera.getPos();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        if (hovered != null && hovered != selected) {
            Box bounds = hovered.getBounds();
            if (bounds != null) {
                renderBox(matrices, bounds, HOVER_COLOR);
            }
        }

        if (selected != null) {
            Box bounds = selected.getBounds();
            if (bounds != null) {
                renderBox(matrices, bounds, SELECT_COLOR);
            }
        }

        matrices.pop();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderBox(MatrixStack matrices, Box box, float[] color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = DebugLineRenderer.begin();

        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        addLine(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, color);
        addLine(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, color);
        addLine(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, color);
        addLine(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, color);

        addLine(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, color);
        addLine(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        addLine(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        addLine(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, color);

        addLine(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, color);
        addLine(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, color);
        addLine(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        addLine(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, color);

        DebugLineRenderer.draw(buffer);
    }

    private void addLine(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float[] color) {
        VertexConsumer color1 = buffer.vertex(matrix, x1, y1, z1).color(color[0], color[1], color[2], color[3]);
        buffer.vertex(matrix, x2, y2, z2).color(color[0], color[1], color[2], color[3]);
    }
}
