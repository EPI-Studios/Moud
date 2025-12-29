package com.moud.server.network;

import com.moud.api.util.PathUtils;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.project.ProjectLoader;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ResourcePackBuilder {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(ResourcePackBuilder.class);
    private static final int PACK_FORMAT_1_21 = 34;

    private ResourcePackBuilder() {
    }

    static Path buildFromProjectAssets() {
        Path projectRoot = ProjectLoader.findProjectRoot();

        List<Path> assetRoots = new ArrayList<>();

        Path rootAssets = projectRoot.resolve("assets");
        if (Files.isDirectory(rootAssets)) assetRoots.add(rootAssets);

        Path clientAssets = projectRoot.resolve("client").resolve("assets");
        if (Files.isDirectory(clientAssets)) assetRoots.add(clientAssets);

        Path packagesDir = projectRoot.resolve("packages");
        if (Files.isDirectory(packagesDir)) {
            try (Stream<Path> dirs = Files.walk(packagesDir, 2)) {
                dirs.filter(path -> Files.isDirectory(path) && path.getFileName().toString().equals("assets"))
                        .forEach(assetRoots::add);
            } catch (IOException e) {
                LOGGER.warn("Failed to scan packages for assets", e);
            }
        }

        Path repoRoot = projectRoot.getParent() != null ? projectRoot.getParent().getParent() : null;
        Path internalClientAssets = repoRoot != null ? repoRoot.resolve("client-mod/src/main/resources/assets") : null;
        if (internalClientAssets != null && Files.isDirectory(internalClientAssets)) {
            assetRoots.add(internalClientAssets);
        }

        if (assetRoots.isEmpty()) {
            LOGGER.warn("No asset directories found skipping resource pack build");
            return null;
        }

        Path cacheDir = projectRoot.resolve(".moud/cache");
        Path packPath = cacheDir.resolve("moud-resourcepack.zip");

        try {
            Files.createDirectories(cacheDir);
            AtomicInteger addedCount;
            try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(packPath));
                 ZipOutputStream zip = new ZipOutputStream(os)) {

                writePackMeta(zip);
                Set<String> added = new HashSet<>();
                addedCount = new AtomicInteger();

                for (Path root : assetRoots) {
                    LOGGER.info(LogContext.builder().put("path", root.toString()).build(), "Including assets from {}", root);
                    addAssetRoot(zip, root, added, addedCount);
                }


                generateMissingSoundsJson(assetRoots, zip, added);
            }

            long size = Files.size(packPath);
            LOGGER.info(LogContext.builder()
                    .put("path", packPath.toAbsolutePath())
                    .put("size_bytes", size)
                    .put("entries", addedCount.get())
                    .build(), "Built resource pack from project assets");
            return packPath;
        } catch (Exception e) {
            LOGGER.warn("Failed to build resource pack", e);
            return null;
        }
    }

    private static void writePackMeta(ZipOutputStream zip) throws IOException {
        ZipEntry meta = new ZipEntry("pack.mcmeta");
        zip.putNextEntry(meta);
        String json = """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "Moud resources"
                  }
                }
                """.formatted(PACK_FORMAT_1_21);
        zip.write(json.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void addAssetRoot(ZipOutputStream zip, Path root, Set<String> added, AtomicInteger addedCount) throws IOException {
        Files.walk(root)
                .filter(Files::isRegularFile)
                .forEach(path -> addAsset(path, root, zip, added, addedCount));
    }

    private static void addAsset(Path file, Path assetsDir, ZipOutputStream zip, Set<String> added, AtomicInteger addedCount) {
        try {
            String relative = PathUtils.normalizeSlashes(assetsDir.relativize(file).toString());
            String entryName = "assets/" + relative;
            if (!added.add(entryName)) {
                return;
            }
            zip.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zip);
            zip.closeEntry();
            addedCount.incrementAndGet();
        } catch (IOException e) {
            LOGGER.warn("Failed to add asset {} to resource pack", file, e);
        }
    }



    private static void generateMissingSoundsJson(List<Path> roots, ZipOutputStream zip, Set<String> added) throws IOException {
        Map<String, Map<String, String>> namespaceSounds = new HashMap<>();

        for (Path root : roots) {
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".ogg");
                        })
                        .forEach(file -> {
                            Path rel = root.relativize(file);
                            String relPath = PathUtils.normalizeSlashes(rel.toString());
                            int firstSlash = relPath.indexOf('/');
                            if (firstSlash <= 0) return;
                            String namespace = relPath.substring(0, firstSlash);
                            String remainder = relPath.substring(firstSlash + 1);
                            if (!remainder.startsWith("sounds/")) return;

                            String idPath = remainder.substring("sounds/".length());
                            int ext = idPath.lastIndexOf('.');
                            if (ext > 0) {
                                idPath = idPath.substring(0, ext);
                            }
                            if (idPath.isEmpty()) return;

                            namespaceSounds
                                    .computeIfAbsent(namespace, ns -> new HashMap<>())
                                    .putIfAbsent(idPath, idPath);
                        });
            } catch (IOException e) {
                LOGGER.warn("Failed to scan assets for sounds under {}", root, e);
            }
        }

        for (var entry : namespaceSounds.entrySet()) {
            String namespace = entry.getKey();
            String soundsJsonPath = "assets/" + namespace + "/sounds.json";
            if (added.contains(soundsJsonPath)) {
                continue; // respect user sounds.json
            }
            var sounds = entry.getValue();
            if (sounds.isEmpty()) continue;

            added.add(soundsJsonPath);
            zip.putNextEntry(new ZipEntry(soundsJsonPath));

            StringBuilder json = new StringBuilder();
            json.append("{\n");
            int i = 0;
            for (var sound : sounds.entrySet()) {
                String id = sound.getKey();
                String namespaced = namespace + ":" + id;

                json.append("  \"").append(id).append("\": { \"sounds\": [ { \"name\": \"")
                        .append(namespaced).append("\", \"stream\": true } ] }");

                if (++i < sounds.size()) {
                    json.append(",\n");
                } else {
                    json.append("\n");
                }
            }
            json.append("}");

            zip.write(json.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            LOGGER.info("Generated sounds.json for namespace '{}' with {} entries", namespace, sounds.size());
        }
    }
}
