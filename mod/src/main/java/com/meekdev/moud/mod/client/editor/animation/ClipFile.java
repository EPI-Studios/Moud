package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.scene.Json;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClipFile {

    private static final int DECIMALS = 5;

    private ClipFile() {}

    public static AnimClip read(String text) {
        Object parsed = Json.parse(text);
        if (!(parsed instanceof Map<?, ?> root)) throw new IllegalArgumentException("an animation file holds a JSON object");
        if (root.get("keyframes") != null && root.get("channels") == null) return readV1(root);
        return readV2(root);
    }

    public static boolean isV1(String text) {
        Object parsed = Json.parse(text);
        return parsed instanceof Map<?, ?> root && root.get("keyframes") != null && root.get("channels") == null;
    }

    private static AnimClip readV2(Map<?, ?> root) {
        AnimClip clip = new AnimClip();
        clip.format = 2;
        if (root.get("fps") != null) clip.fps = Math.max(1, (int) Math.round(number(root.get("fps"), "fps")));
        if (root.get("loop") != null) clip.loop = AnimClip.Loop.named(String.valueOf(root.get("loop")));
        if (root.get("priority") != null) clip.priority = priority(root.get("priority"));
        if (root.get("rig") != null) clip.rig = String.valueOf(root.get("rig"));
        if (root.get("space") != null) clip.space = AnimClip.Space.named(String.valueOf(root.get("space")));
        if (root.get("blend") != null) clip.blend = AnimClip.Blend.named(String.valueOf(root.get("blend")));
        for (Object joint : list(root.get("mask"))) clip.mask.add(String.valueOf(joint));
        if (root.get("channels") instanceof Map<?, ?> channels) {
            for (Map.Entry<?, ?> joint : channels.entrySet()) {
                if (!(joint.getValue() instanceof Map<?, ?> tracks)) throw new IllegalArgumentException("channels of " + joint.getKey() + " must be an object");
                for (Map.Entry<?, ?> track : tracks.entrySet()) {
                    Channel channel = Channel.named(String.valueOf(track.getKey()));
                    if (channel == null) throw new IllegalArgumentException("unknown channel " + track.getKey() + " on " + joint.getKey());
                    for (Object entry : list(track.getValue())) clip.put(String.valueOf(joint.getKey()), channel, key(entry));
                }
            }
        }
        readTimed(root, clip);
        if (root.get("viewModel") instanceof Map<?, ?> view) {
            clip.view = new AnimClip.ViewModel(view.get("model") == null ? "" : String.valueOf(view.get("model")));
        }
        clip.length = root.get("length") == null ? clip.lastKeyTime() : number(root.get("length"), "length");
        return clip;
    }

    private static void readTimed(Map<?, ?> root, AnimClip clip) {
        for (Object entry : list(root.get("markers"))) {
            if (!(entry instanceof Map<?, ?> marker)) throw new IllegalArgumentException("each marker must be an object");
            clip.markers.add(new AnimClip.Marker(number(marker.get("time"), "marker time"), String.valueOf(marker.get("name")),
                    marker.get("value") == null ? "" : String.valueOf(marker.get("value"))));
        }
        for (Object entry : list(root.get("events"))) {
            if (!(entry instanceof Map<?, ?> event)) throw new IllegalArgumentException("each event must be an object");
            Map<String, Object> payload = new LinkedHashMap<>();
            if (event.get("payload") instanceof Map<?, ?> fields) {
                for (Map.Entry<?, ?> field : fields.entrySet()) payload.put(String.valueOf(field.getKey()), field.getValue() == null ? "" : field.getValue());
            }
            String sound = "";
            String particle = "";
            boolean preview = false;
            if (event.get("preview") instanceof Map<?, ?> shown) {
                sound = shown.get("sound") == null ? "" : String.valueOf(shown.get("sound"));
                particle = shown.get("particle") == null ? "" : String.valueOf(shown.get("particle"));
                preview = !Boolean.FALSE.equals(shown.get("enabled"));
            }
            clip.events.add(new AnimClip.Event(number(event.get("time"), "event time"), String.valueOf(event.get("name")),
                    event.get("on") == null ? AnimClip.Side.BOTH : AnimClip.Side.named(String.valueOf(event.get("on"))),
                    payload, sound, particle, preview));
        }
        clip.sortTimed();
    }

    private static AnimKey key(Object entry) {
        if (!(entry instanceof List<?> parts) || parts.size() < 2) throw new IllegalArgumentException("a key is [time, [x, y, z], interpolation]");
        double time = number(parts.get(0), "key time");
        Vector3 value = vector(parts.get(1), "key value");
        Interp interp = parts.size() > 2 && parts.get(2) != null ? Interp.named(String.valueOf(parts.get(2))) : Interp.LINEAR;
        AnimKey.Handle in = null;
        AnimKey.Handle out = null;
        if (parts.size() > 3 && parts.get(3) instanceof Map<?, ?> handles) {
            in = handle(handles.get("in"));
            out = handle(handles.get("out"));
        }
        return new AnimKey(time, value, interp, in, out);
    }

    private static AnimKey.Handle handle(Object value) {
        if (value == null) return null;
        if (!(value instanceof List<?> pair) || pair.size() != 2) throw new IllegalArgumentException("a handle is [dt, dv]");
        double dt = number(pair.get(0), "handle time");
        Object dv = pair.get(1);
        if (dv instanceof Number same) return new AnimKey.Handle(dt, new Vector3(same.doubleValue(), same.doubleValue(), same.doubleValue()));
        return new AnimKey.Handle(dt, vector(dv, "handle value"));
    }

    private static AnimClip readV1(Map<?, ?> root) {
        AnimClip clip = new AnimClip();
        clip.format = 1;
        clip.loop = Boolean.TRUE.equals(root.get("looped")) ? AnimClip.Loop.LOOP : AnimClip.Loop.ONCE;
        clip.priority = root.get("priority") == null ? "0" : priority(root.get("priority"));
        Set<String> rotated = new LinkedHashSet<>();
        Set<String> moved = new LinkedHashSet<>();
        List<Map<?, ?>> frames = new ArrayList<>();
        for (Object entry : list(root.get("keyframes"))) {
            if (!(entry instanceof Map<?, ?> frame)) throw new IllegalArgumentException("each keyframe must be an object");
            frames.add(frame);
            if (!(frame.get("poses") instanceof Map<?, ?> poses)) continue;
            for (Map.Entry<?, ?> pose : poses.entrySet()) {
                if (!(pose.getValue() instanceof Map<?, ?> fields)) throw new IllegalArgumentException("pose " + pose.getKey() + " must be an object");
                if (fields.get("rotation") != null || fields.get("angles") != null) rotated.add(String.valueOf(pose.getKey()));
                if (fields.get("position") != null) moved.add(String.valueOf(pose.getKey()));
            }
        }
        frames.sort((a, b) -> Double.compare(number(a.get("time"), "keyframe time"), number(b.get("time"), "keyframe time")));
        for (Map<?, ?> frame : frames) {
            double time = number(frame.get("time"), "keyframe time");
            if (!(frame.get("poses") instanceof Map<?, ?> poses)) continue;
            for (Map.Entry<?, ?> pose : poses.entrySet()) {
                String joint = String.valueOf(pose.getKey());
                Map<?, ?> fields = (Map<?, ?>) pose.getValue();
                Object easing = fields.get("easing");
                Interp interp = easing == null || "linear".equals(String.valueOf(easing)) ? Interp.LINEAR : Interp.BEZIER;
                if (rotated.contains(joint)) {
                    List<AnimKey> before = clip.keys(joint, Channel.ROTATION);
                    Vector3 previous = before.isEmpty() ? Vector3.ZERO : before.getLast().value();
                    clip.put(joint, Channel.ROTATION, new AnimKey(time, v1Rotation(fields, previous), interp));
                }
                if (moved.contains(joint)) {
                    Vector3 position = fields.get("position") == null ? Vector3.ZERO : vector(fields.get("position"), "position");
                    clip.put(joint, Channel.POSITION, new AnimKey(time, position, interp));
                }
            }
        }
        readTimed(root, clip);
        clip.length = root.get("length") == null ? clip.lastKeyTime() : number(root.get("length"), "length");
        return clip;
    }

    private static Vector3 v1Rotation(Map<?, ?> fields, Vector3 previous) {
        if (fields.get("rotation") instanceof List<?> q && q.size() == 4) {
            Quat rotation = new Quat(number(q.get(0), "rotation"), number(q.get(1), "rotation"), number(q.get(2), "rotation"), number(q.get(3), "rotation"));
            return clean(Euler.nearest(rotation, previous));
        }
        if (fields.get("angles") != null) return vector(fields.get("angles"), "angles");
        return Vector3.ZERO;
    }

    private static Vector3 clean(Vector3 value) {
        return new Vector3(round(value.x()), round(value.y()), round(value.z()));
    }

    private static double round(double value) {
        return Math.round(value * 1e4) / 1e4;
    }

    public static String write(AnimClip clip) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", 2);
        root.put("length", clip.length);
        root.put("fps", clip.fps);
        root.put("loop", clip.loop.key());
        root.put("priority", isNumber(clip.priority) ? (Object) Double.parseDouble(clip.priority) : clip.priority);
        root.put("rig", clip.rig);
        root.put("space", clip.space.key());
        root.put("blend", clip.blend.key());
        root.put("mask", clip.mask);
        root.put("channels", channels(clip));
        root.put("markers", markers(clip));
        root.put("events", events(clip));
        if (clip.space == AnimClip.Space.VIEW) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("model", clip.view.model());
            root.put("viewModel", view);
        }
        return Json.write(plain(root));
    }

    private static Map<String, Object> channels(AnimClip clip) {
        Map<String, Object> joints = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Channel, List<AnimKey>>> joint : clip.channels.entrySet()) {
            if (joint.getValue().isEmpty()) continue;
            Map<String, Object> tracks = new LinkedHashMap<>();
            for (Channel channel : Channel.values()) {
                List<AnimKey> keys = joint.getValue().get(channel);
                if (keys == null || keys.isEmpty()) continue;
                List<Object> written = new ArrayList<>();
                for (AnimKey key : keys) written.add(key(key));
                tracks.put(channel.key(), written);
            }
            joints.put(joint.getKey(), tracks);
        }
        return joints;
    }

    private static List<Object> markers(AnimClip clip) {
        List<Object> out = new ArrayList<>();
        for (AnimClip.Marker marker : clip.markers) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("time", marker.time());
            fields.put("name", marker.name());
            fields.put("value", marker.value());
            out.add(fields);
        }
        return out;
    }

    private static List<Object> events(AnimClip clip) {
        List<Object> out = new ArrayList<>();
        for (AnimClip.Event event : clip.events) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("time", event.time());
            fields.put("name", event.name());
            fields.put("on", event.on().key());
            fields.put("payload", event.payload());
            if (!event.sound().isEmpty() || !event.particle().isEmpty() || event.preview()) {
                Map<String, Object> preview = new LinkedHashMap<>();
                if (!event.sound().isEmpty()) preview.put("sound", event.sound());
                if (!event.particle().isEmpty()) preview.put("particle", event.particle());
                if (!event.preview()) preview.put("enabled", false);
                fields.put("preview", preview);
            }
            out.add(fields);
        }
        return out;
    }

    private static List<Object> key(AnimKey key) {
        List<Object> out = new ArrayList<>(List.of(key.time(), vector(key.value()), key.interp().key()));
        if (key.in() != null || key.out() != null) {
            Map<String, Object> handles = new LinkedHashMap<>();
            if (key.in() != null) handles.put("in", handle(key.in()));
            if (key.out() != null) handles.put("out", handle(key.out()));
            out.add(handles);
        }
        return out;
    }

    private static List<Object> handle(AnimKey.Handle handle) {
        return List.of(handle.dt(), vector(handle.dv()));
    }

    private static List<Object> vector(Vector3 value) {
        return List.of(value.x(), value.y(), value.z());
    }

    private static Object plain(Object value) {
        return switch (value) {
            case null -> null;
            case Boolean b -> b;
            case Number n -> rounded(n.doubleValue());
            case String s -> s;
            case List<?> list -> {
                List<Object> out = new ArrayList<>();
                for (Object item : list) out.add(plain(item));
                yield out;
            }
            case Map<?, ?> map -> {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) out.put(String.valueOf(entry.getKey()), plain(entry.getValue()));
                yield out;
            }
            default -> String.valueOf(value);
        };
    }

    private static BigDecimal decimal(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP);
    }

    private static double rounded(double value) {
        return decimal(value).doubleValue();
    }

    static String number(double value) {
        BigDecimal rounded = decimal(value).stripTrailingZeros();
        String text = rounded.scale() <= 0 ? rounded.toBigInteger().toString() : rounded.toPlainString();
        return text.equals("-0") ? "0" : text;
    }

    private static String priority(Object value) {
        if (value instanceof Number number) return number(number.doubleValue());
        String text = String.valueOf(value);
        if (!AnimClip.PRIORITIES.contains(text) && !isNumber(text)) {
            throw new IllegalArgumentException("priority must be one of " + String.join(", ", AnimClip.PRIORITIES) + " or a number, got " + text);
        }
        return text;
    }

    static boolean isNumber(String text) {
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static List<?> list(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return list;
        throw new IllegalArgumentException("expected a list, got " + value);
    }

    private static double number(Object value, String what) {
        if (value instanceof Number number) return number.doubleValue();
        throw new IllegalArgumentException(what + " must be a number, got " + value);
    }

    private static Vector3 vector(Object value, String what) {
        if (value instanceof List<?> v && v.size() == 3) return new Vector3(number(v.get(0), what), number(v.get(1), what), number(v.get(2), what));
        throw new IllegalArgumentException(what + " must be three numbers");
    }
}
