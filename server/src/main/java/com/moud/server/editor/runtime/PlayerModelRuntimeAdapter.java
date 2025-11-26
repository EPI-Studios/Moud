package com.moud.server.editor.runtime;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.proxy.PlayerModelProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class PlayerModelRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerModelRuntimeAdapter.class);
    private static final String DEFAULT_SKIN = "https://textures.minecraft.net/texture/45c338913be11c119f0e90a962f8d833b0dff78eaefdd8f2fa2a3434a1f2af0";

    private final String sceneId;
    private PlayerModelProxy proxy;
    private String objectId;

    public PlayerModelRuntimeAdapter(String sceneId) {
        this.sceneId = sceneId;
    }

    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) throws Exception {
        this.objectId = snapshot.objectId();
        Map<String, Object> props = snapshot.properties();
        Vector3 position = vectorProperty(props.get("position"), new Vector3(0, 64, 0));
        String skinUrl = stringProperty(props, "skinUrl", DEFAULT_SKIN);
        proxy = new PlayerModelProxy(position, skinUrl);
        proxy.setAutoAnimation(boolProperty(props, "autoAnimation", true));
        proxy.setLoopAnimation(boolProperty(props, "loopAnimation", true));
        proxy.setAnimationDuration(intProperty(props, "animationDuration", 2000));
        String override = stringProperty(props, "animationOverride", "");
        proxy.setManualAnimation(override.isBlank() ? null : override);
        LOGGER.info("Created player model '{}' for scene {}", objectId, sceneId);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) throws Exception {
        if (proxy == null) {
            create(snapshot);
            return;
        }
        Map<String, Object> props = snapshot.properties();
        if (props.containsKey("position")) {
            proxy.setPosition(vectorProperty(props.get("position"), proxy.getPosition()));
        }
        proxy.setSkin(stringProperty(props, "skinUrl", DEFAULT_SKIN));
        proxy.setAutoAnimation(boolProperty(props, "autoAnimation", true));
        proxy.setLoopAnimation(boolProperty(props, "loopAnimation", true));
        proxy.setAnimationDuration(intProperty(props, "animationDuration", 2000));
        String override = stringProperty(props, "animationOverride", "");
        proxy.setManualAnimation(override.isBlank() ? null : override);
    }

    @Override
    public void remove() {
        if (proxy != null) {
            proxy.remove();
            proxy = null;
        }
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

    private static String stringProperty(Map<String, Object> props, String key, String defaultValue) {
        Object value = props.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static boolean boolProperty(Map<String, Object> props, String key, boolean fallback) {
        Object value = props.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return fallback;
    }

    private static int intProperty(Map<String, Object> props, String key, int fallback) {
        Object value = props.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(value.toString()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
