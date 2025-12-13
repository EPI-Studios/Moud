package com.moud.client.editor.scene;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.animation.AnimatedPlayerModel;
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
        syncEmittersFromGraph();
        syncLimbPropertiesFromGraph();
    }

    @SuppressWarnings("unchecked")
    private void syncLimbPropertiesFromGraph() {
        var playerModelManager = com.moud.client.animation.ClientPlayerModelManager.getInstance();
        var partConfigManager = com.moud.client.animation.PlayerPartConfigManager.getInstance();

        for (SceneObject obj : sceneGraph.getObjects()) {
            if (!"player_model".equalsIgnoreCase(obj.getType())) {
                continue;
            }

            AnimatedPlayerModel animModel = resolvePlayerModel(obj, playerModelManager);
            if (animModel == null || animModel.getEntity() == null) {
                continue;
            }
            applyLimbProperties(obj, animModel, partConfigManager);
        }
    }

    public void restoreLimbPropertiesForModel(long modelId, AnimatedPlayerModel model) {
        if (model == null || model.getEntity() == null) {
            return;
        }
        SceneObject target = sceneGraph.get("player_model:" + modelId);
        if (target == null) {
            for (SceneObject obj : sceneGraph.getObjects()) {
                if (!"player_model".equalsIgnoreCase(obj.getType())) {
                    continue;
                }
                long parsed = parseModelId(obj.getId());
                if (parsed == modelId) {
                    target = obj;
                    break;
                }
            }
        }
        if (target == null) {
            target = findNearestPlayerModel(model.getEntity().getX(), model.getEntity().getY(), model.getEntity().getZ());
        }
        if (target != null) {
            applyLimbProperties(target, model, com.moud.client.animation.PlayerPartConfigManager.getInstance());
        }
    }

    @SuppressWarnings("unchecked")
    private void applyLimbProperties(SceneObject obj, AnimatedPlayerModel animModel, com.moud.client.animation.PlayerPartConfigManager partConfigManager) {
        if (animModel == null || animModel.getEntity() == null) {
            return;
        }
        java.util.UUID uuid = animModel.getEntity().getUuid();

        Object limbPropsRaw = obj.getProperties().get("limbProperties");
        if (!(limbPropsRaw instanceof Map<?, ?>)) {
            return;
        }

        Map<String, Object> limbProperties = (Map<String, Object>) limbPropsRaw;
        for (Map.Entry<String, Object> entry : limbProperties.entrySet()) {
            String boneName = entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?>)) continue;
            Map<String, Object> limbData = (Map<String, Object>) entry.getValue();

            java.util.Map<String, Object> updates = new java.util.HashMap<>();

            Object posRaw = limbData.get("position");
            if (posRaw instanceof Map<?, ?> posMap) {
                double x = toDouble(posMap.get("x"), 0);
                double y = toDouble(posMap.get("y"), 0);
                double z = toDouble(posMap.get("z"), 0);
                updates.put("position", new Vector3(x, y, z));
            }

            Object rotRaw = limbData.get("rotation");
            if (rotRaw instanceof Map<?, ?> rotMap) {
                double x = toDouble(rotMap.get("x"), 0);
                double y = toDouble(rotMap.get("y"), 0);
                double z = toDouble(rotMap.get("z"), 0);
                updates.put("rotation", new Vector3(x, y, z));
            }

            Object scaleRaw = limbData.get("scale");
            if (scaleRaw instanceof Map<?, ?> scaleMap) {
                double x = toDouble(scaleMap.get("x"), 1);
                double y = toDouble(scaleMap.get("y"), 1);
                double z = toDouble(scaleMap.get("z"), 1);
                updates.put("scale", new Vector3(x, y, z));
            }

            Object overrideRaw = limbData.get("overrideAnimation");
            if (overrideRaw instanceof Boolean) {
                updates.put("overrideAnimation", overrideRaw);
            }

            if (!updates.isEmpty()) {
                partConfigManager.updatePartConfig(uuid, boneName, updates);
                LOGGER.debug("Restored limb config for {} bone {} on model {}", uuid, boneName, animModel.getModelId());
            }
        }
    }

    private AnimatedPlayerModel resolvePlayerModel(SceneObject obj, com.moud.client.animation.ClientPlayerModelManager manager) {
        long modelId = parseModelId(obj.getId());
        if (modelId >= 0) {
            AnimatedPlayerModel direct = manager.getModel(modelId);
            if (direct != null) {
                return direct;
            }
        }

        Vector3 pos = vectorFromMap(obj.getProperties().get("position"), Vector3.zero());
        AnimatedPlayerModel best = null;
        double bestDist = Double.MAX_VALUE;
        for (AnimatedPlayerModel candidate : manager.getModels()) {
            if (candidate == null || candidate.getEntity() == null) {
                continue;
            }
            double dx = candidate.getEntity().getX() - pos.x;
            double dy = candidate.getEntity().getY() - pos.y;
            double dz = candidate.getEntity().getZ() - pos.z;
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    private SceneObject findNearestPlayerModel(double x, double y, double z) {
        SceneObject best = null;
        double bestDist = Double.MAX_VALUE;
        for (SceneObject obj : sceneGraph.getObjects()) {
            if (!"player_model".equalsIgnoreCase(obj.getType())) {
                continue;
            }
            Vector3 pos = vectorFromMap(obj.getProperties().get("position"), Vector3.zero());
            double dx = pos.x - x;
            double dy = pos.y - y;
            double dz = pos.z - z;
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = obj;
            }
        }
        return best;
    }

    private long parseModelId(String objId) {
        if (objId == null) {
            return -1;
        }
        int colonIdx = objId.indexOf(':');
        if (colonIdx < 0 || colonIdx + 1 >= objId.length()) {
            return -1;
        }
        try {
            return Long.parseLong(objId.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Vector3 vectorFromMap(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), fallback != null ? fallback.x : 0);
            double y = toDouble(map.get("y"), fallback != null ? fallback.y : 0);
            double z = toDouble(map.get("z"), fallback != null ? fallback.z : 0);
            return new Vector3(x, y, z);
        }
        return fallback;
    }

    private double toDouble(Object val, double fallback) {
        if (val instanceof Number n) return n.doubleValue();
        if (val != null) {
            try { return Double.parseDouble(val.toString()); } catch (Exception ignored) {}
        }
        return fallback;
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
            com.moud.client.editor.runtime.RuntimeObjectRegistry.getInstance().removeEmitter(ack.objectId());
        } else if (ack.updatedObject() != null) {
            SceneObject obj = SceneObject.fromSnapshot(ack.updatedObject());
            if ("particle_emitter".equalsIgnoreCase(obj.getType())) {
                com.moud.client.editor.runtime.RuntimeObjectRegistry.getInstance().syncEmitter(obj);
            }
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

    public void mergeAnimationTransform(String sceneId,
                                        String objectId,
                                        Vector3 position,
                                        Vector3 rotation,
                                        Quaternion rotationQuat,
                                        Vector3 scale,
                                        Map<String, Float> properties) {
        if (!Objects.equals(sceneId, activeSceneId)) {
            return;
        }
        SceneObject object = sceneGraph.get(objectId);
        if (object != null) {
            ConcurrentHashMap<String, Object> merged = new ConcurrentHashMap<>(object.getProperties());
            if (position != null) {
                merged.put("position", vectorToMap(position));
            }
            if (rotation != null) {
                merged.put("rotation", rotationToMap(rotation));
            }
            if (rotationQuat != null) {
                merged.put("rotationQuat", quaternionToMap(rotationQuat));
            }
            if (scale != null) {
                merged.put("scale", vectorToMap(scale));
            }
            if (properties != null && !properties.isEmpty()) {
                properties.forEach((key, value) -> applyNestedProperty(merged, key, value));
            }
            object.overwriteProperties(merged);
        }
        com.moud.client.editor.runtime.RuntimeObjectRegistry.getInstance()
                .applyAnimationTransform(objectId, position, rotation, rotationQuat, scale, properties);
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

    private void syncEmittersFromGraph() {
        for (SceneObject obj : sceneGraph.getObjects()) {
            if ("particle_emitter".equalsIgnoreCase(obj.getType())) {
                com.moud.client.editor.runtime.RuntimeObjectRegistry.getInstance().syncEmitter(obj);
            }
        }
    }

    public String getActiveSceneId() {
        return activeSceneId;
    }

    public EditorSceneGraph getSceneGraph() {
        return sceneGraph;
    }

    private Map<String, Object> vectorToMap(Vector3 vec) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("x", vec.x);
        map.put("y", vec.y);
        map.put("z", vec.z);
        return map;
    }

    private Map<String, Object> rotationToMap(Vector3 vec) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("pitch", vec.x);
        map.put("yaw", vec.y);
        map.put("roll", vec.z);
        return map;
    }

    private Map<String, Object> quaternionToMap(Quaternion quat) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("x", quat.x);
        map.put("y", quat.y);
        map.put("z", quat.z);
        map.put("w", quat.w);
        return map;
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
