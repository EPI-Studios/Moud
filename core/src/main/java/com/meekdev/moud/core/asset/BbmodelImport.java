package com.meekdev.moud.core.asset;

import com.meekdev.moud.core.character.AnimationController;
import com.meekdev.moud.core.character.Clip;
import com.meekdev.moud.core.character.Retarget;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Bone;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Model;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.core.scene.Json;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BbmodelImport {

    public static final String EULER = "zyx";

    private static final double PX = 1.0 / 16.0;
    private static final double SMALLEST = 0.05;
    private static final double HANDLE = 0.1;
    private static final int PLAYER_LIKE = 3;

    public record Group(String uuid, String name, String parent, Vector3 origin, Vector3 rotation) {}

    public record Rig(List<Group> groups, Vector3 min, Vector3 max, Map<String, Map<String, Object>> clips,
                      boolean player, List<String> notes) {

        public Vector3 center() {
            return min.add(max).mul(0.5);
        }

        public Vector3 size() {
            Vector3 span = max.sub(min);
            return new Vector3(Math.max(SMALLEST, span.x()), Math.max(SMALLEST, span.y()), Math.max(SMALLEST, span.z()));
        }

        public Group group(String name) {
            for (Group group : groups) {
                if (group.name().equals(name)) return group;
            }
            return null;
        }
    }

    public record Imported(Model model, Map<String, String> files, List<String> notes) {}

    private BbmodelImport() {}

    public static Imported build(String text, String res, Instance parent) {
        String path = Res.parse(res);
        String base = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
        String folder = res.substring(0, res.lastIndexOf('.'));
        Rig rig = read(text, base);

        Model model = Instances.create(Classes.MODEL, parent, base);
        Vector3 center = rig.center();
        MeshPart mesh = Instances.create(Classes.MESH_PART, model, "mesh", part -> {
            part.meshId = res;
            part.size = rig.size();
            part.cframe = CFrame.at(center);
            part.anchored = true;
        });
        Instances.setObj(model, Classes.MODEL.property("primaryPart"), mesh);

        Map<String, Instance> bones = new HashMap<>();
        for (Group group : rig.groups()) {
            Group above = group.parent() == null ? null : byUuid(rig, group.parent());
            Instance holder = above == null ? mesh : bones.get(above.uuid());
            Vector3 from = above == null ? center : above.origin().mul(PX);
            Bone bone = Instances.create(Classes.BONE, holder, group.name(),
                    made -> made.cframe = new CFrame(group.origin().mul(PX).sub(from), Clip.euler(group.rotation(), EULER)));
            bones.put(group.uuid(), bone);
        }

        AnimationController controller = Instances.create(Classes.ANIMATION_CONTROLLER, model, "animationController");
        Instances.create(Classes.ANIMATOR, controller, "animator");

        Map<String, String> files = new LinkedHashMap<>();
        if (!rig.clips().isEmpty()) {
            Instance animations = Instances.create(Classes.FOLDER, model, "animations");
            for (Map.Entry<String, Map<String, Object>> clip : rig.clips().entrySet()) {
                String file = folder + "/" + clip.getKey() + ".anim";
                files.put(file, write(clip.getValue()));
                Instances.create(Classes.ANIMATION, animations, clip.getKey(), animation -> animation.animationId = file);
            }
        }
        return new Imported(model, files, rig.notes());
    }

    private static Group byUuid(Rig rig, String uuid) {
        for (Group group : rig.groups()) {
            if (group.uuid().equals(uuid)) return group;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static String modernize(String text) {
        if (!(Json.parse(text) instanceof Map<?, ?> parsed)) return text;
        Map<String, Object> root = (Map<String, Object>) parsed;
        if (!list(root.get("groups")).isEmpty()) return text;
        List<Object> groups = new ArrayList<>();
        lift(list(root.get("outliner")), groups);
        if (groups.isEmpty()) return text;
        root.put("groups", groups);
        return Json.write(root);
    }

    private static void lift(List<?> entries, List<Object> groups) {
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> node) || node.get("uuid") == null) continue;
            Map<String, Object> group = new LinkedHashMap<>();
            for (Map.Entry<?, ?> field : node.entrySet()) {
                if (!"children".equals(field.getKey())) group.put(String.valueOf(field.getKey()), field.getValue());
            }
            groups.add(group);
            lift(list(node.get("children")), groups);
        }
    }

    public static Rig read(String text, String name) {
        if (!(Json.parse(text) instanceof Map<?, ?> root)) throw new IllegalArgumentException("a .bbmodel holds a JSON object");
        List<String> notes = new ArrayList<>();

        Map<String, Map<?, ?>> groupData = new HashMap<>();
        for (Object entry : list(root.get("groups"))) {
            if (entry instanceof Map<?, ?> group && group.get("uuid") != null) groupData.put(String.valueOf(group.get("uuid")), group);
        }
        List<Group> groups = new ArrayList<>();
        Map<String, String> owners = new HashMap<>();
        Set<String> taken = new HashSet<>();
        outline(list(root.get("outliner")), null, groupData, groups, owners, taken, notes);

        Map<String, Group> byUuid = new HashMap<>();
        for (Group group : groups) byUuid.put(group.uuid(), group);

        double[] bounds = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Object entry : list(root.get("elements"))) {
            if (!(entry instanceof Map<?, ?> element)) continue;
            String type = element.get("type") == null ? "cube" : String.valueOf(element.get("type"));
            String owner = owners.get(String.valueOf(element.get("uuid")));
            for (Vector3 point : points(element, type)) grow(bounds, place(point, owner, byUuid));
        }
        if (bounds[0] > bounds[3]) {
            notes.add(name + " has no cubes or meshes to draw");
            bounds = new double[] {0, 0, 0, 0, 0, 0};
        }
        Vector3 min = new Vector3(bounds[0], bounds[1], bounds[2]).mul(PX);
        Vector3 max = new Vector3(bounds[3], bounds[4], bounds[5]).mul(PX);

        int matched = 0;
        Set<String> seen = new HashSet<>();
        for (Group group : groups) {
            String joint = Retarget.joint(group.name());
            if (joint != null && seen.add(joint)) matched++;
        }
        boolean player = matched >= PLAYER_LIKE;

        for (Object entry : list(root.get("textures"))) {
            if (entry instanceof Map<?, ?> texture && (texture.get("source") == null || !String.valueOf(texture.get("source")).startsWith("data:"))) {
                notes.add("texture " + texture.get("name") + " is not saved inside the .bbmodel, so it draws blank");
            }
        }

        Map<String, Map<String, Object>> clips = new LinkedHashMap<>();
        Set<String> clipNames = new HashSet<>();
        for (Object entry : list(root.get("animations"))) {
            if (!(entry instanceof Map<?, ?> animation)) continue;
            String clipName = unique(safe(String.valueOf(animation.get("name") == null ? "animation" : animation.get("name"))), clipNames);
            clips.put(clipName, clip(animation, clipName, groups, byUuid, player ? "player" : name, legacy(root), notes));
        }
        return new Rig(List.copyOf(groups), min, max, clips, player, notes);
    }

    private static void outline(List<?> entries, String parent, Map<String, Map<?, ?>> groupData, List<Group> groups,
                                Map<String, String> owners, Set<String> taken, List<String> notes) {
        for (Object entry : entries) {
            if (entry instanceof String uuid) {
                if (parent != null) owners.put(uuid, parent);
                continue;
            }
            if (!(entry instanceof Map<?, ?> node) || node.get("uuid") == null) continue;
            String uuid = String.valueOf(node.get("uuid"));
            Map<?, ?> data = groupData.getOrDefault(uuid, node);
            String wanted = data.get("name") == null ? "bone" : String.valueOf(data.get("name"));
            String unique = unique(wanted, taken);
            if (!unique.equals(wanted)) notes.add("there are two groups named " + wanted + ", the second is called " + unique);
            groups.add(new Group(uuid, unique, parent, vector(data.get("origin"), Vector3.ZERO), vector(data.get("rotation"), Vector3.ZERO)));
            outline(list(node.get("children")), uuid, groupData, groups, owners, taken, notes);
        }
    }

    private static List<Vector3> points(Map<?, ?> element, String type) {
        List<Vector3> points = new ArrayList<>();
        Vector3 origin = vector(element.get("origin"), Vector3.ZERO);
        Quat turn = Clip.euler(vector(element.get("rotation"), Vector3.ZERO), "xyz");
        if (type.equals("cube")) {
            double grow = element.get("inflate") instanceof Number n ? n.doubleValue() : 0;
            Vector3 from = vector(element.get("from"), Vector3.ZERO).sub(new Vector3(grow, grow, grow));
            Vector3 to = vector(element.get("to"), Vector3.ZERO).add(new Vector3(grow, grow, grow));
            for (int corner = 0; corner < 8; corner++) {
                Vector3 p = new Vector3((corner & 1) == 0 ? from.x() : to.x(), (corner & 2) == 0 ? from.y() : to.y(),
                        (corner & 4) == 0 ? from.z() : to.z());
                points.add(origin.add(turn.rotate(p.sub(origin))));
            }
        } else if (type.equals("mesh") && element.get("vertices") instanceof Map<?, ?> vertices) {
            for (Object vertex : vertices.values()) points.add(origin.add(turn.rotate(vector(vertex, Vector3.ZERO))));
        }
        return points;
    }

    private static Vector3 place(Vector3 point, String owner, Map<String, Group> byUuid) {
        int guard = 0;
        for (Group group = owner == null ? null : byUuid.get(owner); group != null && guard++ < 256;
             group = group.parent() == null ? null : byUuid.get(group.parent())) {
            Quat turn = Clip.euler(group.rotation(), "xyz");
            point = group.origin().add(turn.rotate(point.sub(group.origin())));
        }
        return point;
    }

    private static void grow(double[] bounds, Vector3 p) {
        bounds[0] = Math.min(bounds[0], p.x());
        bounds[1] = Math.min(bounds[1], p.y());
        bounds[2] = Math.min(bounds[2], p.z());
        bounds[3] = Math.max(bounds[3], p.x());
        bounds[4] = Math.max(bounds[4], p.y());
        bounds[5] = Math.max(bounds[5], p.z());
    }

    private record Frame(double time, List<double[]> points, String interp, Map<?, ?> source) {}

    static boolean legacy(Map<?, ?> root) {
        if (!(root.get("meta") instanceof Map<?, ?> meta) || meta.get("format_version") == null) return true;
        String version = String.valueOf(meta.get("format_version")).strip();
        int dot = version.indexOf('.');
        try {
            return Integer.parseInt(dot < 0 ? version : version.substring(0, dot)) < 5;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static Map<String, Object> clip(Map<?, ?> animation, String clipName, List<Group> groups, Map<String, Group> byUuid,
                                            String rigName, boolean legacy, List<String> notes) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", 2.0);
        double length = animation.get("length") instanceof Number n ? n.doubleValue() : 0;
        String loop = animation.get("loop") == null ? "once" : String.valueOf(animation.get("loop"));
        if (!loop.equals("loop") && !loop.equals("hold")) loop = "once";
        out.put("length", length);
        out.put("loop", loop);
        out.put("euler", EULER);
        out.put("rig", rigName);
        for (String unused : List.of("anim_time_update", "start_delay", "loop_delay", "blend_weight")) {
            Object value = animation.get(unused);
            if (value != null && !String.valueOf(value).isBlank() && !"0".equals(String.valueOf(value))) {
                notes.add(clipName + ": " + unused + " (" + value + ") is not carried over");
            }
        }

        Map<String, Object> joints = new LinkedHashMap<>();
        List<Object> events = new ArrayList<>();
        if (animation.get("animators") instanceof Map<?, ?> animators) {
            for (Map.Entry<?, ?> entry : animators.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> animator)) continue;
                String key = String.valueOf(entry.getKey());
                String type = animator.get("type") == null ? "bone" : String.valueOf(animator.get("type"));
                if (key.equals("effects") || type.equals("effect")) {
                    effects(animator, events);
                    continue;
                }
                Group group = byUuid.get(key);
                if (group == null && animator.get("name") != null) group = named(groups, String.valueOf(animator.get("name")));
                if (!type.equals("bone") || group == null) {
                    if (!list(animator.get("keyframes")).isEmpty()) {
                        notes.add(clipName + ": " + animator.get("name") + " (" + type + ") is not a group, its keyframes are left out");
                    }
                    continue;
                }
                Map<String, Object> channels = channels(animator, clipName + ": " + group.name(), legacy, notes);
                if (!channels.isEmpty()) joints.put(group.name(), channels);
            }
        }
        out.put("joints", joints);
        if (!events.isEmpty()) {
            events.sort(Comparator.comparingDouble(event -> ((Number) ((Map<?, ?>) event).get("time")).doubleValue()));
            out.put("events", events);
        }
        Map<String, Object> skeleton = new LinkedHashMap<>();
        for (Group group : groups) {
            Map<String, Object> link = new LinkedHashMap<>();
            Group above = group.parent() == null ? null : byUuid.get(group.parent());
            if (above != null) link.put("parent", above.name());
            link.put("pivot", numbers(group.origin().mul(PX)));
            if (!group.rotation().equals(Vector3.ZERO)) link.put("rotation", numbers(group.rotation()));
            skeleton.put(group.name(), link);
        }
        out.put("skeleton", skeleton);
        return out;
    }

    private static Group named(List<Group> groups, String name) {
        for (Group group : groups) {
            if (group.name().equals(name)) return group;
        }
        return null;
    }

    private static Map<String, Object> channels(Map<?, ?> animator, String where, boolean legacy, List<String> notes) {
        Map<String, List<Frame>> byChannel = new LinkedHashMap<>();
        for (Object entry : list(animator.get("keyframes"))) {
            if (!(entry instanceof Map<?, ?> keyframe) || !(keyframe.get("time") instanceof Number time)) continue;
            String channel = String.valueOf(keyframe.get("channel"));
            if (!channel.equals("rotation") && !channel.equals("position") && !channel.equals("scale")) continue;
            double missing = channel.equals("scale") ? 1 : 0;
            List<double[]> points = new ArrayList<>();
            for (Object point : list(keyframe.get("data_points"))) {
                if (point instanceof Map<?, ?> data) {
                    String at = where + " " + channel + " at " + time.doubleValue() + "s";
                    points.add(new double[] {value(data.get("x"), missing, at, notes), value(data.get("y"), missing, at, notes),
                            value(data.get("z"), missing, at, notes)});
                }
            }
            if (points.isEmpty()) points.add(new double[] {missing, missing, missing});
            String interp = keyframe.get("interpolation") == null ? "linear" : String.valueOf(keyframe.get("interpolation"));
            byChannel.computeIfAbsent(channel, c -> new ArrayList<>()).add(new Frame(time.doubleValue(), points, interp, keyframe));
        }
        Map<String, Object> channels = new LinkedHashMap<>();
        for (Map.Entry<String, List<Frame>> entry : byChannel.entrySet()) {
            List<Frame> frames = entry.getValue();
            frames.sort(Comparator.comparingDouble(Frame::time));
            String channel = entry.getKey();
            boolean bezier = frames.stream().anyMatch(frame -> frame.interp().equals("bezier"));
            List<Object> keys = new ArrayList<>();
            for (int n = 0; n < frames.size(); n++) {
                Frame frame = frames.get(n);
                Frame next = n + 1 < frames.size() ? frames.get(n + 1) : null;
                String leaving = next == null ? "linear" : segment(frame.interp(), next.interp());
                double[] pre = convert(channel, frame.points().getFirst(), legacy);
                double[] post = frame.points().size() > 1 ? convert(channel, frame.points().get(1), legacy) : pre;
                if (post != pre) keys.add(List.of(frame.time(), numbers(pre), "linear"));
                List<Object> key = new ArrayList<>(List.of(frame.time(), numbers(post), leaving));
                if (bezier) key.add(handles(channel, frame.source(), legacy));
                keys.add(key);
            }
            channels.put(channel, keys);
        }
        return channels;
    }

    private static String segment(String before, String after) {
        if (before.equals("step")) return "step";
        if (before.equals("catmullrom") || after.equals("catmullrom")) return "catmullrom";
        if (before.equals("bezier") || after.equals("bezier")) return "bezier";
        return "linear";
    }

    private static double[] convert(String channel, double[] v, boolean legacy) {
        return switch (channel) {
            case "rotation" -> legacy ? new double[] {-v[0], -v[1], v[2]} : v.clone();
            case "position" -> new double[] {(legacy ? -v[0] : v[0]) * PX, v[1] * PX, v[2] * PX};
            default -> v;
        };
    }

    private static Map<String, Object> handles(String channel, Map<?, ?> keyframe, boolean legacy) {
        Map<String, Object> handles = new LinkedHashMap<>();
        handles.put("in", handle(channel, keyframe.get("bezier_left_time"), keyframe.get("bezier_left_value"), -HANDLE, legacy));
        handles.put("out", handle(channel, keyframe.get("bezier_right_time"), keyframe.get("bezier_right_value"), HANDLE, legacy));
        return handles;
    }

    private static List<Object> handle(String channel, Object times, Object values, double fallback, boolean legacy) {
        double time = list(times).isEmpty() || !(list(times).getFirst() instanceof Number n) ? fallback : n.doubleValue();
        double[] value = new double[3];
        List<?> given = list(values);
        for (int c = 0; c < 3 && c < given.size(); c++) value[c] = given.get(c) instanceof Number n ? n.doubleValue() : 0;
        double[] turned = channel.equals("scale") ? value : convert(channel, value, legacy);
        return List.of(time, numbers(turned));
    }

    private static void effects(Map<?, ?> animator, List<Object> events) {
        for (Object entry : list(animator.get("keyframes"))) {
            if (!(entry instanceof Map<?, ?> keyframe) || !(keyframe.get("time") instanceof Number time)) continue;
            String channel = String.valueOf(keyframe.get("channel"));
            for (Object point : list(keyframe.get("data_points"))) {
                if (!(point instanceof Map<?, ?> data)) continue;
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("time", time.doubleValue());
                event.put("name", channel);
                if (channel.equals("timeline")) {
                    event.put("payload", data.get("script") == null ? "" : String.valueOf(data.get("script")));
                } else {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    for (String field : List.of("effect", "file", "locator", "script")) {
                        Object value = data.get(field);
                        if (value != null && !String.valueOf(value).isEmpty()) payload.put(field, String.valueOf(value));
                    }
                    event.put("payload", payload);
                }
                events.add(event);
            }
        }
    }

    private static double value(Object raw, double missing, String where, List<String> notes) {
        if (raw instanceof Number n) return n.doubleValue();
        if (raw == null) return missing;
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) return missing;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException molang) {
            notes.add(where + " is molang (" + text + "), it became " + (missing == 0 ? "0" : "1"));
            return missing;
        }
    }

    private static String unique(String wanted, Set<String> taken) {
        String name = wanted;
        for (int n = 2; !taken.add(name); n++) name = wanted + n;
        return name;
    }

    private static String safe(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        while (cleaned.startsWith(".")) cleaned = cleaned.substring(1);
        return cleaned.isEmpty() ? "animation" : cleaned;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static Vector3 vector(Object value, Vector3 missing) {
        if (!(value instanceof List<?> v) || v.size() < 3) return missing;
        return new Vector3(number(v.get(0)), number(v.get(1)), number(v.get(2)));
    }

    private static double number(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<Object> numbers(Vector3 v) {
        return numbers(new double[] {v.x(), v.y(), v.z()});
    }

    private static List<Object> numbers(double[] v) {
        List<Object> out = new ArrayList<>(v.length);
        for (double d : v) out.add(Math.abs(d) < 1e-12 ? 0.0 : Math.round(d * 1e6) / 1e6);
        return out;
    }

    public static String write(Object value) {
        return Json.write(value, BbmodelImport::flat);
    }

    private static boolean flat(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.size() > 4) return false;
            for (Object inside : map.values()) {
                if (inside instanceof Map<?, ?> || inside instanceof List<?> list && !nearlyShallow(list)) return false;
            }
            return true;
        }
        if (value instanceof List<?> list) {
            for (Object inside : list) {
                if (inside instanceof Map<?, ?> map && !flat(map)) return false;
                if (inside instanceof List<?> nested && !shallow(nested)) return false;
            }
        }
        return true;
    }

    private static boolean nearlyShallow(List<?> list) {
        for (Object inside : list) {
            if (inside instanceof Map<?, ?> || inside instanceof List<?> nested && !shallow(nested)) return false;
        }
        return true;
    }

    private static boolean shallow(List<?> list) {
        for (Object inside : list) {
            if (inside instanceof Map<?, ?> || inside instanceof List<?>) return false;
        }
        return true;
    }
}
