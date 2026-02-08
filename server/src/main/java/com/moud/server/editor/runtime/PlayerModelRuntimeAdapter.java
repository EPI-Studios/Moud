package com.moud.server.editor.runtime;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.proxy.PlayerModelProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class PlayerModelRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerModelRuntimeAdapter.class);
    private static final String DEFAULT_SKIN = "https://textures.minecraft.net/texture/45c338913be11c119f0e90a962f8d833b0dff78eaefdd8f2fa2a3434a1f2af0";

    private final String sceneId;
    private String objectId;
    private PlayerModelProxy model;
    private int lastSignature;
    private String lastAnim = "";
    private boolean lastLoop = true;
    private boolean lastAuto = true;
    private int lastDuration = 2000;

    public PlayerModelRuntimeAdapter(String sceneId) {
        this.sceneId = sceneId;
    }

    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) throws Exception {
        lastSignature = computeSignature(snapshot);
        this.objectId = snapshot.objectId();
        applySnapshot(snapshot.properties(), true);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) throws Exception {
        int signature = computeSignature(snapshot);
        if (signature == lastSignature && model != null) {
            return;
        }
        lastSignature = signature;

        this.objectId = snapshot.objectId();
        applySnapshot(snapshot.properties(), false);
    }

    private void applySnapshot(Map<String, Object> props, boolean creating) {
        Vector3 position = vectorProperty(props.get("position"), new Vector3(0, 64, 0));
        String skinUrl = stringProperty(props, "skinUrl", DEFAULT_SKIN);
        Quaternion rotation = rotationProperty(props, Quaternion.identity());
        Vector3 euler = rotation.toEuler();
        float yaw = (float) euler.y;
        float pitch = (float) euler.x;
        String instanceName = stringProperty(props, "instanceName", null);

        if (creating || model == null) {
            model = new PlayerModelProxy(position, skinUrl);
            LOGGER.info("Created player model '{}' for scene {}", objectId, sceneId);
        } else {
            model.setPosition(position);
            model.setSkin(skinUrl);
        }
        if (instanceName != null && !instanceName.isBlank()) {
            model.setInstance(instanceName);
        }
        model.setRotation(yaw, pitch);
        applyAnimationOverride(props);
    }

    private static int computeSignature(MoudPackets.SceneObjectSnapshot snapshot) {
        Map<String, Object> props = snapshot.properties();
        return java.util.Objects.hash(snapshot.objectId(), props);
    }

    private void applyAnimationOverride(Map<String, Object> props) {
        if (model == null) {
            return;
        }
        boolean autoAnimation = boolProperty(props, "autoAnimation", true);
        boolean loopAnimation = boolProperty(props, "loopAnimation", true);
        String anim = stringProperty(props, "animationOverride", "");
        int durationMs = intProperty(props, "animationDuration", 2000);

        model.setAutoAnimation(autoAnimation);
        model.setLoopAnimation(loopAnimation);
        model.setAnimationDuration(durationMs);

        String targetAnim = anim == null ? "" : anim;
        if (autoAnimation && targetAnim.isBlank()) {
            if (!lastAuto || !lastAnim.isBlank()) {
                model.clearManualAnimation();
            }
        } else {
            String selectedAnim = targetAnim.isBlank() ? "moud:player_animations/idle" : targetAnim;
            if (!selectedAnim.equals(lastAnim) || loopAnimation != lastLoop || autoAnimation != lastAuto || durationMs != lastDuration) {
                model.setManualAnimation(selectedAnim);
            }
            targetAnim = selectedAnim;
        }
        lastAnim = targetAnim;
        lastLoop = loopAnimation;
        lastAuto = autoAnimation;
        lastDuration = durationMs;
    }

    @Override
    public void remove() {
        if (model != null) {
            model.remove();
            model = null;
        }
    }

    public void applyPropertyChange(String key, Object value) throws Exception {
        if (model == null) {
            return;
        }
        switch (key) {
            case "position" -> {
                Vector3 pos = vectorProperty(value, null);
                if (pos != null) {
                    model.setPosition(pos);
                }
            }
            case "rotation", "rotationQuat" -> {
                Quaternion rot = rotationProperty(Map.of(key, value), null);
                if (rot != null) {
                    Vector3 euler = rot.toEuler();
                    model.setRotation((float) euler.y, (float) euler.x);
                }
            }
            case "skinUrl" -> model.setSkin(stringProperty(Map.of("skinUrl", value), "skinUrl", DEFAULT_SKIN));
            case "instanceName" -> {
                String instance = stringProperty(Map.of("instanceName", value), "instanceName", null);
                if (instance != null) {
                    model.setInstance(instance);
                }
            }
            case "animationOverride", "autoAnimation", "loopAnimation", "animationDuration" -> {
                Map<String, Object> map = new HashMap<>();
                map.put("autoAnimation", lastAuto);
                map.put("loopAnimation", lastLoop);
                map.put("animationOverride", lastAnim);
                map.put("animationDuration", lastDuration);
                map.put(key, value);

                applyAnimationOverride(map);
            }
            default -> {
                // ignore unhandled properties for lightweight updates
            }
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

    private static double doubleProperty(Map<String, Object> props, String key, double fallback) {
        Object value = props.get(key);
        return toDouble(value, fallback);
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

    private static Quaternion rotationProperty(Map<String, Object> props, Quaternion fallback) {
        Object rawQuat = props.get("rotationQuat");
        if (rawQuat instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), 0);
            double y = toDouble(map.get("y"), 0);
            double z = toDouble(map.get("z"), 0);
            double w = toDouble(map.get("w"), 1);
            return new Quaternion((float) x, (float) y, (float) z, (float) w);
        }
        Object raw = props.get("rotation");
        if (raw instanceof Map<?, ?> map) {
            boolean hasEuler = map.containsKey("pitch") || map.containsKey("yaw") || map.containsKey("roll");
            double pitch = hasEuler ? toDouble(map.get("pitch"), 0) : toDouble(map.get("x"), 0);
            double yaw = hasEuler ? toDouble(map.get("yaw"), 0) : toDouble(map.get("y"), 0);
            double roll = hasEuler ? toDouble(map.get("roll"), 0) : toDouble(map.get("z"), 0);
            return Quaternion.fromEuler((float) pitch, (float) yaw, (float) roll);
        }
        return fallback;
    }
}
