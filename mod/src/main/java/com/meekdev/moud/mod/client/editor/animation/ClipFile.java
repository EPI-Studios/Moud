package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.AnimationBlend;
import com.meekdev.moud.core.character.AnimationSpace;
import com.meekdev.moud.core.character.Clip;
import com.meekdev.moud.core.character.ClipCurve;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.core.tween.Easing;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ClipFile {

    private static final int DECIMALS = 5;

    private ClipFile() {}

    public static AnimClip read(String text) {
        Map<String, Object> root = root(text);
        boolean v1 = v1(root);
        if (root.get("joints") == null && root.get("channels") != null) root.put("joints", root.remove("channels"));
        Clip parsed = Clip.parse(root);
        AnimClip clip = new AnimClip();
        clip.length = parsed.length();
        if (root.get("fps") != null) clip.fps = Math.max(1, (int) Math.round(number(root.get("fps"), "fps")));
        clip.loop = switch (parsed.loop()) {
            case LOOP -> AnimClip.Loop.LOOP;
            case ONCE -> AnimClip.Loop.ONCE;
            case HOLD -> AnimClip.Loop.HOLD;
        };
        clip.priority = priority(root.get("priority"));
        clip.rig = root.get("rig") == null ? "" : String.valueOf(root.get("rig"));
        clip.space = parsed.space() == AnimationSpace.VIEW ? AnimClip.Space.VIEW : AnimClip.Space.BODY;
        clip.blend = parsed.blend() == AnimationBlend.ADDITIVE ? AnimClip.Blend.ADDITIVE : AnimClip.Blend.NORMAL;
        clip.mask.addAll(parsed.mask());
        clip.euler = parsed.euler();
        clip.skeleton.putAll(parsed.skeleton());
        clip.retarget.putAll(parsed.retarget());
        Set<String> moved = v1 ? moved(root) : null;
        for (Map.Entry<String, Clip.Channels> joint : parsed.joints().entrySet()) {
            String name = joint.getKey();
            Clip.Channels channels = joint.getValue();
            if (channels.rotation() != null) keys(clip, parsed, name, Channel.ROTATION, channels.rotation());
            if (channels.position() != null && (moved == null || moved.contains(name))) keys(clip, parsed, name, Channel.POSITION, channels.position());
            if (channels.scale() != null) keys(clip, parsed, name, Channel.SCALE, channels.scale());
            if (channels.weight() != 1) clip.weights.put(name, channels.weight());
        }
        readTimed(root, clip);
        if (root.get("viewModel") instanceof Map<?, ?> view) {
            clip.view = new AnimClip.ViewModel(view.get("model") == null ? "" : String.valueOf(view.get("model")));
        }
        return clip;
    }

    public static @Nullable String older(String text) {
        Map<String, Object> root = root(text);
        if (v1(root)) return "format 1";
        if (root.get("channels") != null && root.get("joints") == null) return "an early editor format";
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root(String text) {
        Object parsed = Json.parse(text);
        if (!(parsed instanceof Map<?, ?> root)) throw new IllegalArgumentException("an animation file holds a JSON object");
        return new LinkedHashMap<>((Map<String, Object>) root);
    }

    private static boolean v1(Map<String, Object> root) {
        return root.get("keyframes") != null && root.get("joints") == null && root.get("channels") == null;
    }

    private static Set<String> moved(Map<String, Object> root) {
        Set<String> moved = new LinkedHashSet<>();
        for (Object entry : list(root.get("keyframes"))) {
            if (!(entry instanceof Map<?, ?> frame) || !(frame.get("poses") instanceof Map<?, ?> poses)) continue;
            for (Map.Entry<?, ?> pose : poses.entrySet()) {
                if (pose.getValue() instanceof Map<?, ?> fields && fields.get("position") != null) moved.add(String.valueOf(pose.getKey()));
            }
        }
        return moved;
    }

    private static void keys(AnimClip clip, Clip parsed, String joint, Channel channel, ClipCurve curve) {
        Vector3 previous = Vector3.ZERO;
        for (ClipCurve.Key key : curve.keys()) {
            double[] v = key.value();
            Vector3 value = v.length == 4
                    ? clean(parsed.angles(joint, new Quat(v[0], v[1], v[2], v[3]), previous))
                    : new Vector3(v[0], v[1], v[2]);
            previous = value;
            Interp interp = switch (key.interp()) {
                case LINEAR -> Interp.LINEAR;
                case CATMULLROM -> Interp.SMOOTH;
                case BEZIER -> Interp.BEZIER;
                case STEP -> Interp.STEP;
                case EASED -> key.easing() == Easing.LINEAR ? Interp.LINEAR : Interp.BEZIER;
            };
            clip.track(joint, channel).add(new AnimKey(key.time(), value, interp, handle(key.in()), handle(key.out())));
        }
    }

    private static AnimKey.@Nullable Handle handle(ClipCurve.@Nullable Handle handle) {
        if (handle == null) return null;
        double[] v = handle.value();
        if (v.length == 0) return new AnimKey.Handle(handle.time(), Vector3.ZERO);
        if (v.length < 3) return new AnimKey.Handle(handle.time(), new Vector3(v[0], v[0], v[0]));
        return new AnimKey.Handle(handle.time(), new Vector3(v[0], v[1], v[2]));
    }

    private static void readTimed(Map<String, Object> root, AnimClip clip) {
        for (Object entry : list(root.get("markers"))) {
            if (!(entry instanceof Map<?, ?> marker)) throw new IllegalArgumentException("each marker must be an object");
            clip.markers.add(new AnimClip.Marker(number(marker.get("time"), "marker time"), String.valueOf(marker.get("name")),
                    marker.get("value") == null ? "" : String.valueOf(marker.get("value"))));
        }
        for (Object entry : list(root.get("events"))) {
            if (!(entry instanceof Map<?, ?> event)) throw new IllegalArgumentException("each event must be an object");
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
                    event.get("payload"), sound, particle, preview));
        }
        clip.sortTimed();
    }

    private static Vector3 clean(Vector3 value) {
        return new Vector3(round(value.x()), round(value.y()), round(value.z()));
    }

    private static double round(double value) {
        return Math.round(value * 1e4) / 1e4;
    }

    public static Clip compile(AnimClip clip) {
        Map<String, Clip.Channels> joints = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Channel, List<AnimKey>>> joint : clip.channels.entrySet()) {
            Map<Channel, List<AnimKey>> tracks = joint.getValue();
            ClipCurve rotation = Curves.curve(tracks.getOrDefault(Channel.ROTATION, List.of()));
            ClipCurve position = Curves.curve(tracks.getOrDefault(Channel.POSITION, List.of()));
            ClipCurve scale = Curves.curve(tracks.getOrDefault(Channel.SCALE, List.of()));
            if (rotation == null && position == null && scale == null) continue;
            joints.put(joint.getKey(), new Clip.Channels(rotation, position, scale, clip.weights.getOrDefault(joint.getKey(), 1.0)));
        }
        List<Clip.Marker> markers = new ArrayList<>();
        for (AnimClip.Marker marker : clip.markers) markers.add(new Clip.Marker(marker.time(), marker.name(), marker.value()));
        for (AnimClip.Event event : clip.events) markers.add(new Clip.Marker(event.time(), event.name(), event.payload(), event.on().runtime()));
        markers.sort(Comparator.comparingDouble(Clip.Marker::time));
        return new Clip(clip.length, switch (clip.loop) {
            case LOOP -> Clip.Loop.LOOP;
            case ONCE -> Clip.Loop.ONCE;
            case HOLD -> Clip.Loop.HOLD;
        }, Clip.priority(isNumber(clip.priority) ? (Object) Double.parseDouble(clip.priority) : clip.priority), joints, markers,
                new LinkedHashSet<>(clip.mask), clip.blend == AnimClip.Blend.ADDITIVE ? AnimationBlend.ADDITIVE : AnimationBlend.NORMAL,
                clip.space == AnimClip.Space.VIEW ? AnimationSpace.VIEW : AnimationSpace.BODY, clip.rig, clip.euler,
                new LinkedHashMap<>(clip.skeleton), new LinkedHashMap<>(clip.retarget));
    }

    public static String write(AnimClip clip) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);
        root.put("length", clip.length);
        root.put("fps", clip.fps);
        root.put("loop", clip.loop.key());
        root.put("priority", isNumber(clip.priority) ? (Object) Double.parseDouble(clip.priority) : clip.priority);
        root.put("euler", clip.euler);
        if (!clip.rig.isEmpty()) root.put("rig", clip.rig);
        root.put("space", clip.space.key());
        root.put("blend", clip.blend.key());
        root.put("mask", clip.mask);
        root.put("joints", joints(clip));
        root.put("markers", markers(clip));
        root.put("events", events(clip));
        if (clip.space == AnimClip.Space.VIEW) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("model", clip.view.model());
            root.put("viewModel", view);
        }
        if (!clip.retarget.isEmpty()) root.put("retarget", clip.retarget);
        if (!clip.skeleton.isEmpty()) root.put("skeleton", skeleton(clip.skeleton));
        return Json.write(plain(root));
    }

    private static Map<String, Object> joints(AnimClip clip) {
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
            Double weight = clip.weights.get(joint.getKey());
            if (weight != null && weight != 1) tracks.put("weight", weight);
            joints.put(joint.getKey(), tracks);
        }
        return joints;
    }

    private static Map<String, Object> skeleton(Map<String, Clip.Link> skeleton) {
        Map<String, Object> links = new LinkedHashMap<>();
        for (Map.Entry<String, Clip.Link> entry : skeleton.entrySet()) {
            Map<String, Object> link = new LinkedHashMap<>();
            if (entry.getValue().parent() != null) link.put("parent", entry.getValue().parent());
            link.put("pivot", vector(entry.getValue().pivot()));
            if (!entry.getValue().rotation().equals(Vector3.ZERO)) link.put("rotation", vector(entry.getValue().rotation()));
            links.put(entry.getKey(), link);
        }
        return links;
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
        if (value == null) return Clip.LEVELS.getFirst();
        if (value instanceof Number number) return number(number.doubleValue());
        return String.valueOf(value);
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
}
