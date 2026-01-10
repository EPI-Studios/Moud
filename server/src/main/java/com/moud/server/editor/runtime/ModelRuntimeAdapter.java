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

import java.util.Objects;
import java.util.Map;

public final class ModelRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRuntimeAdapter.class);

    private final String sceneId;
    private ModelProxy model;
    private String objectId;
    private String lastAnimClip = "";
    private boolean lastAnimPlaying = false;
    private boolean lastAnimLoop = true;
    private double lastAnimSpeed = 1.0;
    private double lastAnimTime = 0.0;

    public ModelRuntimeAdapter(String sceneId) {
        this.sceneId = sceneId;
    }

    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) throws Exception {
        this.objectId = snapshot.objectId();
        Map<String, Object> props = snapshot.properties();
        String modelPath = stringProperty(props, "modelPath", "moud:models/capsule.obj");
        Vector3 position = vectorProperty(props.get("position"), new Vector3(0, 64, 0));
        Quaternion rotation = quaternionProperty(props, Quaternion.identity());
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
        applyAnimation(props);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) throws Exception {
        if (model == null) {
            create(snapshot);
            return;
        }
        Map<String, Object> props = snapshot.properties();
        Vector3 position = vectorProperty(props.get("position"), model.getPosition());
        Quaternion rotation = quaternionProperty(props, model.getRotation());
        Vector3 scale = vectorProperty(props.get("scale"), model.getScale());
        String texture = stringProperty(props, "texture", model.getTexture());

        model.setTransform(position, rotation, scale);
        if (!Objects.equals(texture, model.getTexture())) {
            LOGGER.info("Scene '{}' updating model {} texture from '{}' to '{}'", sceneId, model.getId(), model.getTexture(), texture);
            model.setTexture(texture);
        }
        applyAnimation(props);
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
        if (raw instanceof Map<?,?> map) {
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

    private static boolean boolProperty(Object raw, boolean fallback) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        if (raw instanceof String s) {
            String trimmed = s.trim().toLowerCase();
            if (trimmed.isEmpty()) {
                return fallback;
            }
            if ("true".equals(trimmed) || "1".equals(trimmed) || "yes".equals(trimmed) || "on".equals(trimmed)) {
                return true;
            }
            if ("false".equals(trimmed) || "0".equals(trimmed) || "no".equals(trimmed) || "off".equals(trimmed)) {
                return false;
            }
        }
        return fallback;
    }

    private void applyAnimation(Map<String, Object> props) {
        if (model == null || props == null) {
            return;
        }

        String clip = stringProperty(props, "animationClip", "").trim();
        Integer clipIndex = parseAnimationIndex(clip);
        boolean playing = boolProperty(props.getOrDefault("animationPlaying", false), false);
        boolean loop = boolProperty(props.getOrDefault("animationLoop", true), true);
        double speed = toDouble(props.getOrDefault("animationSpeed", 1.0), 1.0);
        double time = toDouble(props.getOrDefault("animationTime", 0.0), 0.0);

        boolean clipChanged = !Objects.equals(clip, lastAnimClip);
        boolean playingChanged = playing != lastAnimPlaying;
        boolean loopChanged = loop != lastAnimLoop;
        boolean speedChanged = Math.abs(speed - lastAnimSpeed) > 1.0e-6;
        boolean timeChanged = Math.abs(time - lastAnimTime) > 1.0e-6;

        if (!clipChanged && !playingChanged && !loopChanged && !speedChanged && !timeChanged) {
            return;
        }

        lastAnimClip = clip;
        lastAnimPlaying = playing;
        lastAnimLoop = loop;
        lastAnimSpeed = speed;
        lastAnimTime = time;

        if (clip.isEmpty()) {
            model.stopAnimation();
            return;
        }

        if (clipChanged) {
            if (clipIndex != null) {
                model.playAnimation(clipIndex.intValue());
            } else {
                model.playAnimation(clip);
            }
            model.setLoopAnimation(loop);
            model.setAnimationSpeed(speed);
            model.seekAnimation(time);
            if (!playing) {
                model.pauseAnimation();
            }
            return;
        }

        if (loopChanged) {
            model.setLoopAnimation(loop);
        }
        if (speedChanged) {
            model.setAnimationSpeed(speed);
        }
        if (timeChanged) {
            model.seekAnimation(time);
        }
        if (playingChanged) {
            if (playing) {
                if (clipIndex != null) {
                    model.playAnimation(clipIndex.intValue());
                } else {
                    model.playAnimation(clip);
                }
            } else {
                model.pauseAnimation();
            }
        }
    }

    private static Integer parseAnimationIndex(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase();
        if (!lower.startsWith("animation_")) {
            return null;
        }
        String remainder = trimmed.substring("animation_".length()).trim();
        try {
            int index = Integer.parseInt(remainder);
            return index >= 0 ? index : null;
        } catch (NumberFormatException ignored) {
            return null;
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
