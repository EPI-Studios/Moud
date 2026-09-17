package com.meekdev.moud.core.character;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.tween.Easing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record Clip(double length, boolean looped, double priority, Map<String, List<Key>> joints, List<Marker> markers) {

    public record Key(double time, CFrame cframe, Easing easing, Easing.Direction direction, double weight) {}

    public record Marker(double time, String name, String value) {}

    public static final Clip EMPTY = new Clip(0, false, 0, Map.of(), List.of());

    public CFrame sample(String joint, double time) {
        List<Key> keys = joints.get(joint);
        if (keys == null || keys.isEmpty()) return null;
        if (time <= keys.getFirst().time()) return keys.getFirst().cframe();
        for (int n = 1; n < keys.size(); n++) {
            Key next = keys.get(n);
            if (time > next.time()) continue;
            Key previous = keys.get(n - 1);
            double span = next.time() - previous.time();
            double alpha = span <= 0 ? 1 : (time - previous.time()) / span;
            return previous.cframe().lerp(next.cframe(), previous.easing().apply(alpha, previous.direction()));
        }
        return keys.getLast().cframe();
    }

    public double weight(String joint) {
        List<Key> keys = joints.get(joint);
        return keys == null || keys.isEmpty() ? 0 : keys.getFirst().weight();
    }

    public static Clip of(KeyframeSequence sequence) {
        Map<String, List<Key>> joints = new LinkedHashMap<>();
        List<Marker> markers = new ArrayList<>();
        double length = 0;
        for (Instance child : sequence.children()) {
            if (!(child instanceof Keyframe frame)) continue;
            length = Math.max(length, frame.time);
            poses(frame, frame, joints);
            for (Instance inside : frame.children()) {
                if (inside instanceof KeyframeMarker marker) markers.add(new Marker(frame.time, marker.name(), marker.value));
            }
        }
        return build(length, sequence.looped, sequence.priority, joints, markers);
    }

    private static void poses(Keyframe frame, Instance under, Map<String, List<Key>> joints) {
        for (Instance child : under.children()) {
            if (!(child instanceof KeyframePose pose)) continue;
            joints.computeIfAbsent(pose.name(), name -> new ArrayList<>())
                    .add(new Key(frame.time, pose.cframe, pose.easing, pose.direction, pose.weight));
            poses(frame, pose, joints);
        }
    }

    @SuppressWarnings("unchecked")
    public static Clip parse(Map<String, Object> root) {
        Map<String, List<Key>> joints = new LinkedHashMap<>();
        List<Marker> markers = new ArrayList<>();
        double length = 0;
        for (Object entry : list(root.get("keyframes"))) {
            if (!(entry instanceof Map<?, ?> frame)) throw new IllegalArgumentException("each keyframe must be an object");
            double time = number(frame.get("time"), "keyframe time");
            length = Math.max(length, time);
            Object poses = frame.get("poses");
            if (poses instanceof Map<?, ?> byJoint) {
                for (Map.Entry<?, ?> one : byJoint.entrySet()) {
                    if (!(one.getValue() instanceof Map<?, ?> pose)) throw new IllegalArgumentException("pose " + one.getKey() + " must be an object");
                    joints.computeIfAbsent(String.valueOf(one.getKey()), name -> new ArrayList<>())
                            .add(new Key(time, cframe((Map<String, Object>) pose), easing(pose.get("easing")), direction(pose.get("direction")),
                                    pose.get("weight") == null ? 1 : number(pose.get("weight"), "weight")));
                }
            }
        }
        for (Object entry : list(root.get("markers"))) {
            if (!(entry instanceof Map<?, ?> marker)) throw new IllegalArgumentException("each marker must be an object");
            markers.add(new Marker(number(marker.get("time"), "marker time"), String.valueOf(marker.get("name")),
                    marker.get("value") == null ? "" : String.valueOf(marker.get("value"))));
        }
        if (root.get("length") != null) length = number(root.get("length"), "length");
        return build(length, Boolean.TRUE.equals(root.get("looped")), root.get("priority") == null ? 0 : number(root.get("priority"), "priority"), joints, markers);
    }

    private static Clip build(double length, boolean looped, double priority, Map<String, List<Key>> joints, List<Marker> markers) {
        for (List<Key> keys : joints.values()) keys.sort(Comparator.comparingDouble(Key::time));
        markers.sort(Comparator.comparingDouble(Marker::time));
        return new Clip(length, looped, priority, joints, markers);
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

    private static CFrame cframe(Map<String, Object> pose) {
        Vector3 position = pose.get("position") == null ? Vector3.ZERO : vector(pose.get("position"), "position");
        Quat rotation = Quat.IDENTITY;
        if (pose.get("rotation") instanceof List<?> q && q.size() == 4) {
            rotation = new Quat(number(q.get(0), "rotation"), number(q.get(1), "rotation"), number(q.get(2), "rotation"), number(q.get(3), "rotation"));
        } else if (pose.get("angles") != null) {
            Vector3 degrees = vector(pose.get("angles"), "angles");
            rotation = Quat.euler(Math.toRadians(degrees.x()), Math.toRadians(degrees.y()), Math.toRadians(degrees.z()));
        }
        return new CFrame(position, rotation);
    }

    private static Vector3 vector(Object value, String what) {
        if (value instanceof List<?> v && v.size() == 3) return new Vector3(number(v.get(0), what), number(v.get(1), what), number(v.get(2), what));
        throw new IllegalArgumentException(what + " must be three numbers");
    }

    private static Easing easing(Object value) {
        if (value == null) return Easing.LINEAR;
        return Easing.valueOf(String.valueOf(value).toUpperCase(Locale.ROOT));
    }

    private static Easing.Direction direction(Object value) {
        if (value == null) return Easing.Direction.IN_OUT;
        return switch (String.valueOf(value)) {
            case "in" -> Easing.Direction.IN;
            case "out" -> Easing.Direction.OUT;
            case "inOut" -> Easing.Direction.IN_OUT;
            default -> throw new IllegalArgumentException("direction must be in, out or inOut, got " + value);
        };
    }
}
