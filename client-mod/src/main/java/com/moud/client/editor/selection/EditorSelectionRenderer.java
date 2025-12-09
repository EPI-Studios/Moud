package com.moud.client.editor.selection;

import com.moud.client.editor.EditorModeManager;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.scene.blueprint.BlueprintCornerSelector;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.model.ClientModelManager;
import com.moud.client.model.RenderableModel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

public final class EditorSelectionRenderer implements WorldRenderEvents.AfterEntities {
    private EditorSelectionRenderer() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(new EditorSelectionRenderer());
    }

    @Override
    public void afterEntities(WorldRenderContext context) {
        renderCornerGuides(context);
        renderMarkers(context);
        SceneObject selected = SceneEditorOverlay.getInstance().getSelectedObject();
        if (selected == null) {
            return;
        }
        Box box = computeBounds(selected);
        if (box == null) {
            return;
        }
        Vec3d cameraPos = context.camera().getPos();
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
        context.matrixStack().push();
        context.matrixStack().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        WorldRenderer.drawBox(context.matrixStack(), buffer, box, 0.08f, 0.62f, 1.0f, 1.0f);
        context.matrixStack().pop();
    }

    private void renderMarkers(WorldRenderContext context) {
        if (!EditorModeManager.getInstance().isActive()) {
            return;
        }
        var graph = SceneSessionManager.getInstance().getSceneGraph();
        if (graph.getObjects().isEmpty()) {
            return;
        }
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
        Vec3d cameraPos = context.camera().getPos();
        var matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
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
                WorldRenderer.drawBox(matrices, buffer, markerBox, 0.98f, 0.82f, 0.2f, 1.0f);
            } else if ("zone".equalsIgnoreCase(type)) {
                Box zoneBox = extractZoneBox(object);
                if (zoneBox != null) {
                    WorldRenderer.drawBox(matrices, buffer, zoneBox, 0.3f, 0.9f, 0.4f, 0.6f);
                }
            }
        }
        matrices.pop();
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

    private void renderCornerGuides(WorldRenderContext context) {
        var selector = BlueprintCornerSelector.getInstance();
        if (!selector.isPicking() && !SceneEditorOverlay.getInstance().hasRegionCorners()) {
            return;
        }
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }
        Vec3d cameraPos = context.camera().getPos();
        var matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

        float[] cornerA = SceneEditorOverlay.getInstance().getRegionCornerA();
        float[] cornerB = SceneEditorOverlay.getInstance().getRegionCornerB();
        boolean aSet = SceneEditorOverlay.getInstance().isRegionACaptured();
        boolean bSet = SceneEditorOverlay.getInstance().isRegionBCaptured();

        if (aSet) {
            drawCorner(buffer, matrices, cornerA, 0.2f, 0.6f, 0.9f);
        }
        if (bSet) {
            drawCorner(buffer, matrices, cornerB, 0.9f, 0.5f, 0.3f);
        }

        if (aSet && bSet) {
            Box box = SceneEditorOverlay.buildBoxFromCorners(cornerA, cornerB);
            WorldRenderer.drawBox(matrices, buffer, box, 0.4f, 0.8f, 0.4f, 0.6f);
        }
        matrices.pop();
    }

    private void drawCorner(VertexConsumer buffer, MatrixStack matrices, float[] corner, float r, float g, float b) {
        double size = 0.15;
        Box box = new Box(
                corner[0] - size,
                corner[1] - size,
                corner[2] - size,
                corner[0] + size,
                corner[1] + size,
                corner[2] + size
        );
        WorldRenderer.drawBox(matrices, buffer, box, r, g, b, 0.9f);
    }
}
