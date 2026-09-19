package com.meekdev.moud.core.character;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.tween.Easing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Clip {

    public enum Loop { LOOP, ONCE, HOLD }

    public record Marker(double time, String name, Object value) {}

    public record Channels(ClipCurve rotation, ClipCurve position, ClipCurve scale, double weight) {}

    public record Link(String parent, Vector3 pivot, Vector3 rotation) {}

    public record Sample(Quat rotation, Vector3 position, Vector3 scale) {}

    public static final List<String> LEVELS = List.of("core", "idle", "movement", "action", "action2", "action3", "action4");

    public static final String EULER = "yxz";

    private static final int DEEPEST = 64;

    public static final Clip EMPTY = new Clip(0, Loop.ONCE, 0, Map.of(), List.of(), Set.of(), AnimationBlend.NORMAL,
            AnimationSpace.BODY, "", EULER, Map.of());

    private final double length;
    private final Loop loop;
    private final double priority;
    private final Map<String, Channels> joints;
    private final List<Marker> markers;
    private final Set<String> mask;
    private final AnimationBlend blend;
    private final AnimationSpace space;
    private final String rig;
    private final String euler;
    private final Map<String, Link> skeleton;
    private final Map<String, Quat> rests = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public Clip(double length, Loop loop, double priority, Map<String, Channels> joints, List<Marker> markers,
                Set<String> mask, AnimationBlend blend, AnimationSpace space, String rig, String euler, Map<String, Link> skeleton) {
        this.length = length;
        this.loop = loop;
        this.priority = priority;
        this.joints = joints;
        this.markers = markers;
        this.mask = mask;
        this.blend = blend;
        this.space = space;
        this.rig = rig;
        this.euler = euler;
        this.skeleton = skeleton;
        for (Map.Entry<String, Link> link : skeleton.entrySet()) {
            Vector3 r = link.getValue().rotation();
            if (!r.equals(Vector3.ZERO)) rests.put(link.getKey(), euler(r, euler));
        }
        for (String name : joints.keySet()) {
            String target = Retarget.joint(name);
            if (target != null) aliases.putIfAbsent(target, name);
        }
    }

    public double length() { return length; }
    public Loop loop() { return loop; }
    public boolean looped() { return loop == Loop.LOOP; }
    public double priority() { return priority; }
    public Map<String, Channels> joints() { return joints; }
    public List<Marker> markers() { return markers; }
    public Set<String> mask() { return mask; }
    public AnimationBlend blend() { return blend; }
    public AnimationSpace space() { return space; }
    public String rig() { return rig; }
    public String euler() { return euler; }
    public Map<String, Link> skeleton() { return skeleton; }

    public String channelFor(String joint, boolean retarget) {
        if (joints.containsKey(joint)) return joint;
        return retarget ? aliases.get(joint) : null;
    }

    public double weight(String joint) {
        Channels channels = joints.get(joint);
        return channels == null ? 0 : channels.weight();
    }

    public CFrame sample(String joint, double time) {
        Sample sample = pose(joint, time);
        if (sample == null) return null;
        return new CFrame(sample.position() == null ? Vector3.ZERO : sample.position(),
                sample.rotation() == null ? Quat.IDENTITY : sample.rotation());
    }

    public Sample pose(String joint, double time) {
        Channels channels = joints.get(joint);
        if (channels == null) return null;
        Quat rest = rests.get(joint);
        Quat rotation = null;
        if (channels.rotation() != null) {
            double[] v = channels.rotation().sample(time);
            if (v.length == 4) {
                rotation = new Quat(v[0], v[1], v[2], v[3]).normalize();
            } else {
                Vector3 angles = new Vector3(v[0], v[1], v[2]);
                Link link = skeleton.get(joint);
                if (rest != null) angles = angles.add(link.rotation());
                rotation = euler(angles, euler);
                if (rest != null) rotation = rest.inverse().mul(rotation).normalize();
            }
        }
        Vector3 position = null;
        if (channels.position() != null) {
            double[] v = channels.position().sample(time);
            position = new Vector3(v[0], v[1], v[2]);
            if (rest != null) position = rest.inverse().rotate(position);
        }
        Vector3 scale = null;
        if (channels.scale() != null) {
            double[] v = channels.scale().sample(time);
            scale = new Vector3(v[0], v[1], v[2]);
        }
        return new Sample(rotation, position, scale);
    }

    public Map<String, Sample> composed(double time) {
        Map<String, CFrame[]> frames = new HashMap<>();
        Map<String, Sample> out = new HashMap<>();
        for (String name : skeleton.keySet()) {
            CFrame[] both = frames(name, time, frames, 0);
            Quat delta = both[1].rotation().mul(both[0].rotation().inverse()).normalize();
            Sample own = pose(name, time);
            out.put(name, new Sample(delta, both[1].position().sub(both[0].position()), own == null ? null : own.scale()));
        }
        return out;
    }

    private CFrame[] frames(String name, double time, Map<String, CFrame[]> known, int depth) {
        CFrame[] done = known.get(name);
        if (done != null) return done;
        Link link = skeleton.get(name);
        CFrame[] parent = {CFrame.IDENTITY, CFrame.IDENTITY};
        Vector3 from = Vector3.ZERO;
        if (link.parent() != null && skeleton.containsKey(link.parent()) && depth < DEEPEST) {
            parent = frames(link.parent(), time, known, depth + 1);
            from = skeleton.get(link.parent()).pivot();
        }
        CFrame local = new CFrame(link.pivot().sub(from), rests.getOrDefault(name, Quat.IDENTITY));
        CFrame moved = local;
        Sample sample = pose(name, time);
        if (sample != null) {
            moved = local.mul(new CFrame(sample.position() == null ? Vector3.ZERO : sample.position(),
                    sample.rotation() == null ? Quat.IDENTITY : sample.rotation()));
        }
        CFrame[] both = {parent[0].mul(local), parent[1].mul(moved)};
        known.put(name, both);
        return both;
    }

    public static Quat euler(Vector3 degrees, String order) {
        Quat out = Quat.IDENTITY;
        for (int n = 0; n < order.length(); n++) {
            out = out.mul(switch (order.charAt(n)) {
                case 'x' -> Quat.axisAngle(Vector3.RIGHT, Math.toRadians(degrees.x()));
                case 'y' -> Quat.axisAngle(Vector3.UP, Math.toRadians(degrees.y()));
                default -> Quat.axisAngle(new Vector3(0, 0, 1), Math.toRadians(degrees.z()));
            });
        }
        return out.normalize();
    }

    public static Set<String> maskOf(String text) {
        Set<String> names = new LinkedHashSet<>();
        for (String one : text.split("[,\\s]+")) {
            if (!one.isEmpty()) names.add(one);
        }
        return names;
    }

    public static Clip of(KeyframeSequence sequence) {
        Map<String, List<ClipCurve.Key>> rotations = new LinkedHashMap<>();
        Map<String, List<ClipCurve.Key>> positions = new LinkedHashMap<>();
        Map<String, Double> weights = new HashMap<>();
        List<Marker> markers = new ArrayList<>();
        double length = 0;
        for (Instance child : sequence.children()) {
            if (!(child instanceof Keyframe frame)) continue;
            length = Math.max(length, frame.time);
            poses(frame, frame, rotations, positions, weights);
            for (Instance inside : frame.children()) {
                if (inside instanceof KeyframeMarker marker) markers.add(new Marker(frame.time, marker.name(), marker.value));
            }
        }
        return new Clip(length, sequence.looped ? Loop.LOOP : Loop.ONCE, sequence.priority, full(rotations, positions, weights),
                sorted(markers), maskOf(sequence.mask), sequence.blend, sequence.space, "", EULER, Map.of());
    }

    private static void poses(Keyframe frame, Instance under, Map<String, List<ClipCurve.Key>> rotations,
                              Map<String, List<ClipCurve.Key>> positions, Map<String, Double> weights) {
        for (Instance child : under.children()) {
            if (!(child instanceof KeyframePose pose)) continue;
            add(pose.name(), frame.time, pose.cframe, pose.easing, pose.direction, rotations, positions);
            weights.putIfAbsent(pose.name(), pose.weight);
            poses(frame, pose, rotations, positions, weights);
        }
    }

    private static void add(String joint, double time, CFrame at, Easing easing, Easing.Direction direction,
                            Map<String, List<ClipCurve.Key>> rotations, Map<String, List<ClipCurve.Key>> positions) {
        Quat q = at.rotation();
        Vector3 p = at.position();
        rotations.computeIfAbsent(joint, name -> new ArrayList<>()).add(new ClipCurve.Key(time,
                new double[] {q.x(), q.y(), q.z(), q.w()}, ClipCurve.Interp.EASED, easing, direction, null, null));
        positions.computeIfAbsent(joint, name -> new ArrayList<>()).add(new ClipCurve.Key(time,
                new double[] {p.x(), p.y(), p.z()}, ClipCurve.Interp.EASED, easing, direction, null, null));
    }

    private static Map<String, Channels> full(Map<String, List<ClipCurve.Key>> rotations,
                                              Map<String, List<ClipCurve.Key>> positions, Map<String, Double> weights) {
        Map<String, Channels> joints = new LinkedHashMap<>();
        for (Map.Entry<String, List<ClipCurve.Key>> entry : rotations.entrySet()) {
            String name = entry.getKey();
            joints.put(name, new Channels(new ClipCurve(entry.getValue()), new ClipCurve(positions.get(name)), null,
                    weights.getOrDefault(name, 1.0)));
        }
        return joints;
    }

    public static Clip parse(Map<String, Object> root) {
        return root.get("joints") instanceof Map<?, ?> ? parseChannels(root) : parseKeyframes(root);
    }

    private static Clip parseKeyframes(Map<String, Object> root) {
        Map<String, List<ClipCurve.Key>> rotations = new LinkedHashMap<>();
        Map<String, List<ClipCurve.Key>> positions = new LinkedHashMap<>();
        Map<String, Double> weights = new HashMap<>();
        double length = 0;
        for (Object entry : list(root.get("keyframes"))) {
            if (!(entry instanceof Map<?, ?> frame)) throw new IllegalArgumentException("each keyframe must be an object");
            double time = number(frame.get("time"), "keyframe time");
            length = Math.max(length, time);
            if (!(frame.get("poses") instanceof Map<?, ?> byJoint)) continue;
            for (Map.Entry<?, ?> one : byJoint.entrySet()) {
                if (!(one.getValue() instanceof Map<?, ?> pose)) throw new IllegalArgumentException("pose " + one.getKey() + " must be an object");
                String joint = String.valueOf(one.getKey());
                add(joint, time, cframe(pose), easing(pose.get("easing")), direction(pose.get("direction")), rotations, positions);
                weights.putIfAbsent(joint, pose.get("weight") == null ? 1 : number(pose.get("weight"), "weight"));
            }
        }
        List<Marker> markers = markers(root);
        if (root.get("length") != null) length = number(root.get("length"), "length");
        return new Clip(length, Boolean.TRUE.equals(root.get("looped")) ? Loop.LOOP : Loop.ONCE, priority(root.get("priority")),
                full(rotations, positions, weights), markers, maskOf(root.get("mask")), blend(root.get("blend")),
                space(root.get("space")), text(root.get("rig")), EULER, Map.of());
    }

    private static Clip parseChannels(Map<String, Object> root) {
        String euler = root.get("euler") == null ? EULER : String.valueOf(root.get("euler")).toLowerCase(Locale.ROOT);
        if (!euler.matches("[xyz]{3}") || euler.chars().distinct().count() != 3) {
            throw new IllegalArgumentException("euler must name each axis once, like yxz or zyx, got " + euler);
        }
        Map<String, Channels> joints = new LinkedHashMap<>();
        double length = 0;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) root.get("joints")).entrySet()) {
            String joint = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> channels)) throw new IllegalArgumentException("joint " + joint + " must be an object");
            ClipCurve rotation = curve(channels.get("rotation"), joint + " rotation", true);
            ClipCurve position = curve(channels.get("position"), joint + " position", false);
            ClipCurve scale = curve(channels.get("scale"), joint + " scale", false);
            for (ClipCurve curve : new ClipCurve[] {rotation, position, scale}) {
                if (curve != null) length = Math.max(length, curve.end());
            }
            double weight = channels.get("weight") == null ? 1 : number(channels.get("weight"), joint + " weight");
            joints.put(joint, new Channels(rotation, position, scale, weight));
        }
        List<Marker> markers = markers(root);
        for (Marker marker : markers) length = Math.max(length, marker.time());
        if (root.get("length") != null) length = number(root.get("length"), "length");
        return new Clip(length, loop(root), priority(root.get("priority")), joints, markers, maskOf(root.get("mask")),
                blend(root.get("blend")), space(root.get("space")), text(root.get("rig")), euler, skeleton(root.get("skeleton")));
    }

    private static ClipCurve curve(Object value, String what, boolean rotation) {
        if (value == null) return null;
        List<ClipCurve.Key> keys = new ArrayList<>();
        for (Object entry : list(value)) {
            if (!(entry instanceof List<?> key) || key.size() < 2) {
                throw new IllegalArgumentException(what + " keys must be [time, [x, y, z], interpolation?, handles?]");
            }
            double time = number(key.get(0), what + " time");
            double[] v = numbers(key.get(1), what);
            if (v.length != 3 && !(rotation && v.length == 4)) {
                throw new IllegalArgumentException(what + " values must be three numbers" + (rotation ? " or a quaternion" : ""));
            }
            ClipCurve.Interp interp = key.size() > 2 && key.get(2) != null ? interp(key.get(2), what) : ClipCurve.Interp.LINEAR;
            ClipCurve.Handle in = null;
            ClipCurve.Handle out = null;
            if (key.size() > 3 && key.get(3) instanceof Map<?, ?> handles) {
                in = handle(handles.get("in"), what);
                out = handle(handles.get("out"), what);
            }
            keys.add(new ClipCurve.Key(time, v, interp, Easing.LINEAR, Easing.Direction.IN_OUT, in, out));
        }
        return keys.isEmpty() ? null : new ClipCurve(keys);
    }

    private static ClipCurve.Handle handle(Object value, String what) {
        if (value == null) return null;
        if (!(value instanceof List<?> pair) || pair.size() != 2) {
            throw new IllegalArgumentException(what + " handles must be [dt, dv] or [dt, [dx, dy, dz]]");
        }
        double time = number(pair.get(0), what + " handle time");
        double[] v = pair.get(1) instanceof Number n ? new double[] {n.doubleValue()} : numbers(pair.get(1), what + " handle");
        return new ClipCurve.Handle(time, v);
    }

    private static ClipCurve.Interp interp(Object value, String what) {
        return switch (String.valueOf(value)) {
            case "linear" -> ClipCurve.Interp.LINEAR;
            case "catmullrom" -> ClipCurve.Interp.CATMULLROM;
            case "bezier" -> ClipCurve.Interp.BEZIER;
            case "step" -> ClipCurve.Interp.STEP;
            default -> throw new IllegalArgumentException(what + " interpolation must be linear, catmullrom, bezier or step, got " + value);
        };
    }

    private static Map<String, Link> skeleton(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> byName)) throw new IllegalArgumentException("skeleton must be an object");
        Map<String, Link> links = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : byName.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> link)) throw new IllegalArgumentException("skeleton " + entry.getKey() + " must be an object");
            links.put(String.valueOf(entry.getKey()), new Link(link.get("parent") == null ? null : String.valueOf(link.get("parent")),
                    link.get("pivot") == null ? Vector3.ZERO : vector(link.get("pivot"), "pivot"),
                    link.get("rotation") == null ? Vector3.ZERO : vector(link.get("rotation"), "rotation")));
        }
        return links;
    }

    private static Loop loop(Map<String, Object> root) {
        Object value = root.get("loop");
        if (value == null) return Boolean.TRUE.equals(root.get("looped")) ? Loop.LOOP : Loop.ONCE;
        return switch (String.valueOf(value)) {
            case "loop" -> Loop.LOOP;
            case "once" -> Loop.ONCE;
            case "hold" -> Loop.HOLD;
            default -> throw new IllegalArgumentException("loop must be loop, once or hold, got " + value);
        };
    }

    public static double priority(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.doubleValue();
        int level = LEVELS.indexOf(String.valueOf(value));
        if (level < 0) throw new IllegalArgumentException("priority must be a number or one of " + String.join(", ", LEVELS) + ", got " + value);
        return level;
    }

    private static AnimationBlend blend(Object value) {
        if (value == null || "normal".equals(value)) return AnimationBlend.NORMAL;
        if ("additive".equals(value)) return AnimationBlend.ADDITIVE;
        throw new IllegalArgumentException("blend must be normal or additive, got " + value);
    }

    private static AnimationSpace space(Object value) {
        if (value == null || "body".equals(value)) return AnimationSpace.BODY;
        if ("view".equals(value)) return AnimationSpace.VIEW;
        throw new IllegalArgumentException("space must be body or view, got " + value);
    }

    private static Set<String> maskOf(Object value) {
        if (value == null) return Set.of();
        if (value instanceof String text) return maskOf(text);
        Set<String> names = new LinkedHashSet<>();
        for (Object name : list(value)) names.add(String.valueOf(name));
        return names;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static List<Marker> markers(Map<String, Object> root) {
        List<Marker> markers = new ArrayList<>();
        for (Object entry : list(root.get("markers"))) {
            if (!(entry instanceof Map<?, ?> marker)) throw new IllegalArgumentException("each marker must be an object");
            markers.add(new Marker(number(marker.get("time"), "marker time"), String.valueOf(marker.get("name")),
                    marker.get("value") == null ? "" : String.valueOf(marker.get("value"))));
        }
        for (Object entry : list(root.get("events"))) {
            if (!(entry instanceof Map<?, ?> event)) throw new IllegalArgumentException("each event must be an object");
            markers.add(new Marker(number(event.get("time"), "event time"), String.valueOf(event.get("name")), event.get("payload")));
        }
        return sorted(markers);
    }

    private static List<Marker> sorted(List<Marker> markers) {
        markers.sort(Comparator.comparingDouble(Marker::time));
        return markers;
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

    private static double[] numbers(Object value, String what) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(what + " must be a list of numbers, got " + value);
        double[] out = new double[list.size()];
        for (int n = 0; n < out.length; n++) out[n] = number(list.get(n), what);
        return out;
    }

    private static CFrame cframe(Map<?, ?> pose) {
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
