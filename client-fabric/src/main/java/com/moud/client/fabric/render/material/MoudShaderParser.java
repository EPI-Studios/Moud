package com.moud.client.fabric.render.material;

import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.ResPath;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32C;


public final class MoudShaderParser {
    private static final Pattern STAGE_LINE = Pattern.compile("^\\s*#stage\\s+([a-zA-Z_]+)\\s*$");
    private static final Pattern UNIFORM_LINE = Pattern.compile("\\buniform\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+([a-zA-Z_][a-zA-Z0-9_]*)(\\s*\\[\\s*(\\d+)\\s*\\])?\\s*");
    private static final Pattern LAYOUT_PREFIX = Pattern.compile("^\\s*layout\\s*\\([^)]*\\)\\s*");
    private static final Pattern INCLUDE_LINE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
    private static final int MAX_INCLUDE_DEPTH = 8;
    private static final String BUILTIN_SHADER_PREFIX = "assets/moud/shaders/builtin/";

    private static final String DEFAULT_BLIT_VERTEX =
            loadBuiltin("default_blit.vert");

    private MoudShaderParser() {
    }

    private static String loadBuiltin(String name) {
        try (InputStream is = MoudShaderParser.class.getClassLoader()
                .getResourceAsStream(BUILTIN_SHADER_PREFIX + name)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return "";
    }

    public static MoudShaderFile parse(String text) {
        return parse(text, null);
    }

    public static MoudShaderFile parse(String text, String sourcePath) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String baseDir = baseDirFor(sourcePath);
        PreprocessResult pre = preprocessIncludes(text, 0, baseDir, new LinkedHashSet<>());
        text = pre.text;

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

        AssetHash hash = hashProgramSources(stageSources);
        return new MoudShaderFile(stageSources, List.copyOf(uniforms.values()), hash, pre.dependencies);
    }

    private static AssetHash hashProgramSources(Int2ObjectMap<String> stageSources) {
        String v = stageSources.get(GL20C.GL_VERTEX_SHADER);
        String f = stageSources.get(GL20C.GL_FRAGMENT_SHADER);
        String g = stageSources.get(GL32C.GL_GEOMETRY_SHADER);
        StringBuilder b = new StringBuilder();
        if (v != null) b.append("#stage vertex\n").append(v).append('\n');
        if (f != null) b.append("#stage fragment\n").append(f).append('\n');
        if (g != null) b.append("#stage geometry\n").append(g).append('\n');
        return AssetHash.sha256(b.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static PreprocessResult preprocessIncludes(String source,
                                                      int depth,
                                                      String baseDir,
                                                      LinkedHashSet<String> deps) {
        if (source == null || depth >= MAX_INCLUDE_DEPTH) {
            return new PreprocessResult(source == null ? "" : source, List.copyOf(deps));
        }
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            Matcher m = INCLUDE_LINE.matcher(lines[i]);
            if (m.matches()) {
                String includeRaw = m.group(1);
                ResolvedInclude inc = resolveInclude(includeRaw, baseDir);
                if (inc != null && inc.resolvedPath != null && inc.resolvedPath.startsWith(ResPath.SCHEME)) {
                    deps.add(inc.resolvedPath);
                }
                if (inc != null && inc.text != null) {
                    PreprocessResult inner = preprocessIncludes(
                            inc.text,
                            depth + 1,
                            baseDirFor(inc.resolvedPath),
                            deps
                    );
                    result.append(inner.text);
                } else {
                    result.append("// #include failed: ").append(includeRaw).append('\n');
                }
            } else {
                result.append(lines[i]);
                if (i < lines.length - 1) {
                    result.append('\n');
                }
            }
        }
        return new PreprocessResult(result.toString(), List.copyOf(deps));
    }

    private static ResolvedInclude resolveInclude(String raw, String baseDir) {
        String path = raw == null ? "" : raw.trim();
        if (path.isEmpty()) {
            return null;
        }

        if (path.startsWith(ResPath.SCHEME)) {
            return new ResolvedInclude(path, MoudTextAssets.readText(path));
        }

        if (baseDir != null && !baseDir.isBlank()) {
            String rel = path.startsWith("/") ? path.substring(1) : path;
            String candidate = baseDir.endsWith("/") ? (baseDir + rel) : (baseDir + "/" + rel);
            if (candidate.startsWith(ResPath.SCHEME) && MoudTextAssets.exists(candidate)) {
                return new ResolvedInclude(candidate, MoudTextAssets.readText(candidate));
            }
        }

        try (InputStream is = MoudShaderParser.class.getClassLoader()
                .getResourceAsStream(BUILTIN_SHADER_PREFIX + path)) {
            if (is != null) {
                return new ResolvedInclude(null, new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String baseDirFor(String sourcePath) {
        if (sourcePath == null) {
            return null;
        }
        String sp = sourcePath.trim();
        if (!sp.startsWith(ResPath.SCHEME)) {
            return null;
        }
        int slash = sp.lastIndexOf('/');
        if (slash < 0) {
            return sp;
        }
        return sp.substring(0, slash + 1);
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

    private record ResolvedInclude(String resolvedPath, String text) {
    }

    private record PreprocessResult(String text, List<String> dependencies) {
    }
}
