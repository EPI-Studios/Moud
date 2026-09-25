package com.meekdev.moud.script.luau;

import com.meekdev.moud.core.instance.Instance;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LuauScene {

    private static final String INDENT = "    ";
    private static final String ROOT = "World";
    private static final String GAME = "Game";
    private static final String FALLBACK = "Instance";
    private static final int MOST_OVERLOADS = 100;
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TYPE = Pattern.compile("declare extern type (\\w+)(?: extends (\\w+))? with");
    private static final Pattern ALIAS = Pattern.compile("export type (\\w+)");
    private static final Pattern MEMBER = Pattern.compile("(?:read |write |function )?(\\w+)\\s*[:(]");
    private static final Set<String> RESERVED = Set.of("and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while",
            "continue", "export", "type", "typeof", "read", "write");

    private final Map<String, String> parents = new HashMap<>();
    private final Map<String, Set<String>> members = new HashMap<>();
    private final Predicate<Instance> keep;
    private final Set<String> taken = new HashSet<>();
    private final StringBuilder out = new StringBuilder();

    private LuauScene(String types, Predicate<Instance> keep) {
        this.keep = keep;
        read(types);
    }

    static String declare(String types, Instance world, Predicate<Instance> keep) {
        LuauScene scene = new LuauScene(types, keep);
        if (!scene.members.containsKey(GAME)) return "";
        String root = scene.node(world, ROOT);
        if (root.equals(world.def().name())) return "";
        String game = scene.unique("Scene" + GAME);
        scene.out.append("declare extern type ").append(game).append(" extends ").append(GAME).append(" with\n");
        scene.out.append(INDENT).append("read world: ").append(root).append('\n');
        scene.out.append("end\n\n");
        scene.out.append("declare game: ").append(game).append('\n');
        return scene.out.toString();
    }

    private void read(String types) {
        Set<String> current = null;
        for (String line : types.split("\n")) {
            Matcher alias = ALIAS.matcher(line);
            if (alias.lookingAt()) taken.add(alias.group(1));
            Matcher type = TYPE.matcher(line);
            if (type.matches()) {
                current = new HashSet<>();
                members.put(type.group(1), current);
                if (type.group(2) != null) parents.put(type.group(1), type.group(2));
                continue;
            }
            if (current == null) continue;
            if (line.equals("end")) {
                current = null;
                continue;
            }
            Matcher member = MEMBER.matcher(line.strip());
            if (member.lookingAt()) current.add(member.group(1));
        }
        taken.addAll(members.keySet());
    }

    private String node(Instance instance, String path) {
        String base = members.containsKey(instance.def().name()) ? instance.def().name() : FALLBACK;
        Map<String, String> children = new LinkedHashMap<>();
        for (Instance child : instance.children()) {
            if (!keep.test(child) || children.containsKey(child.name()) || !plain(child.name())) continue;
            children.put(child.name(), node(child, path + "_" + clean(child.name())));
        }
        if (children.isEmpty()) return base;
        String name = unique(path);
        out.append("declare extern type ").append(name).append(" extends ").append(base).append(" with\n");
        for (Map.Entry<String, String> child : children.entrySet()) {
            if (indexable(child.getKey(), base)) out.append(INDENT).append("read ").append(child.getKey()).append(": ").append(child.getValue()).append('\n');
        }
        children.entrySet().stream().limit(MOST_OVERLOADS).forEach(child ->
                out.append(INDENT).append("function find(self, name: ").append(quote(child.getKey())).append("): ").append(child.getValue()).append('\n'));
        out.append(INDENT).append("function find(self, name: string): Instance?\n");
        out.append("end\n\n");
        return name;
    }

    private boolean indexable(String child, String type) {
        if (!NAME.matcher(child).matches() || RESERVED.contains(child)) return false;
        for (String at = type; at != null; at = parents.get(at)) {
            Set<String> own = members.get(at);
            if (own != null && own.contains(child)) return false;
        }
        return true;
    }

    private String unique(String name) {
        String candidate = name;
        for (int n = 2; !taken.add(candidate); n++) candidate = name + n;
        return candidate;
    }

    private static String clean(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "");
    }

    private static boolean plain(String name) {
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) return false;
        }
        return !name.isEmpty();
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
