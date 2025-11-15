package com.moud.client.editor.selection;

import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.model.ClientModelManager;
import com.moud.client.model.RenderableModel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class EditorSelectionRenderer implements WorldRenderEvents.AfterEntities {
    private EditorSelectionRenderer() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(new EditorSelectionRenderer());
    }

    @Override
    public void afterEntities(WorldRenderContext context) {
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

    private Box computeBounds(SceneObject object) {
        String objectId = object.getId();
        Long modelId = SceneSelectionManager.getInstance().getBindingForObject(objectId);
        Vec3d position = extractPosition(object);
        if (position == null) {
            return null;
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
}