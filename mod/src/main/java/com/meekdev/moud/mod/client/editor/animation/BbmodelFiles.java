package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.Retarget;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.scene.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class BbmodelFiles implements ModelImport {

    static final List<String> PLAYER = List.of("head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg");

    private static @Nullable ModelImport runtime;

    public static void runtime(@Nullable ModelImport converter) {
        runtime = converter;
    }

    static String joint(String group) {
        String joint = Retarget.joint(group);
        return joint == null ? "" : joint;
    }

    @Override
    public Summary inspect(Path file) throws IOException {
        return inspect(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
    }

    static Summary inspect(String name, String text) {
        Object parsed = Json.parse(text);
        if (!(parsed instanceof Map<?, ?> root)) throw new IllegalArgumentException("a .bbmodel file holds a JSON object");
        Map<String, Cube> elements = new HashMap<>();
        for (Object entry : list(root.get("elements"))) {
            if (!(entry instanceof Map<?, ?> element) || element.get("uuid") == null) continue;
            elements.put(String.valueOf(element.get("uuid")), new Cube(vector(element.get("from")), vector(element.get("to"))));
        }
        Map<String, Map<?, ?>> groupData = new HashMap<>();
        for (Object entry : list(root.get("groups"))) {
            if (entry instanceof Map<?, ?> group && group.get("uuid") != null) groupData.put(String.valueOf(group.get("uuid")), group);
        }
        List<Group> groups = new ArrayList<>();
        for (Object entry : list(root.get("outliner"))) walk(entry, 0, groupData, elements, groups);
        Set<String> matched = new LinkedHashSet<>();
        for (Group group : groups) {
            if (PLAYER.contains(group.joint())) matched.add(group.joint());
        }
        List<Animation> animations = new ArrayList<>();
        for (Object entry : list(root.get("animations"))) {
            if (entry instanceof Map<?, ?> animation) animations.add(animation(animation));
        }
        String format = "Blockbench";
        if (root.get("meta") instanceof Map<?, ?> meta && meta.get("model_format") != null) format += " · " + formatName(String.valueOf(meta.get("model_format")));
        return new Summary(name, format, groups, animations, matched.size(), PLAYER.size());
    }

    private static String formatName(String format) {
        return switch (format) {
            case "free" -> "generic model";
            case "java_block" -> "Java block";
            case "bedrock", "bedrock_old" -> "Bedrock entity";
            case "modded_entity" -> "modded entity";
            case "animated_entity_model" -> "animated entity";
            default -> format.replace('_', ' ');
        };
    }

    private static void walk(Object entry, int depth, Map<String, Map<?, ?>> groupData, Map<String, Cube> elements, List<Group> into) {
        if (!(entry instanceof Map<?, ?> node)) return;
        Map<?, ?> data = node.get("name") != null ? node : groupData.getOrDefault(String.valueOf(node.get("uuid")), node);
        String name = data.get("name") == null ? "group" : String.valueOf(data.get("name"));
        List<Cube> shape = new ArrayList<>();
        int cubes = 0;
        for (Object child : list(node.get("children"))) {
            if (child instanceof String uuid) {
                cubes++;
                Cube cube = elements.get(uuid);
                if (cube != null) shape.add(cube);
            }
        }
        into.add(new Group(String.valueOf(node.get("uuid")), name, joint(name), vector(data.get("origin")), cubes, depth, shape));
        for (Object child : list(node.get("children"))) {
            if (child instanceof Map<?, ?>) walk(child, depth + 1, groupData, elements, into);
        }
    }

    private static Animation animation(Map<?, ?> animation) {
        String name = animation.get("name") == null ? "animation" : String.valueOf(animation.get("name"));
        double length = animation.get("length") instanceof Number number ? number.doubleValue() : 0;
        String loop = animation.get("loop") == null ? "once" : String.valueOf(animation.get("loop"));
        int bones = 0;
        int keys = 0;
        int molang = 0;
        if (animation.get("animators") instanceof Map<?, ?> animators) {
            for (Object value : animators.values()) {
                if (!(value instanceof Map<?, ?> animator)) continue;
                List<?> frames = list(animator.get("keyframes"));
                if (frames.isEmpty()) continue;
                bones++;
                for (Object frame : frames) {
                    if (!(frame instanceof Map<?, ?> keyframe)) continue;
                    keys++;
                    if (hasMolang(keyframe)) molang++;
                    if (keyframe.get("time") instanceof Number time) length = Math.max(length, time.doubleValue());
                }
            }
        }
        return new Animation(name, length, loop, bones, keys, molang);
    }

    private static boolean hasMolang(Map<?, ?> keyframe) {
        for (Object point : list(keyframe.get("data_points"))) {
            if (!(point instanceof Map<?, ?> values)) continue;
            for (String axis : new String[] {"x", "y", "z"}) {
                Object value = values.get(axis);
                if (value instanceof String text && !text.isBlank() && !ClipFile.isNumber(text.strip())) return true;
            }
        }
        return false;
    }

    @Override
    public Outcome run(Path file, Summary summary, Choices choices) {
        ModelImport converter = runtime;
        if (converter == null) return new Outcome(false, "there is no importer to run", List.of(), List.of());
        return converter.run(file, summary, choices);
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static Vector3 vector(Object value) {
        if (value instanceof List<?> v && v.size() == 3 && v.get(0) instanceof Number x && v.get(1) instanceof Number y && v.get(2) instanceof Number z) {
            return new Vector3(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        return Vector3.ZERO;
    }
}
