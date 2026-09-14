package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.gl.Programs;
import com.meekdev.moud.script.api.ShaderRef;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

public final class ShaderPatches implements ShaderRef {

    public static final ShaderPatches INSTANCE = new ShaderPatches();

    record Stage(String after, String before, String at, String code, Map<String, String> out) {

        static Stage of(Object value) {
            if (!(value instanceof Map<?, ?> map)) return null;
            Map<String, String> out = new LinkedHashMap<>();
            if (map.get("out") instanceof Map<?, ?> declared) declared.forEach((name, type) -> out.put(String.valueOf(name), String.valueOf(type)));
            return new Stage(text(map.get("after")), text(map.get("before")), text(map.get("at")), text(map.get("code")), out);
        }

        private static String text(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }

    static final class Patch implements ShaderRef.Patch {
        final Set<String> targets = new HashSet<>();
        final Map<String, String> uniforms = new LinkedHashMap<>();
        final Stage vertex;
        final Stage fragment;
        final Map<String, Object> values = new ConcurrentHashMap<>();
        final Consumer<String> problems;
        final AtomicLong version = new AtomicLong(1);
        final Set<String> reported = ConcurrentHashMap.newKeySet();

        Patch(List<String> targets, Map<String, Object> spec, Consumer<String> problems) {
            for (String target : targets) this.targets.add(name(target));
            if (spec.get("uniforms") instanceof Map<?, ?> declared) declared.forEach((name, type) -> uniforms.put(String.valueOf(name), String.valueOf(type)));
            this.vertex = Stage.of(spec.get("vertex"));
            this.fragment = Stage.of(spec.get("fragment"));
            this.problems = problems;
        }

        @Override
        public void set(String uniform, Object value) {
            values.put(uniform, value);
            version.incrementAndGet();
        }

        @Override
        public void remove() {
            if (PATCHES.remove(this)) rebuild = true;
        }

        void problem(String text) {
            if (reported.add(text)) problems.accept(text);
        }
    }

    private static final class Program {
        final int id;
        final String vertex;
        final String fragment;
        final Map<String, Integer> locations = new ConcurrentHashMap<>();
        final Map<Patch, Long> uploaded = new ConcurrentHashMap<>();

        Program(int id, String vertex, String fragment) {
            this.id = id;
            this.vertex = vertex;
            this.fragment = fragment;
        }
    }

    private static final Pattern VERSION = Pattern.compile("^\\s*#(version|extension)[^\\n]*\\n", Pattern.MULTILINE);
    private static final Pattern MAIN = Pattern.compile("void\\s+main\\s*\\(\\s*\\)\\s*\\{");

    private static final List<Patch> PATCHES = new CopyOnWriteArrayList<>();
    private static final Map<Integer, Program> PROGRAMS = new ConcurrentHashMap<>();
    private static final ThreadLocal<List<String>> LOADING = ThreadLocal.withInitial(ArrayList::new);
    private static volatile boolean rebuild;

    private ShaderPatches() {}

    @Override
    public ShaderRef.Patch patch(List<String> targets, Map<String, Object> spec, Consumer<String> problems) {
        Patch patch = new Patch(targets, spec, problems);
        PATCHES.add(patch);
        rebuild = true;
        return patch;
    }

    public static String name(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) return id;
        String path = parsed.getPath();
        if (path.startsWith("shaders/")) path = path.substring("shaders/".length());
        int dot = path.lastIndexOf('.');
        if (dot > path.lastIndexOf('/')) path = path.substring(0, dot);
        return parsed.getNamespace() + ":" + path;
    }

    public static String source(String id, boolean vertex, String text) {
        if (text == null || PATCHES.isEmpty()) return text;
        String name = name(id);
        String out = text;
        Set<String> declared = new HashSet<>();
        for (Patch patch : PATCHES) {
            if (patch.targets.contains(name)) out = apply(patch, name, vertex, out, declared);
        }
        return out;
    }

    static String apply(Patch patch, String name, boolean vertex, String text, Set<String> declared) {
        StringBuilder head = new StringBuilder();
        patch.uniforms.forEach((uniform, type) -> {
            if (declared.add(uniform)) head.append("uniform ").append(type).append(' ').append(uniform).append(";\n");
        });
        if (patch.vertex != null) {
            patch.vertex.out().forEach((varying, type) -> {
                if (declared.add(varying)) head.append(vertex ? "out " : "in ").append(type).append(' ').append(varying).append(";\n");
            });
        }
        String out = text;
        Stage stage = vertex ? patch.vertex : patch.fragment;
        if (stage != null && stage.code() != null) out = insert(patch, name, vertex, out, stage);
        Matcher version = VERSION.matcher(out);
        int at = 0;
        while (version.find()) at = version.end();
        return out.substring(0, at) + head + out.substring(at);
    }

    private static String insert(Patch patch, String name, boolean vertex, String text, Stage stage) {
        String code = "\n" + stage.code() + "\n";
        String where = (vertex ? "vertex" : "fragment") + " shader of " + name;
        if (stage.after() != null) {
            int found = text.indexOf(stage.after());
            if (found < 0) return missing(patch, "after", stage.after(), where, text);
            int end = text.indexOf(';', found);
            end = end < 0 ? text.indexOf('\n', found) : end + 1;
            return text.substring(0, end) + code + text.substring(end);
        }
        if (stage.before() != null) {
            int found = text.indexOf(stage.before());
            if (found < 0) return missing(patch, "before", stage.before(), where, text);
            int line = text.lastIndexOf('\n', found) + 1;
            return text.substring(0, line) + code + text.substring(line);
        }
        Matcher main = MAIN.matcher(text);
        if (!main.find()) return missing(patch, "at", "void main()", where, text);
        if ("end".equals(stage.at())) {
            int close = closing(text, main.end() - 1);
            if (close < 0) return missing(patch, "at", "the end of main", where, text);
            return text.substring(0, close) + code + text.substring(close);
        }
        return text.substring(0, main.end()) + code + text.substring(main.end());
    }

    private static int closing(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static String missing(Patch patch, String kind, String anchor, String where, String text) {
        patch.problem("the " + where + " has no \"" + anchor + "\" to put code " + kind + ", the patch skips it there");
        return text;
    }

    public static void loading(String id) {
        LOADING.get().add(name(id));
    }

    public static void linked(int program, String vertex, String fragment) {
        PROGRAMS.put(program, new Program(program, name(vertex), name(fragment)));
    }

    public static void linkedLoaded(int program) {
        List<String> loaded = LOADING.get();
        if (loaded.size() >= 2) linked(program, loaded.get(loaded.size() - 2), loaded.get(loaded.size() - 1));
        loaded.clear();
    }

    public static void frame() {
        if (rebuild) {
            rebuild = false;
            PROGRAMS.clear();
            RenderSystem.getDevice().clearPipelineCache();
            InstanceMeshRegistry.INSTANCE.reloadShaders();
            return;
        }
        if (PATCHES.isEmpty() || PROGRAMS.isEmpty()) return;
        for (Program program : PROGRAMS.values()) {
            for (Patch patch : PATCHES) {
                if (!patch.targets.contains(program.vertex) && !patch.targets.contains(program.fragment)) continue;
                long version = patch.version.get();
                Long done = program.uploaded.get(patch);
                if (done != null && done == version) continue;
                if (!Programs.exists(program.id)) {
                    PROGRAMS.remove(program.id);
                    break;
                }
                upload(program, patch);
                program.uploaded.put(patch, version);
            }
        }
    }

    private static void upload(Program program, Patch patch) {
        boolean direct = Programs.separateUniforms();
        int previous = direct ? 0 : Programs.current();
        if (!direct) Programs.use(program.id);
        try {
            for (Map.Entry<String, Object> entry : patch.values.entrySet()) {
                String type = patch.uniforms.get(entry.getKey());
                if (type == null) continue;
                int location = program.locations.computeIfAbsent(entry.getKey(), key -> Programs.location(program.id, key));
                if (location < 0) continue;
                write(program.id, location, type, entry.getValue(), direct, patch);
            }
        } finally {
            if (!direct) Programs.use(previous);
        }
    }

    private static void write(int program, int location, String type, Object value, boolean direct, Patch patch) {
        float[] f = floats(value);
        switch (type) {
            case "int", "bool" -> {
                int i = value instanceof Boolean b ? (b ? 1 : 0) : f.length > 0 ? Math.round(f[0]) : 0;
                Programs.uniform(program, location, i, direct);
            }
            case "float" -> Programs.uniform(program, location, f, 1, direct);
            case "vec2" -> Programs.uniform(program, location, f, 2, direct);
            case "vec3" -> Programs.uniform(program, location, f, 3, direct);
            case "vec4" -> Programs.uniform(program, location, f, 4, direct);
            default -> patch.problem("uniform type " + type + " is not supported, use float, int, bool, vec2, vec3 or vec4");
        }
    }

    private static float[] floats(Object value) {
        return switch (value) {
            case Number n -> new float[] {n.floatValue()};
            case Boolean b -> new float[] {b ? 1 : 0};
            case Vector3 v -> new float[] {(float) v.x(), (float) v.y(), (float) v.z()};
            case Color c -> new float[] {c.r(), c.g(), c.b(), c.a()};
            case Quat q -> new float[] {(float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()};
            case List<?> list -> {
                float[] out = new float[list.size()];
                for (int n = 0; n < out.length; n++) out[n] = list.get(n) instanceof Number x ? x.floatValue() : 0;
                yield out;
            }
            default -> new float[0];
        };
    }
}
