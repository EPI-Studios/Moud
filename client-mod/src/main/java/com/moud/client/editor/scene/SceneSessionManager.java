package com.moud.client.editor.scene;

import com.moud.client.network.ClientPacketWrapper;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SceneSessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoudSceneSession");
    private static final SceneSessionManager INSTANCE = new SceneSessionManager();

    private final EditorSceneGraph sceneGraph = new EditorSceneGraph();
    private String activeSceneId = "default";
    private boolean awaitingSnapshot;
    private boolean editorActive;

    private SceneSessionManager() {
    }

    public static SceneSessionManager getInstance() {
        return INSTANCE;
    }

    public void onEditorActivated() {
        editorActive = true;
        requestSceneState();
    }

    public void onEditorDeactivated() {
        editorActive = false;
    }

    public void forceRefresh() {
        if (!editorActive) {
            return;
        }
        awaitingSnapshot = false;
        requestSceneState();
    }

    private void requestSceneState() {
        if (awaitingSnapshot) {
            return;
        }
        awaitingSnapshot = true;
        LOGGER.info("Requesting scene state for '{}'", activeSceneId);
        ClientPacketWrapper.sendToServer(new MoudPackets.RequestSceneStatePacket(activeSceneId));
    }

    public void handleSceneState(MoudPackets.SceneStatePacket packet) {
        if (!packet.sceneId().equals(activeSceneId)) {
            LOGGER.debug("Ignoring scene state for {}", packet.sceneId());
            return;
        }
        awaitingSnapshot = false;
        SceneHistoryManager.getInstance().clear();
        sceneGraph.applySnapshot(packet);
        SceneEditorDiagnostics.log("Scene synced: " + sceneGraph.getObjects().size() + " objects, version " + packet.version());
        LOGGER.info("Scene state received: {} objects (version {})", sceneGraph.getObjects().size(), packet.version());
    }

    public void handleEditAck(MoudPackets.SceneEditAckPacket ack) {
        if (!ack.sceneId().equals(activeSceneId)) {
            return;
        }
        if (!ack.success()) {
            LOGGER.warn("Scene edit failed: {}", ack.message());
            SceneEditorDiagnostics.log("Edit failed: " + ack.message());
            return;
        }
        sceneGraph.applyAcknowledgement(ack);
        SceneEditorDiagnostics.log("Edit applied (" + ack.message() + ")");
        if (ack.updatedObject() == null && ack.objectId() != null) {
            SceneHistoryManager.getInstance().dropEntriesForObject(ack.objectId());
        }
    }

    public void submitEdit(String action, Map<String, Object> payload) {
        ClientPacketWrapper.sendToServer(new MoudPackets.SceneEditPacket(
                activeSceneId,
                action,
                new ConcurrentHashMap<>(payload),
                sceneGraph.getVersion()
        ));
    }

    public void submitPropertyUpdate(String objectId, Map<String, Object> updatedValues) {
        if (objectId == null) {
            return;
        }
        SceneObject object = sceneGraph.get(objectId);
        if (object == null) {
            return;
        }
        ConcurrentHashMap<String, Object> merged = new ConcurrentHashMap<>(object.getProperties());
        merged.putAll(updatedValues);
        ConcurrentHashMap<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("id", objectId);
        payload.put("properties", merged);
        sceneGraph.mergeProperties(objectId, merged);
        submitEdit("update", payload);
    }

    public void submitFullProperties(String objectId, Map<String, Object> newProperties) {
        if (objectId == null || newProperties == null) {
            return;
        }
        SceneObject object = sceneGraph.get(objectId);
        if (object == null) {
            return;
        }
        ConcurrentHashMap<String, Object> payload = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Object> copy = new ConcurrentHashMap<>(newProperties);
        payload.put("id", objectId);
        payload.put("properties", copy);
        sceneGraph.mergeProperties(objectId, copy);
        submitEdit("update", payload);
    }

    public void submitTransformUpdate(String objectId, float[] translation, float[] rotation, float[] scale, float[] quaternion) {
        if (objectId == null) {
            return;
        }
        SceneObject object = sceneGraph.get(objectId);
        if (object == null) {
            return;
        }
        ConcurrentHashMap<String, Object> merged = new ConcurrentHashMap<>(object.getProperties());
        merged.put("position", vectorToMap(translation));
        merged.put("rotation", rotationToMap(rotation));
        if (quaternion != null) {
            merged.put("rotationQuat", quaternionToMap(quaternion));
        }
        merged.put("scale", vectorToMap(scale));
        ConcurrentHashMap<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("id", objectId);
        payload.put("properties", merged);
        sceneGraph.mergeProperties(objectId, merged);
        submitEdit("update", payload);
    }

    public void mergeAnimationProperty(String sceneId, String objectId, String propertyKey, com.moud.api.animation.PropertyTrack.PropertyType propertyType, float value, Map<String, Object> payload) {
        if (!Objects.equals(sceneId, activeSceneId)) {
            return;
        }
        SceneObject object = sceneGraph.get(objectId);
        if (object != null) {
            ConcurrentHashMap<String, Object> merged = new ConcurrentHashMap<>(object.getProperties());
            if (payload != null && !payload.isEmpty()) {
                String root = propertyKey.contains(".") ? propertyKey.substring(0, propertyKey.indexOf('.')) : propertyKey;
                merged.put(root, payload);
            } else {
                applyNestedProperty(merged, propertyKey, value);
            }
            object.overwriteProperties(merged);
        }
        com.moud.client.editor.runtime.RuntimeObjectRegistry.getInstance()
                .applyAnimationProperty(objectId, propertyKey, propertyType, value);
    }

    @SuppressWarnings("unchecked")
    private void applyNestedProperty(Map<String, Object> props, String key, float value) {
        if (key.contains(".")) {
            String[] parts = key.split("\\.");
            if (parts.length >= 2) {
                String root = parts[0];
                Map<String, Object> nested = null;
                Object existingRoot = props.get(root);
                if (existingRoot instanceof Map<?, ?> m) {
                    nested = (Map<String, Object>) m;
                } else {
                    nested = new java.util.HashMap<>();
                    props.put(root, nested);
                }
                if (parts.length == 2) {
                    nested.put(parts[1], value);
                } else {
                    Map<String, Object> current = nested;
                    for (int i = 1; i < parts.length - 1; i++) {
                        Object child = current.get(parts[i]);
                        if (child instanceof Map<?, ?> mm) {
                            current = (Map<String, Object>) mm;
                        } else {
                            Map<String, Object> newChild = new java.util.HashMap<>();
                            current.put(parts[i], newChild);
                            current = newChild;
                        }
                    }
                    current.put(parts[parts.length - 1], value);
                }
                return;
            }
        }
        props.put(key, value);
    }

    public void tick(MinecraftClient client) {
        if (!editorActive) {
            return;
        }
        if (!awaitingSnapshot && sceneGraph.getObjects().isEmpty()) {
            requestSceneState();
        }
    }

    public EditorSceneGraph getSceneGraph() {
        return sceneGraph;
    }

    private Map<String, Object> vectorToMap(float[] values) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("x", values[0]);
        map.put("y", values[1]);
        map.put("z", values[2]);
        return map;
    }

    private Map<String, Object> rotationToMap(float[] values) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("pitch", values[0]);
        map.put("yaw", values[1]);
        map.put("roll", values[2]);
        return map;
    }

    private Map<String, Object> quaternionToMap(float[] values) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("x", values[0]);
        map.put("y", values[1]);
        map.put("z", values[2]);
        map.put("w", values[3]);
        return map;
    }
}
