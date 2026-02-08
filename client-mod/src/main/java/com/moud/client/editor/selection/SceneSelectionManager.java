package com.moud.client.editor.selection;

import com.moud.client.collision.ModelCollisionManager;
import com.moud.client.editor.EditorModeManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.network.MoudPackets;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SceneSelectionManager {
    private static final SceneSelectionManager INSTANCE = new SceneSelectionManager();

    private final Map<Long, String> modelBindings = new ConcurrentHashMap<>();
    private final Map<String, Long> objectBindings = new ConcurrentHashMap<>();

    private SceneSelectionManager() {}

    public static SceneSelectionManager getInstance() {
        return INSTANCE;
    }

    public void handleBindingPacket(MoudPackets.SceneBindingPacket packet) {
        if (packet.removed()) {
            String objectId = modelBindings.remove(packet.modelId());
            if (objectId != null) {
                objectBindings.remove(objectId);
            }
        } else {
            modelBindings.put(packet.modelId(), packet.objectId());
            objectBindings.put(packet.objectId(), packet.modelId());
        }
    }

    public boolean handleClickSelection() {
        return handleClickSelection(false);
    }

    public boolean handleClickSelection(boolean shiftHeld) {
        if (!EditorModeManager.getInstance().isActive()) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        var camera = client.gameRenderer.getCamera();
        Vec3d origin = camera.getPos();
        Vec3d direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        long modelId = ModelCollisionManager.getInstance().pick(origin, direction.normalize(), 256.0);
        if (modelId < 0) {
            if (!shiftHeld) {
                SceneEditorOverlay.getInstance().clearSelection();
            }
            return false;
        }
        String objectId = modelBindings.get(modelId);
        if (objectId == null) {
            return false;
        }
        SceneEditorOverlay.getInstance().selectFromExternal(objectId, shiftHeld);
        return true;
    }

    public void clear() {
        modelBindings.clear();
        objectBindings.clear();
    }

    public Long getBindingForObject(String objectId) {
        return objectBindings.get(objectId);
    }
}
