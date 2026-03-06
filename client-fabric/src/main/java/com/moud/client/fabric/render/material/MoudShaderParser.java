package com.moud.client.fabric.render.material;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32C;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MoudShaderParser {
    private static final Pattern STAGE_LINE = Pattern.compile("^\\s*#stage\\s+([a-zA-Z_]+)\\s*$");
    private static final Pattern UNIFORM_LINE = Pattern.compile("\\buniform\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+([a-zA-Z_][a-zA-Z0-9_]*)(\\s*\\[\\s*(\\d+)\\s*\\])?\\s*;");
    private static final Pattern LAYOUT_PREFIX = Pattern.compile("^\\s*layout\\s*\\([^)]*\\)\\s*");

    private static final String DEFAULT_BLIT_VERTEX = """
            out vec2 texCoord;

            void main() {
                vec2 uv = vec2(gl_VertexID & 1, gl_VertexID & 2);
                gl_Position = vec4(uv * vec2(3.0) - vec2(1.0), 0.0, 1.0);
                texCoord = uv * vec2(1.5);
            }
            """;

    private MoudShaderParser() {
    }

    public static MoudShaderFile parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        String currentStage = null;
        boolean sawStage = false;

        StringBuilder noStage = new StringBuilder();
        Map<String, StringBuilder> stageBuilders = new HashMap<>();

        LinkedHashMap<String, MoudShaderUniform> uniforms = new LinkedHashMap<>();
        ExposeInfo pendingExpose = null;

        for (String line : lines) {
            if (line == null) {
                continue;
            }
            Matcher stageMatch = STAGE_LINE.matcher(line);
            if (stageMatch.matches()) {
                currentStage = stageMatch.group(1);
                sawStage = true;
                continue;
            }

            parseUniform(line, uniforms, pendingExpose);
            ExposeInfo exposeOnLine = parseExpose(line);
            boolean hasUniformDecl = hasUniformDecl(line);
            if (exposeOnLine != null && !hasUniformDecl) {
                pendingExpose = exposeOnLine;
            } else if (hasUniformDecl) {
                pendingExpose = null;
            }

            if (currentStage == null) {
                noStage.append(line).append('\n');
                continue;
            }
            stageBuilders.computeIfAbsent(currentStage, k -> new StringBuilder()).append(line).append('\n');
        }

        if (!sawStage) {
            stageBuilders.put("fragment", noStage);
        }

        Int2ObjectMap<String> stageSources = new Int2ObjectArrayMap<>();
        String vertex = stageString(stageBuilders, "vertex");
        String fragment = stageString(stageBuilders, "fragment");
        if (fragment == null || fragment.isBlank()) {
            return null;
        }
        if (vertex == null || vertex.isBlank()) {
            vertex = DEFAULT_BLIT_VERTEX;
        }
        stageSources.put(GL20C.GL_VERTEX_SHADER, vertex);
        stageSources.put(GL20C.GL_FRAGMENT_SHADER, fragment);

        String geometry = stageString(stageBuilders, "geometry");
        if (geometry != null && !geometry.isBlank()) {
            stageSources.put(GL32C.GL_GEOMETRY_SHADER, geometry);
        }

        return new MoudShaderFile(stageSources, List.copyOf(uniforms.values()));
    }

    private static String stageString(Map<String, StringBuilder> stageBuilders, String stage) {
        StringBuilder b = stageBuilders.get(stage);
        if (b != null) {
            return b.toString();
        }
        StringBuilder alt = stageBuilders.get(stage.toUpperCase(Locale.ROOT));
        return alt != null ? alt.toString() : null;
    }

    private static boolean hasUniformDecl(String line) {
        if (line == null) {
            return false;
        }
        String code = stripComments(line);
        code = LAYOUT_PREFIX.matcher(code).replaceFirst("");
        return UNIFORM_LINE.matcher(code).find();
    }

    private static void parseUniform(String line, LinkedHashMap<String, MoudShaderUniform> uniforms, ExposeInfo pending) {
        if (line == null) {
            return;
        }
        String code = stripComments(line);
        if (code.indexOf('{') >= 0) {
            return;
        }
        code = LAYOUT_PREFIX.matcher(code).replaceFirst("");

        Matcher m = UNIFORM_LINE.matcher(code);
        if (!m.find()) {
            return;
        }

        String type = m.group(1);
        String name = m.group(2);
        if (name == null || name.isBlank()) {
            return;
        }

        ExposeInfo expose = parseExpose(line);
        boolean exposed = expose != null || pending != null;
        Map<String, String> hints = exposed ? (expose != null ? expose.hints : pending.hints) : Map.of();

        uniforms.putIfAbsent(name, new MoudShaderUniform(name, type, exposed, hints));
    }

    private static String stripComments(String line) {
        int lineComment = line.indexOf("//");
        int blockComment = line.indexOf("/*");
        int cut = -1;
        if (lineComment >= 0) {
            cut = lineComment;
        }
        if (blockComment >= 0) {
            cut = cut < 0 ? blockComment : Math.min(cut, blockComment);
        }
        if (cut >= 0) {
            return line.substring(0, cut);
        }
        return line;
    }

    private static ExposeInfo parseExpose(String line) {
        int idx = line.indexOf("@expose");
        if (idx < 0) {
            return null;
        }
        String rest = line.substring(idx + "@expose".length()).trim();
        if (rest.isEmpty()) {
            return new ExposeInfo(Map.of());
        }

        HashMap<String, String> hints = new HashMap<>();
        for (String token : rest.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            int eq = token.indexOf('=');
            if (eq <= 0) {
                hints.put(token, "true");
                continue;
            }
            String key = token.substring(0, eq).trim();
            String value = token.substring(eq + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!key.isBlank()) {
                hints.put(key, value);
            }
        }
        return new ExposeInfo(Map.copyOf(hints));
    }

    private record ExposeInfo(Map<String, String> hints) {
    }
}

