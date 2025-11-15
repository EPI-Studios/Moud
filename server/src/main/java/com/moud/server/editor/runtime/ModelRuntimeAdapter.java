package com.moud.server.editor.runtime;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.entity.ModelManager;
import com.moud.server.instance.InstanceManager;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.proxy.ModelProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class ModelRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRuntimeAdapter.class);

    private final String sceneId;
    private ModelProxy model;
    private String objectId;

    public ModelRuntimeAdapter(String sceneId) {
        this.sceneId = sceneId;
    }

    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) throws Exception {
        this.objectId = snapshot.objectId();
        Map<String, Object> props = snapshot.properties();
        String modelPath = stringProperty(props, "modelPath", "moud:models/capsule.obj");
        Vector3 position = vectorProperty(props.get("position"), new Vector3(0, 64, 0));
        Quaternion rotation = quaternionFromEuler(props.get("rotation"), Quaternion.identity());
        Vector3 scale = vectorProperty(props.get("scale"), Vector3.one());
        String texture = stringProperty(props, "texture", "");

        model = new ModelProxy(
                InstanceManager.getInstance().getDefaultInstance(),
                modelPath,
                position,
                rotation,
                scale,
                texture
        );
        ModelManager.getInstance().tagSceneBinding(model.getId(), sceneId, snapshot.objectId());
        broadcastBinding(false);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) throws Exception {
        if (model == null) {
            create(snapshot);
            return;
        }
        Map<String, Object> props = snapshot.properties();
        Vector3 position = vectorProperty(props.get("position"), model.getPosition());
        Quaternion rotation = quaternionFromEuler(props.get("rotation"), model.getRotation());
        Vector3 scale = vectorProperty(props.get("scale"), model.getScale());
        String texture = stringProperty(props, "texture", model.getTexture());

        model.setPosition(position);
        model.setScale(scale);
        if (texture != null && !texture.isEmpty()) {
            model.setTexture(texture);
        }
        if (rotation != null) {
            model.setRotation(rotation);
        }
    }

    @Override
    public void remove() {
        if (model != null) {
            try {
                model.remove();
            } catch (Exception e) {
                LOGGER.warn("Failed to remove model bound to scene {}", sceneId, e);
            }
            broadcastBinding(true);
            model = null;
        }
    }

    private static String stringProperty(Map<String, Object> props, String key, String defaultValue) {
        Object value = props.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static Vector3 vectorProperty(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?,?> map) {
            double x = toDouble(map.get("x"), fallback.x);
            double y = toDouble(map.get("y"), fallback.y);
            double z = toDouble(map.get("z"), fallback.z);
            return new Vector3(x, y, z);
        }
        return fallback;
    }

    private static Quaternion quaternionFromEuler(Object raw, Quaternion fallback) {
        if (raw instanceof Map<?,?> map) {
            double pitch = toDouble(map.get("pitch"), 0);
            double yaw = toDouble(map.get("yaw"), 0);
            double roll = toDouble(map.get("roll"), 0);
            return Quaternion.fromEuler((float) pitch, (float) yaw, (float) roll);
        }
        return fallback;
    }

    private static double toDouble(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return raw != null ? Double.parseDouble(raw.toString()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private void broadcastBinding(boolean removed) {
        if (model == null || objectId == null) {
            return;
        }
        ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
        if (networkManager != null) {
            networkManager.broadcast(new MoudPackets.SceneBindingPacket(sceneId, objectId, model.getId(), removed));
        }
    }
}