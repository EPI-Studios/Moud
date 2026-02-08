package com.moud.server.editor.runtime;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.instance.InstanceManager;
import com.moud.server.proxy.MediaDisplayProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public final class DisplayRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayRuntimeAdapter.class);

    private final String sceneId;
    private MediaDisplayProxy display;
    private boolean lastPbrEnabled = false;
    private String lastPbrBaseColor = "";
    private String lastPbrNormal = "";
    private String lastPbrMetallicRoughness = "";
    private String lastPbrEmissive = "";
    private String lastPbrOcclusion = "";
    private double lastPbrMetallicFactor = 0.0;
    private double lastPbrRoughnessFactor = 1.0;

    public DisplayRuntimeAdapter(String sceneId) {
        this.sceneId = sceneId;
    }

    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) throws Exception {
        Map<String, Object> props = snapshot.properties();
        Vector3 position = vectorProperty(props.get("position"), new Vector3(0, 64, 0));
        Quaternion rotation = quaternionProperty(props, Quaternion.identity());
        Vector3 scale = vectorProperty(props.get("scale"), new Vector3(3, 2, 0.1));

        display = new MediaDisplayProxy(
                InstanceManager.getInstance().getDefaultInstance(),
                position,
                rotation,
                scale
        );

        applyContent(props);
        applyLooping(props);
        applyPbr(props);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) throws Exception {
        if (display == null) {
            create(snapshot);
            return;
        }
        Map<String, Object> props = snapshot.properties();
        Vector3 position = vectorProperty(props.get("position"), display.getPosition());
        Quaternion rotation = quaternionProperty(props, display.getRotation());
        Vector3 scale = vectorProperty(props.get("scale"), display.getScale());
        display.setTransform(position, rotation, scale);
        applyContent(props);
        applyLooping(props);
        applyPbr(props);
    }

    @Override
    public void remove() {
        if (display != null) {
            try {
                display.remove();
            } catch (Exception e) {
                LOGGER.warn("Failed to remove display runtime for scene {}", sceneId, e);
            }
            display = null;
        }
    }

    private void applyContent(Map<String, Object> props) {
        if (display == null) {
            return;
        }
        String content = stringProperty(props, "displayContent", "");
        if (content.isEmpty()) {
            return;
        }
        String type = stringProperty(props, "displayType", "image").toLowerCase();
        switch (type) {
            case "video", "url" -> {
                double frameRate = toDouble(props.get("frameRate"), 24.0);
                boolean loop = boolProperty(props.get("loop"), true);
                display.setVideo(content, frameRate, loop);
                if (boolProperty(props.get("playing"), true)) {
                    display.play();
                }
            }
            case "sequence" -> {
                Object framesRaw = props.get("frameSources");
                if (framesRaw instanceof List<?> frames && !frames.isEmpty()) {
                    String[] sources = frames.stream().map(String::valueOf).toArray(String[]::new);
                    double fps = toDouble(props.get("frameRate"), 12.0);
                    boolean loop = boolProperty(props.get("loop"), true);
                    display.setFrameSequence(sources, fps, loop);
                } else {
                    display.setImage(content);
                }
            }
            default -> display.setImage(content);
        }
    }

    private void applyLooping(Map<String, Object> props) {
        if (display == null) {
            return;
        }
        if (boolProperty(props.get("playing"), true)) {
            display.play();
        } else {
            display.pause();
        }
    }

    private void applyPbr(Map<String, Object> props) {
        if (display == null || props == null) {
            return;
        }

        boolean enabled = boolProperty(props.get("pbrEnabled"), false);
        String baseColor = stringProperty(props, "pbrBaseColor", "").trim();
        String normal = stringProperty(props, "pbrNormal", "").trim();
        String mr = stringProperty(props, "pbrMetallicRoughness", "").trim();
        String emissive = stringProperty(props, "pbrEmissive", "").trim();
        String occlusion = stringProperty(props, "pbrOcclusion", "").trim();
        double metallicFactor = toDouble(props.getOrDefault("pbrMetallicFactor", 0.0), 0.0);
        double roughnessFactor = toDouble(props.getOrDefault("pbrRoughnessFactor", 1.0), 1.0);

        boolean changed = enabled != lastPbrEnabled
                || !baseColor.equals(lastPbrBaseColor)
                || !normal.equals(lastPbrNormal)
                || !mr.equals(lastPbrMetallicRoughness)
                || !emissive.equals(lastPbrEmissive)
                || !occlusion.equals(lastPbrOcclusion)
                || Math.abs(metallicFactor - lastPbrMetallicFactor) > 1.0e-6
                || Math.abs(roughnessFactor - lastPbrRoughnessFactor) > 1.0e-6;
        if (!changed) {
            return;
        }

        lastPbrEnabled = enabled;
        lastPbrBaseColor = baseColor;
        lastPbrNormal = normal;
        lastPbrMetallicRoughness = mr;
        lastPbrEmissive = emissive;
        lastPbrOcclusion = occlusion;
        lastPbrMetallicFactor = metallicFactor;
        lastPbrRoughnessFactor = roughnessFactor;

        display.setPbrState(
                enabled,
                baseColor,
                normal,
                mr,
                emissive,
                occlusion,
                metallicFactor,
                roughnessFactor
        );
    }

    private static String stringProperty(Map<String, Object> props, String key, String defaultValue) {
        Object value = props.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static boolean boolProperty(Object raw, boolean fallback) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        if (raw != null) {
            return Boolean.parseBoolean(raw.toString());
        }
        return fallback;
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

    private static Quaternion quaternionProperty(Map<String, Object> props, Quaternion fallback) {
        Object rawQuat = props.get("rotationQuat");
        if (rawQuat instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), 0);
            double y = toDouble(map.get("y"), 0);
            double z = toDouble(map.get("z"), 0);
            double w = toDouble(map.get("w"), 1);
            return new Quaternion((float) x, (float) y, (float) z, (float) w);
        }
        return quaternionFromEuler(props.get("rotation"), fallback);
    }

    private static Quaternion quaternionFromEuler(Object raw, Quaternion fallback) {
        if (raw instanceof Map<?, ?> map) {
            boolean hasEuler = map.containsKey("pitch") || map.containsKey("yaw") || map.containsKey("roll");
            double pitch = hasEuler ? toDouble(map.get("pitch"), 0) : toDouble(map.get("x"), 0);
            double yaw = hasEuler ? toDouble(map.get("yaw"), 0) : toDouble(map.get("y"), 0);
            double roll = hasEuler ? toDouble(map.get("roll"), 0) : toDouble(map.get("z"), 0);
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
}
