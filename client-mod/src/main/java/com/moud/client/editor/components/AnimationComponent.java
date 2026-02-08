package com.moud.client.editor.components;

import com.moud.client.editor.scene.SceneObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class AnimationComponent {
    public static final String KEY = "animation";

    private AnimationComponent() {
    }

    public static boolean has(SceneObject object) {
        return object != null && object.getProperties().containsKey(KEY);
    }

    @SuppressWarnings("unchecked")
    public static AnimationComponentData get(SceneObject object) {
        if (!has(object)) {
            return null;
        }
        Object raw = object.getProperties().get(KEY);
        if (raw instanceof Map<?, ?> map) {
            return AnimationComponentData.from((Map<String, Object>) map);
        }
        return null;
    }

    public static void attach(SceneObject object, AnimationComponentData data) {
        if (object == null || data == null) {
            return;
        }
        Map<String, Object> props = new ConcurrentHashMap<>(object.getProperties());
        props.put(KEY, data.toMap());
        object.overwriteProperties(props);
    }

    public record AnimationComponentData(
            String animationClipId,
            String animationClipPath,
            boolean playOnStart,
            boolean loop,
            float speed
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new ConcurrentHashMap<>();
            map.put("animationClipId", animationClipId);
            map.put("animationClipPath", animationClipPath);
            map.put("playOnStart", playOnStart);
            map.put("loop", loop);
            map.put("speed", speed);
            return map;
        }

        static AnimationComponentData from(Map<String, Object> map) {
            String id = string(map.get("animationClipId"));
            String path = string(map.get("animationClipPath"));
            boolean play = bool(map.get("playOnStart"), false);
            boolean lp = bool(map.get("loop"), false);
            float spd = number(map.get("speed"), 1.0f);
            return new AnimationComponentData(id, path, play, lp, spd);
        }

        private static String string(Object value) {
            return value == null ? null : String.valueOf(value);
        }

        private static boolean bool(Object value, boolean fallback) {
            if (value instanceof Boolean b) return b;
            if (value instanceof Number n) return n.intValue() != 0;
            if (value != null) return Boolean.parseBoolean(value.toString());
            return fallback;
        }

        private static float number(Object value, float fallback) {
            if (value instanceof Number n) return n.floatValue();
            try {
                return value != null ? Float.parseFloat(value.toString()) : fallback;
            } catch (Exception e) {
                return fallback;
            }
        }
    }
}
