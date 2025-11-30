package com.moud.server.editor.runtime;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.fakeplayer.FakePlayerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlayerModelRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerModelRuntimeAdapter.class);
    private static final String DEFAULT_SKIN = "https://textures.minecraft.net/texture/45c338913be11c119f0e90a962f8d833b0dff78eaefdd8f2fa2a3434a1f2af0";

    private final String sceneId;
    private String objectId;
    private Long fakeId;
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
        createInternal(snapshot, null);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) throws Exception {
        int signature = computeSignature(snapshot);
        if (signature == lastSignature && fakeId != null) {
            return;
        }
        lastSignature = signature;

        Map<String, Object> props = snapshot.properties();
        Vector3 position = vectorProperty(props.get("position"), new Vector3(0, 64, 0));
        String skinUrl = stringProperty(props, "skinUrl", DEFAULT_SKIN);
        Quaternion rotation = rotationProperty(props, Quaternion.identity());
        double width = doubleProperty(props, "width", 0.6);
        double height = doubleProperty(props, "height", 1.8);
        boolean physics = boolProperty(props, "physicsEnabled", false);
        boolean sneaking = boolProperty(props, "sneaking", false);
        boolean sprinting = boolProperty(props, "sprinting", false);
        boolean swinging = boolProperty(props, "swinging", false);
        boolean usingItem = boolProperty(props, "usingItem", false);
        List<MoudPackets.FakePlayerWaypoint> path = pathProperty(props.get("path"));
        double pathSpeed = doubleProperty(props, "pathSpeed", 0.0);
        boolean pathLoop = boolProperty(props, "pathLoop", false);
        boolean pathPingPong = boolProperty(props, "pathPingPong", false);

        MoudPackets.FakePlayerDescriptor descriptor = new MoudPackets.FakePlayerDescriptor(
                fakeId != null ? fakeId : 0L,
                stringProperty(props, "label", "Fake Player"),
                skinUrl,
                position,
                rotation,
                width,
                height,
                physics,
                sneaking,
                sprinting,
                swinging,
                usingItem,
                path,
                pathSpeed,
                pathLoop,
                pathPingPong
        );

        if (fakeId == null) {
            fakeId = FakePlayerManager.getInstance().spawn(descriptor).getId();
            LOGGER.info("Created fake player '{}' for scene {}", objectId, sceneId);
        } else {
            FakePlayerManager.getInstance().update(descriptor);
        }
        applyAnimationOverride(props);
    }

    private void createInternal(MoudPackets.SceneObjectSnapshot snapshot, Long reuseId) throws Exception {
        this.objectId = snapshot.objectId();
        Map<String, Object> props = snapshot.properties();
        Vector3 position = vectorProperty(props.get("position"), new Vector3(0, 64, 0));
        String skinUrl = stringProperty(props, "skinUrl", DEFAULT_SKIN);
        Quaternion rotation = rotationProperty(props, Quaternion.identity());
        double width = doubleProperty(props, "width", 0.6);
        double height = doubleProperty(props, "height", 1.8);
        boolean physics = boolProperty(props, "physicsEnabled", false);
        boolean sneaking = boolProperty(props, "sneaking", false);
        boolean sprinting = boolProperty(props, "sprinting", false);
        boolean swinging = boolProperty(props, "swinging", false);
        boolean usingItem = boolProperty(props, "usingItem", false);
        List<MoudPackets.FakePlayerWaypoint> path = pathProperty(props.get("path"));
        double pathSpeed = doubleProperty(props, "pathSpeed", 0.0);
        boolean pathLoop = boolProperty(props, "pathLoop", false);
        boolean pathPingPong = boolProperty(props, "pathPingPong", false);

        MoudPackets.FakePlayerDescriptor descriptor = new MoudPackets.FakePlayerDescriptor(
                reuseId != null ? reuseId : 0L,
                stringProperty(props, "label", "Fake Player"),
                skinUrl,
                position,
                rotation,
                width,
                height,
                physics,
                sneaking,
                sprinting,
                swinging,
                usingItem,
                path,
                pathSpeed,
                pathLoop,
                pathPingPong
        );
        fakeId = FakePlayerManager.getInstance().spawn(descriptor).getId();
        LOGGER.info("Created fake player '{}' for scene {}", objectId, sceneId);
        applyAnimationOverride(props);
    }

    private static int computeSignature(MoudPackets.SceneObjectSnapshot snapshot) {
        Map<String, Object> props = snapshot.properties();
        return java.util.Objects.hash(snapshot.objectId(), props);
    }

    private void applyAnimationOverride(Map<String, Object> props) {
        if (fakeId == null) {
            return;
        }
        boolean autoAnimation = boolProperty(props, "autoAnimation", true);
        boolean loopAnimation = boolProperty(props, "loopAnimation", true);
        String anim = stringProperty(props, "animationOverride", "");
        int durationMs = intProperty(props, "animationDuration", 2000);

        if (!autoAnimation && anim.isBlank()) {
            FakePlayerManager.getInstance().playAnimation(fakeId, "", 0);
            return;
        }

        String targetAnim = anim.isBlank() ? "moud:idle" : anim;
        int duration = loopAnimation ? 0 : durationMs;
        if (targetAnim.equals(lastAnim) && loopAnimation == lastLoop && autoAnimation == lastAuto && durationMs == lastDuration) {
            return;
        }
        FakePlayerManager.getInstance().playAnimation(fakeId, targetAnim, duration);
        lastAnim = targetAnim;
        lastLoop = loopAnimation;
        lastAuto = autoAnimation;
        lastDuration = durationMs;
    }

    @Override
    public void remove() {
        if (fakeId != null) {
            FakePlayerManager.getInstance().remove(fakeId);
            fakeId = null;
        }
    }

    public void applyPropertyChange(String key, Object value) throws Exception {
        if (fakeId == null) {
            return;
        }
        switch (key) {
            case "position" -> {
                Vector3 pos = vectorProperty(value, null);
                if (pos != null) FakePlayerManager.getInstance().teleport(fakeId, pos);
            }
            case "rotation", "rotationQuat" -> {
                Quaternion rot = rotationProperty(Map.of(key, value), null);
                if (rot != null) FakePlayerManager.getInstance().setRotation(fakeId, rot);
            }
            case "sneaking", "sprinting", "swinging", "usingItem" -> {
                FakePlayerManager.getInstance().setStateFlag(fakeId, key, boolProperty(Map.of(key, value), key, false));
            }
            case "animationOverride", "autoAnimation", "loopAnimation", "animationDuration" -> {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("autoAnimation", lastAuto);
                map.put("loopAnimation", lastLoop);
                map.put("animationOverride", lastAnim);
                map.put("animationDuration", lastDuration);
                map.put(key, value);

                boolean autoAnimation = boolProperty(map, "autoAnimation", lastAuto);
                boolean loopAnimation = boolProperty(map, "loopAnimation", lastLoop);
                String anim = stringProperty(map, "animationOverride", lastAnim);
                int durationMs = intProperty(map, "animationDuration", lastDuration);

                if (!autoAnimation && anim.isBlank()) {
                    FakePlayerManager.getInstance().playAnimation(fakeId, "", 0);
                    lastAnim = anim;
                    lastLoop = loopAnimation;
                    lastAuto = autoAnimation;
                    lastDuration = durationMs;
                    return;
                }
                String targetAnim = anim.isBlank() ? "moud:idle" : anim;
                if (!targetAnim.equals(lastAnim) || loopAnimation != lastLoop || autoAnimation != lastAuto || durationMs != lastDuration) {
                    int duration = loopAnimation ? 0 : durationMs;
                    FakePlayerManager.getInstance().playAnimation(fakeId, targetAnim, duration);
                    lastAnim = targetAnim;
                    lastLoop = loopAnimation;
                    lastAuto = autoAnimation;
                    lastDuration = durationMs;
                }
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

    private static List<MoudPackets.FakePlayerWaypoint> pathProperty(Object raw) {
        List<MoudPackets.FakePlayerWaypoint> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> map) {
                    Vector3 v = vectorProperty(map, null);
                    if (v != null) {
                        out.add(new MoudPackets.FakePlayerWaypoint(v));
                    }
                }
            }
        }
        return out;
    }
}
