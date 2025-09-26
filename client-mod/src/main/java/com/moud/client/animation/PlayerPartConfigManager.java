package com.moud.client.animation;

import com.moud.api.math.Vector3;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerPartConfigManager {
    private static final PlayerPartConfigManager INSTANCE = new PlayerPartConfigManager();
    private final Map<UUID, Map<String, PartConfig>> playerConfigs = new ConcurrentHashMap<>();


    private PlayerPartConfigManager() {}

    public static PlayerPartConfigManager getInstance() {
        return INSTANCE;
    }

    public void updatePartConfig(UUID playerId, String partName, Map<String, Object> properties) {
        playerConfigs.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(partName, k -> new PartConfig())
                .update(properties);
    }

    public PartConfig getPartConfig(UUID playerId, String partName) {
        Map<String, PartConfig> parts = playerConfigs.get(playerId);
        return parts != null ? parts.get(partName) : null;
    }

    public void clearConfig(UUID playerId) {
        playerConfigs.remove(playerId);
    }

    public static class PartConfig {
        public Vector3 position;
        public Vector3 rotation;
        public Vector3 scale;
        public Boolean visible;

        public void update(Map<String, Object> props) {
            if (props.containsKey("position")) this.position = parseVector(props.get("position"));
            if (props.containsKey("rotation")) this.rotation = parseVector(props.get("rotation"));
            if (props.containsKey("scale")) this.scale = parseVector(props.get("scale"));
            if (props.containsKey("visible")) {
                Object visibleProp = props.get("visible");
                if (visibleProp instanceof Boolean) {
                    this.visible = (Boolean) visibleProp;
                }
            }
        }

        private Vector3 parseVector(Object obj) {
            if (obj instanceof Vector3) {
                return (Vector3) obj;
            }
            if (obj instanceof Map<?,?> map) {
                Number xVal = (Number) map.get("x");
                Number yVal = (Number) map.get("y");
                Number zVal = (Number) map.get("z");

                double x = (xVal != null) ? xVal.doubleValue() : 0.0;
                double y = (yVal != null) ? yVal.doubleValue() : 0.0;
                double z = (zVal != null) ? zVal.doubleValue() : 0.0;

                return new Vector3(x, y, z);
            }

            return null;
        }
    }



}