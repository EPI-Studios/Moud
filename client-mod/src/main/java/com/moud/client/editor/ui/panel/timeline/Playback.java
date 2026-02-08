package com.moud.client.editor.ui.panel.timeline;

import com.moud.api.animation.AnimationClip;
import com.moud.api.animation.EventKeyframe;
import com.moud.api.animation.Keyframe;
import com.moud.api.animation.ObjectTrack;
import com.moud.api.animation.PropertyTrack;
import com.moud.api.math.Vector3;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import net.minecraft.util.math.MathHelper;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Playback {
    private Playback() {
    }

    public record TickResult(float currentTime, boolean playing) {
    }

    public static TickResult tickPlayback(
            SceneEditorOverlay overlay,
            AnimationClip currentClip,
            String selectedPath,
            float currentTime,
            boolean playing,
            boolean loop,
            float speed,
            float deltaSeconds
    ) {
        if (!playing || currentClip == null || deltaSeconds <= 0f) {
            return new TickResult(currentTime, playing);
        }
        float duration = Math.max(0.0001f, currentClip.duration());
        float newTime = currentTime + deltaSeconds * speed;
        boolean stillPlaying = playing;
        if (newTime > duration) {
            if (loop) {
                newTime = newTime % duration;
            } else {
                newTime = duration;
                stillPlaying = false;
                overlay.stopAnimation(resolveAnimationId(currentClip, selectedPath));
            }
        }
        overlay.seekAnimation(resolveAnimationId(currentClip, selectedPath), newTime);
        applyAnimationAtTime(currentClip, newTime);
        dispatchEventTrack(currentClip, newTime, overlay);
        return new TickResult(newTime, stillPlaying);
    }

    public static String resolveAnimationId(AnimationClip currentClip, String selectedPath) {
        if (selectedPath != null && !selectedPath.isBlank()) {
            return selectedPath;
        }
        if (currentClip != null && currentClip.id() != null) {
            return currentClip.id();
        }
        return "animation";
    }

    public static void dispatchEventTrack(AnimationClip currentClip, float time, SceneEditorOverlay overlay) {
        if (currentClip == null || currentClip.eventTrack() == null) return;
        float frameWindow = 1f / Math.max(1f, currentClip.frameRate());
        for (EventKeyframe ev : currentClip.eventTrack()) {
            if (Math.abs(ev.time() - time) < 1e-3 || (ev.time() <= time && ev.time() > time - frameWindow)) {
                overlay.triggerEvent(ev.name(), ev.payload());
            }
        }
    }

    public static void applyAnimationAtTime(AnimationClip currentClip, float timeSeconds) {
        if (currentClip == null || currentClip.objectTracks() == null) {
            return;
        }
        SceneSessionManager session = SceneSessionManager.getInstance();
        String sceneId = session.getActiveSceneId();
        if (sceneId == null) {
            return;
        }

        for (ObjectTrack objTrack : currentClip.objectTracks()) {
            if (objTrack == null || objTrack.propertyTracks() == null || objTrack.propertyTracks().isEmpty()) {
                continue;
            }
            SceneObject sceneObj = session.getSceneGraph().get(objTrack.targetObjectId());
            if (sceneObj == null) {
                continue;
            }

            Map<String, Object> props = sceneObj.getProperties();
            Vector3 basePos = readVec3(props.get("position"), Vector3.zero());
            Vector3 baseRot = readRotationVec(props.get("rotation"), Vector3.zero());
            Vector3 baseScale = readVec3(props.get("scale"), Vector3.one());

            Float posX = null, posY = null, posZ = null;
            Float rotX = null, rotY = null, rotZ = null;
            Float scaleX = null, scaleY = null, scaleZ = null;
            Map<String, Float> propertyUpdates = new HashMap<>();

            for (Map.Entry<String, PropertyTrack> entry : objTrack.propertyTracks().entrySet()) {
                PropertyTrack track = entry.getValue();
                if (track == null || track.keyframes() == null || track.keyframes().isEmpty()) {
                    continue;
                }
                String normalizedPath = normalizePropertyPath(entry.getKey());
                boolean limbPath = isLimbProperty(normalizedPath);
                boolean transformPath = !limbPath && isTransformPath(normalizedPath);

                float sampled = sampleTrack(track, timeSeconds, track.propertyType());

                if (transformPath) {
                    switch (normalizedPath) {
                        case "position.x" -> posX = sampled;
                        case "position.y" -> posY = sampled;
                        case "position.z" -> posZ = sampled;
                        case "rotation.x" -> rotX = sampled;
                        case "rotation.y" -> rotY = sampled;
                        case "rotation.z" -> rotZ = sampled;
                        case "scale.x" -> scaleX = sampled;
                        case "scale.y" -> scaleY = sampled;
                        case "scale.z" -> scaleZ = sampled;
                        default -> {
                        }
                    }
                } else if (normalizedPath != null) {
                    propertyUpdates.put(normalizedPath, sampled);
                }
            }

            Vector3 pos = null;
            if (posX != null || posY != null || posZ != null) {
                pos = new Vector3(
                        posX != null ? posX : basePos.x,
                        posY != null ? posY : basePos.y,
                        posZ != null ? posZ : basePos.z
                );
            }

            Vector3 rot = null;
            if (rotX != null || rotY != null || rotZ != null) {
                rot = new Vector3(
                        rotX != null ? rotX : baseRot.x,
                        rotY != null ? rotY : baseRot.y,
                        rotZ != null ? rotZ : baseRot.z
                );
            }

            Vector3 scale = null;
            if (scaleX != null || scaleY != null || scaleZ != null) {
                scale = new Vector3(
                        scaleX != null ? scaleX : baseScale.x,
                        scaleY != null ? scaleY : baseScale.y,
                        scaleZ != null ? scaleZ : baseScale.z
                );
            }

            boolean hasTransforms = pos != null || rot != null || scale != null;
            boolean hasProperties = !propertyUpdates.isEmpty();

            if (hasTransforms || hasProperties) {
                session.mergeAnimationTransform(
                        sceneId,
                        objTrack.targetObjectId(),
                        pos,
                        rot,
                        null,
                        scale,
                        hasProperties ? propertyUpdates : null
                );
            }
        }
    }

    private static float sampleTrack(PropertyTrack track, float timeSeconds, PropertyTrack.PropertyType type) {
        List<Keyframe> keyframes = track.keyframes();
        if (keyframes == null || keyframes.isEmpty()) {
            return clampValue(track, 0f);
        }
        if (keyframes.size() == 1) {
            return clampValue(track, keyframes.getFirst().value());
        }

        keyframes.sort(Comparator.comparing(Keyframe::time));

        if (timeSeconds <= keyframes.getFirst().time()) {
            return clampValue(track, keyframes.getFirst().value());
        }
        if (timeSeconds >= keyframes.getLast().time()) {
            return clampValue(track, keyframes.getLast().value());
        }

        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe a = keyframes.get(i);
            Keyframe b = keyframes.get(i + 1);
            if (timeSeconds < a.time() || timeSeconds > b.time()) {
                continue;
            }
            float span = b.time() - a.time();
            if (span < 1e-6f) {
                return clampValue(track, b.value());
            }
            float t = (timeSeconds - a.time()) / span;
            return clampValue(track, interpolate(a, b, t, type));
        }

        return clampValue(track, keyframes.getLast().value());
    }

    private static float interpolate(Keyframe a, Keyframe b, float t, PropertyTrack.PropertyType type) {
        float start = a.value();
        float end = b.value();
        if (type == PropertyTrack.PropertyType.ANGLE) {
            float delta = MathHelper.wrapDegrees(end - start);
            end = start + delta;
        }

        return switch (a.interpolation()) {
            case STEP -> start;
            case LINEAR -> start + (end - start) * t;
            case SMOOTH -> start + (end - start) * smoothStep(t);
            case EASE_IN -> start + (end - start) * (t * t);
            case EASE_OUT -> start + (end - start) * (1f - (1f - t) * (1f - t));
            case BEZIER -> {
                float dt = Math.max(1e-4f, b.time() - a.time());
                float m0 = a.outTangent() * dt;
                float m1 = b.inTangent() * dt;
                yield hermite(t, start, end, m0, m1);
            }
        };
    }

    private static float hermite(float t, float p0, float p1, float m0, float m1) {
        float t2 = t * t;
        float t3 = t2 * t;
        float h00 = 2f * t3 - 3f * t2 + 1f;
        float h10 = t3 - 2f * t2 + t;
        float h01 = -2f * t3 + 3f * t2;
        float h11 = t3 - t2;
        return h00 * p0 + h10 * m0 + h01 * p1 + h11 * m1;
    }

    private static float smoothStep(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float clampValue(PropertyTrack track, float value) {
        float min = track.minValue();
        float max = track.maxValue();
        if (!Float.isNaN(min)) {
            value = Math.max(min, value);
        }
        if (!Float.isNaN(max)) {
            value = Math.min(max, value);
        }
        return value;
    }

    private static Vector3 readVec3(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            float x = toFloat(map.get("x"), fallback.x);
            float y = toFloat(map.get("y"), fallback.y);
            float z = toFloat(map.get("z"), fallback.z);
            return new Vector3(x, y, z);
        }
        return new Vector3(fallback);
    }

    private static Vector3 readRotationVec(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            Object rx = map.containsKey("pitch") ? map.get("pitch") : map.get("x");
            Object ry = map.containsKey("yaw") ? map.get("yaw") : map.get("y");
            Object rz = map.containsKey("roll") ? map.get("roll") : map.get("z");
            float x = toFloat(rx, fallback.x);
            float y = toFloat(ry, fallback.y);
            float z = toFloat(rz, fallback.z);
            return new Vector3(x, y, z);
        }
        return new Vector3(fallback);
    }

    private static float toFloat(Object value, float fallback) {
        if (value instanceof Number num) {
            return num.floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static String normalizePropertyPath(String raw) {
        if (raw == null) {
            return null;
        }
        boolean hasPrefix = raw.startsWith("player_model:");
        String remainder = hasPrefix ? raw.substring("player_model:".length()) : raw;
        String lower = remainder.toLowerCase(Locale.ROOT);
        int dot = lower.indexOf('.');
        String limb = dot > 0 ? lower.substring(0, dot) : lower;
        String normalizedLimb = "body".equals(limb) ? "torso" : limb;
        boolean isLimb = switch (normalizedLimb) {
            case "head", "torso", "left_arm", "right_arm", "left_leg", "right_leg" -> true;
            default -> false;
        };
        if (!isLimb) {
            return raw;
        }
        String suffix = dot >= 0 && dot < remainder.length() ? remainder.substring(dot) : "";
        return "player_model:" + normalizedLimb + suffix;
    }

    private static boolean isLimbProperty(String path) {
        if (path == null) {
            return false;
        }
        String p = path;
        if (p.startsWith("player_model:")) {
            p = p.substring("player_model:".length());
        }
        int dot = p.indexOf('.');
        String limb = dot > 0 ? p.substring(0, dot) : p;
        return switch (limb) {
            case "head", "torso", "body", "left_arm", "right_arm", "left_leg", "right_leg" -> true;
            default -> false;
        };
    }

    private static boolean isTransformPath(String path) {
        if (path == null) {
            return false;
        }
        return path.equals("position.x") || path.equals("position.y") || path.equals("position.z")
                || path.equals("rotation.x") || path.equals("rotation.y") || path.equals("rotation.z")
                || path.equals("scale.x") || path.equals("scale.y") || path.equals("scale.z");
    }

    public static String formatTime(double seconds) {
        int totalFrames = (int) Math.round(seconds * 60.0);
        int frames = totalFrames % 60;
        int secs = (int) seconds % 60;
        int mins = (int) (seconds / 60.0);
        return String.format("%02d:%02d:%02d", mins, secs, frames);
    }
}
