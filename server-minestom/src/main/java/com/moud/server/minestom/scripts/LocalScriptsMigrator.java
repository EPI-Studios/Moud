package com.moud.server.minestom.scripts;

import com.moud.server.minestom.util.DebugLog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class LocalScriptsMigrator {
    private static final String LEGACY_DIR = "local_scripts";
    private static final String TARGET_DIR = "scripts";
    private static final String CLIENT_SUFFIX = ".client.luau";
    private static final String LUAU_SUFFIX = ".luau";
    private static final String LEGACY_PREFIX = "local_scripts/";

    private final Path projectRoot;
    private final Map<String, String> pathRewrites = new HashMap<>();

    public LocalScriptsMigrator(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    public boolean run() {
        Path legacy = projectRoot.resolve(LEGACY_DIR);
        if (!Files.isDirectory(legacy)) return false;
        Path target = projectRoot.resolve(TARGET_DIR);
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            DebugLog.error("migrate-local-scripts", "create scripts dir failed: " + e.getMessage(), e);
            return false;
        }
        int moved = moveFiles(legacy, target);
        if (moved == 0) return false;
        int scenesUpdated = rewriteSceneReferences();
        cleanupEmptyDirs(legacy);
        DebugLog.info("migrate-local-scripts",
                "moved=" + moved + " scenes_updated=" + scenesUpdated);
        return true;
    }

    public Map<String, String> pathRewrites() {
        return Map.copyOf(pathRewrites);
    }

    private int moveFiles(Path legacy, Path target) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(legacy)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        } catch (IOException e) {
            DebugLog.error("migrate-local-scripts", "walk failed: " + e.getMessage(), e);
            return 0;
        }
        int moved = 0;
        for (Path source : files) {
            Path rel = legacy.relativize(source);
            Path dest = target.resolve(rel);
            String fileName = dest.getFileName().toString();
            if (fileName.endsWith(LUAU_SUFFIX) && !fileName.endsWith(CLIENT_SUFFIX)) {
                String base = fileName.substring(0, fileName.length() - LUAU_SUFFIX.length());
                dest = dest.resolveSibling(base + CLIENT_SUFFIX);
            }
            try {
                Files.createDirectories(dest.getParent());
                Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                DebugLog.error("migrate-local-scripts",
                        "move failed " + source + " -> " + dest + ": " + e.getMessage(), e);
                continue;
            }
            String oldRef = LEGACY_PREFIX + toForwardSlash(rel);
            String newRef = TARGET_DIR + "/" + toForwardSlash(target.relativize(dest));
            pathRewrites.put(oldRef, newRef);
            moved++;
        }
        return moved;
    }

    private int rewriteSceneReferences() {
        if (pathRewrites.isEmpty()) return 0;
        Path scenesDir = projectRoot.resolve("scenes");
        if (!Files.isDirectory(scenesDir)) return 0;
        int touched = 0;
        List<Path> scenes = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(scenesDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".moud.scene"))
                    .forEach(scenes::add);
        } catch (IOException e) {
            DebugLog.error("migrate-local-scripts", "scenes walk failed: " + e.getMessage(), e);
            return 0;
        }
        for (Path scene : scenes) {
            String original;
            try {
                original = Files.readString(scene, StandardCharsets.UTF_8);
            } catch (IOException e) {
                continue;
            }
            String rewritten = original;
            for (Map.Entry<String, String> rewrite : pathRewrites.entrySet()) {
                rewritten = rewritten.replace("\"" + rewrite.getKey() + "\"",
                        "\"" + rewrite.getValue() + "\"");
            }
            if (rewritten.equals(original)) continue;
            try {
                Files.writeString(scene, rewritten, StandardCharsets.UTF_8);
                touched++;
            } catch (IOException e) {
                DebugLog.error("migrate-local-scripts",
                        "write scene failed " + scene + ": " + e.getMessage(), e);
            }
        }
        return touched;
    }

    private void cleanupEmptyDirs(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> dirs = new ArrayList<>();
            walk.filter(Files::isDirectory).forEach(dirs::add);
            dirs.sort(Comparator.comparingInt(p -> -p.getNameCount()));
            for (Path dir : dirs) {
                try (Stream<Path> entries = Files.list(dir)) {
                    if (entries.findAny().isEmpty()) {
                        Files.deleteIfExists(dir);
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String toForwardSlash(Path path) {
        return path.toString().replace('\\', '/');
    }
}
