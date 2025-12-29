package com.moud.client.editor.selection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.editor.EditorModeManager;
import com.moud.client.editor.runtime.RuntimeObject;
import com.moud.client.editor.runtime.RuntimeObjectRegistry;
import com.moud.client.editor.runtime.RuntimeObjectType;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.scene.blueprint.BlueprintCornerSelector;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.picking.RaycastPicker;
import com.moud.client.util.LimbRaycaster;
import com.moud.client.model.ClientModelManager;
import com.moud.client.model.RenderableModel;
import com.moud.client.zone.ClientZoneManager;
import com.moud.client.animation.AnimatedPlayerModel;
import com.moud.client.animation.ClientPlayerModelManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Set;

public final class EditorSelectionRenderer implements WorldRenderEvents.AfterEntities {
    private EditorSelectionRenderer() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(new EditorSelectionRenderer());
    }

    @Override
    public void afterEntities(WorldRenderContext context) {
        RaycastPicker picker = RaycastPicker.getInstance();
        boolean limbSelected = picker.getSelectedLimb() != null;
        SceneEditorOverlay overlay = SceneEditorOverlay.getInstance();
        Set<SceneObject> selectedObjects = overlay.getSelectedObjects();
        String primaryId = overlay.getSelectedObject() != null ? overlay.getSelectedObject().getId() : null;

        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        if (!limbSelected) {
            for (SceneObject selected : selectedObjects) {
                Box box = computeBounds(selected);
                if (box != null) {
                    boolean isPrimary = selected.getId().equals(primaryId);
                    if (isPrimary) {
                        addBox(buffer, matrix, box, 0.08f, 0.62f, 1.0f, 1.0f);
                    } else {
                        addBox(buffer, matrix, box, 0.3f, 0.7f, 0.95f, 0.8f);
                    }
                }
            }
        }

        renderMarkers(context, buffer, matrix);
        renderCornerGuides(context, buffer, matrix);

        matrices.pop();
        var built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
            built.close();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderMarkers(WorldRenderContext context, BufferBuilder buffer, Matrix4f matrix) {
        if (!EditorModeManager.getInstance().isActive()) {
            return;
        }
        var graph = SceneSessionManager.getInstance().getSceneGraph();
        if (graph.getObjects().isEmpty()) {
            return;
        }
        for (SceneObject object : graph.getObjects()) {
            String type = object.getType();
            if ("marker".equalsIgnoreCase(type)) {
                Vec3d pos = extractPosition(object);
                if (pos == null) {
                    continue;
                }
                double size = 0.2;
                Box markerBox = new Box(
                        pos.x - size,
                        pos.y - size,
                        pos.z - size,
                        pos.x + size,
                        pos.y + size,
                        pos.z + size
                );
                addBox(buffer, matrix, markerBox, 0.98f, 0.82f, 0.2f, 1.0f);
            } else if ("zone".equalsIgnoreCase(type)) {
                Box zoneBox = extractZoneBox(object);
                if (zoneBox != null) {
                    addBox(buffer, matrix, zoneBox, 0.3f, 0.9f, 0.4f, 0.6f);
                }
            }
        }

        for (ClientZoneManager.ZoneSnapshot zone : ClientZoneManager.snapshot()) {
            if (zone == null) {
                continue;
            }
            Box zoneBox = new Box(zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ());
            addBox(buffer, matrix, zoneBox, 0.9f, 0.35f, 0.95f, 0.4f);
        }
    }

    private Box computeBounds(SceneObject object) {
        String objectId = object.getId();
        Long modelId = SceneSelectionManager.getInstance().getBindingForObject(objectId);
        Vec3d position = extractPosition(object);
        if (position == null) {
            return null;
        }
        if ("zone".equalsIgnoreCase(object.getType())) {
            return extractZoneBox(object);
        }
        if (modelId != null) {
            RenderableModel model = ClientModelManager.getInstance().getModel(modelId);
            if (model != null && model.hasMeshBounds()) {
                Vec3d min = new Vec3d(model.getMeshMin().x, model.getMeshMin().y, model.getMeshMin().z);
                Vec3d max = new Vec3d(model.getMeshMax().x, model.getMeshMax().y, model.getMeshMax().z);
                Vec3d halfExtents = max.subtract(min).multiply(0.5);
                Vec3d center = min.add(halfExtents);
                Vec3d worldCenter = position.add(center.x, center.y, center.z);
                return new Box(
                        worldCenter.x - halfExtents.x,
                        worldCenter.y - halfExtents.y,
                        worldCenter.z - halfExtents.z,
                        worldCenter.x + halfExtents.x,
                        worldCenter.y + halfExtents.y,
                        worldCenter.z + halfExtents.z
                );
            }
        }

        Object scaleRaw = object.getProperties().get("scale");
        if (scaleRaw instanceof Map<?, ?> map) {
            double sx = SceneEditorOverlay.toDouble(map.get("x"), 1.0);
            double sy = SceneEditorOverlay.toDouble(map.get("y"), 1.0);
            double sz = SceneEditorOverlay.toDouble(map.get("z"), 1.0);
            double hx = Math.max(0.05, Math.abs(sx) * 0.5);
            double hy = Math.max(0.05, Math.abs(sy) * 0.5);
            double hz = Math.max(0.05, Math.abs(sz) * 0.5);
            return new Box(
                    position.x - hx,
                    position.y - hy,
                    position.z - hz,
                    position.x + hx,
                    position.y + hy,
                    position.z + hz
            );
        }

        double size = 0.75;
        return new Box(
                position.x - size,
                position.y - size,
                position.z - size,
                position.x + size,
                position.y + size,
                position.z + size
        );
    }

    @SuppressWarnings("unchecked")
    private Box extractZoneBox(SceneObject object) {
        Map<String, Object> props = object.getProperties();
        Object c1Raw = props.get("corner1");
        Object c2Raw = props.get("corner2");
        if (!(c1Raw instanceof Map<?, ?>) || !(c2Raw instanceof Map<?, ?>)) {
            return null;
        }
        double x1 = SceneEditorOverlay.toDouble(((Map<String, Object>) c1Raw).get("x"), 0);
        double y1 = SceneEditorOverlay.toDouble(((Map<String, Object>) c1Raw).get("y"), 0);
        double z1 = SceneEditorOverlay.toDouble(((Map<String, Object>) c1Raw).get("z"), 0);
        double x2 = SceneEditorOverlay.toDouble(((Map<String, Object>) c2Raw).get("x"), 0);
        double y2 = SceneEditorOverlay.toDouble(((Map<String, Object>) c2Raw).get("y"), 0);
        double z2 = SceneEditorOverlay.toDouble(((Map<String, Object>) c2Raw).get("z"), 0);
        double minX = Math.min(x1, x2);
        double minY = Math.min(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxX = Math.max(x1, x2);
        double maxY = Math.max(y1, y2);
        double maxZ = Math.max(z1, z2);
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Vec3d extractPosition(SceneObject object) {
        Object raw = object.getProperties().get("position");
        if (!(raw instanceof java.util.Map<?,?> map)) {
            return null;
        }
        Object x = map.get("x");
        Object y = map.get("y");
        Object z = map.get("z");
        if (x instanceof Number && y instanceof Number && z instanceof Number) {
            return new Vec3d(((Number) x).doubleValue(), ((Number) y).doubleValue(), ((Number) z).doubleValue());
        }
        return null;
    }


    private void renderCornerGuides(WorldRenderContext context, BufferBuilder buffer, Matrix4f matrix) {
        var selector = BlueprintCornerSelector.getInstance();
        if (!selector.isPicking() && !SceneEditorOverlay.getInstance().hasRegionCorners()) {
            return;
        }

        float[] cornerA = SceneEditorOverlay.getInstance().getRegionCornerA();
        float[] cornerB = SceneEditorOverlay.getInstance().getRegionCornerB();
        boolean aSet = SceneEditorOverlay.getInstance().isRegionACaptured();
        boolean bSet = SceneEditorOverlay.getInstance().isRegionBCaptured();

        if (aSet) {
            drawCorner(buffer, matrix, cornerA, 0.2f, 0.6f, 0.9f);
        }
        if (bSet) {
            drawCorner(buffer, matrix, cornerB, 0.9f, 0.5f, 0.3f);
        }

        if (aSet && bSet) {
            Box box = SceneEditorOverlay.buildBoxFromCorners(cornerA, cornerB);
            addBox(buffer, matrix, box, 0.4f, 0.8f, 0.4f, 0.6f);
        }
    }

    private void drawCorner(BufferBuilder buffer, Matrix4f matrix, float[] corner, float r, float g, float b) {
        double size = 0.15;
        Box box = new Box(
                corner[0] - size,
                corner[1] - size,
                corner[2] - size,
                corner[0] + size,
                corner[1] + size,
                corner[2] + size
        );
        addBox(buffer, matrix, box, r, g, b, 0.9f);
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
}
