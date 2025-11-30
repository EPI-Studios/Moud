package com.moud.server.editor.runtime;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.lighting.ServerLightingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class LightRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LightRuntimeAdapter.class);

    private final String sceneId;
    private long lightId = -1L;

    public LightRuntimeAdapter(String sceneId) {
        this.sceneId = sceneId;
    }

    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) {
        Map<String, Object> props = snapshot.properties();
        String lightType = stringProperty(props, "lightType", "point").toLowerCase();
        Map<String, Object> lightData = buildLightData(props, lightType);
        lightId = ServerLightingManager.getInstance().spawnLight(lightType, lightData);
        LOGGER.info("Created {} light {} for scene {}", lightType, lightId, sceneId);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) {
        if (lightId == -1L) {
            create(snapshot);
            return;
        }
        Map<String, Object> props = snapshot.properties();
        String lightType = stringProperty(props, "lightType", "point").toLowerCase();
        Map<String, Object> lightData = buildLightData(props, lightType);
        lightData.put("id", lightId);
        lightData.put("type", lightType);
        ServerLightingManager.getInstance().createOrUpdateLight(lightId, lightData);
    }

    @Override
    public void remove() {
        if (lightId != -1L) {
            ServerLightingManager.getInstance().removeLight(lightId);
            LOGGER.info("Removed light {} for scene {}", lightId, sceneId);
            lightId = -1L;
        }
    }

    private Map<String, Object> buildLightData(Map<String, Object> props, String lightType) {
        Map<String, Object> data = new HashMap<>();
        Vector3 position = vectorProperty(props.get("position"), new Vector3(0, 64, 0));
        data.put("x", position.x);
        data.put("y", position.y);
        data.put("z", position.z);
        Map<String, Object> color = mapProperty(props.get("color"));
        data.put("r", toDouble(color.get("r"), 1.0));
        data.put("g", toDouble(color.get("g"), 1.0));
        data.put("b", toDouble(color.get("b"), 1.0));
        double brightness = toDouble(props.get("intensity"), toDouble(props.get("brightness"), 1.0));
        data.put("brightness", brightness);
        double range = toDouble(props.get("range"), -1.0);
        if ("area".equals(lightType)) {
            data.put("width", toDouble(props.get("width"), 4.0));
            data.put("height", toDouble(props.get("height"), 4.0));
            double distance = toDouble(props.get("distance"), range > 0 ? range : 8.0);
            data.put("distance", distance);
            data.put("angle", toDouble(props.get("angle"), 45.0));
            Map<String, Object> dir = mapProperty(props.get("direction"));
            Vector3 direction = vectorProperty(dir, new Vector3(0, -1, 0));
            data.put("dirX", direction.x);
            data.put("dirY", direction.y);
            data.put("dirZ", direction.z);
        } else {
            double radius = toDouble(props.get("radius"), range > 0 ? range : 6.0);
            data.put("radius", radius);
        }
        return data;
    }

    private static Map<String, Object> mapProperty(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copy = new HashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    copy.put(String.valueOf(key), value);
                }
            });
            return copy;
        }
        return new HashMap<>();
    }

    private static String stringProperty(Map<String, Object> props, String key, String defaultValue) {
        Object value = props.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static Vector3 vectorProperty(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), fallback.x);
            double y = toDouble(map.get("y"), fallback.y);
            double z = toDouble(map.get("z"), fallback.z);
            return new Vector3(x, y, z);
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
}
