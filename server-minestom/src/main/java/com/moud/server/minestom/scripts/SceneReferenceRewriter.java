package com.moud.server.minestom.scripts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class SceneReferenceRewriter {
    private static final Set<String> PROPERTIES = Set.of("script", "client_script");
    private static final String SCENE_SUFFIX = ".moud.scene";

    private final Path scenesDir;

    public SceneReferenceRewriter(Path projectRoot) {
        this.scenesDir = projectRoot.resolve("scenes");
    }

    public int rewriteExact(String oldValue, String newValue) {
        if (oldValue == null || oldValue.isBlank() || newValue == null) return 0;
        return walk(content -> replaceExactRefs(content, oldValue, newValue));
    }

    public int rewritePrefix(String oldPrefix, String newPrefix) {
        if (oldPrefix == null || oldPrefix.isBlank() || newPrefix == null) return 0;
        return walk(content -> replacePrefixRefs(content, oldPrefix, newPrefix));
    }

    public int rewriteMany(Map<String, String> exactRewrites) {
        if (exactRewrites == null || exactRewrites.isEmpty()) return 0;
        return walk(content -> {
            String result = content;
            for (Map.Entry<String, String> e : exactRewrites.entrySet()) {
                result = replaceExactRefs(result, e.getKey(), e.getValue());
            }
            return result;
        });
    }

    private int walk(java.util.function.Function<String, String> transform) {
        if (!Files.isDirectory(scenesDir)) return 0;
        List<Path> scenes = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(scenesDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(SCENE_SUFFIX))
                    .forEach(scenes::add);
        } catch (IOException e) {
            return 0;
        }
        int touched = 0;
        for (Path scene : scenes) {
            String original;
            try {
                original = Files.readString(scene, StandardCharsets.UTF_8);
            } catch (IOException e) {
                continue;
            }
            String next = transform.apply(original);
            if (next.equals(original)) continue;
            try {
                Files.writeString(scene, next, StandardCharsets.UTF_8);
                touched++;
            } catch (IOException ignored) {
            }
        }
        return touched;
    }

    private static String replaceExactRefs(String content, String oldValue, String newValue) {
        String result = content;
        for (String prop : PROPERTIES) {
            String needle = "\"" + prop + "\"" + ":" + "\"" + oldValue + "\"";
            String replacement = "\"" + prop + "\"" + ":" + "\"" + newValue + "\"";
            result = result.replace(needle, replacement);
            String spaced = "\"" + prop + "\"" + ": " + "\"" + oldValue + "\"";
            String spacedReplacement = "\"" + prop + "\"" + ": " + "\"" + newValue + "\"";
            result = result.replace(spaced, spacedReplacement);
        }
        return result;
    }

    private static String replacePrefixRefs(String content, String oldPrefix, String newPrefix) {
        String result = content;
        for (String prop : PROPERTIES) {
            String[] needles = {
                    "\"" + prop + "\":\"" + oldPrefix,
                    "\"" + prop + "\": \"" + oldPrefix
            };
            String[] replacements = {
                    "\"" + prop + "\":\"" + newPrefix,
                    "\"" + prop + "\": \"" + newPrefix
            };
            for (int i = 0; i < needles.length; i++) {
                result = result.replace(needles[i], replacements[i]);
            }
        }
        return result;
    }
}
