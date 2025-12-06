package com.moud.server.network;

import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.project.ProjectLoader;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ResourcePackBuilder {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(ResourcePackBuilder.class);
    private static final int PACK_FORMAT_1_21 = 34;

    private ResourcePackBuilder() {
    }

    static Path buildFromProjectAssets() {
        Path projectRoot = ProjectLoader.findProjectRoot();

        java.util.List<Path> assetRoots = new java.util.ArrayList<>();

        Path rootAssets = projectRoot.resolve("assets");
        if (Files.isDirectory(rootAssets)) assetRoots.add(rootAssets);

        Path clientAssets = projectRoot.resolve("client").resolve("assets");
        if (Files.isDirectory(clientAssets)) assetRoots.add(clientAssets);

        Path packagesDir = projectRoot.resolve("packages");
        if (Files.isDirectory(packagesDir)) {
            try (java.util.stream.Stream<Path> dirs = Files.walk(packagesDir, 2)) {
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
            try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(packPath));
                 ZipOutputStream zip = new ZipOutputStream(os)) {

                writePackMeta(zip);
                java.util.Set<String> added = new java.util.HashSet<>();

                for (Path root : assetRoots) {
                    LOGGER.info(LogContext.builder().put("path", root.toString()).build(), "Including assets from {}", root);
                    addAssetRoot(zip, root, added);
                }
            }

            long size = Files.size(packPath);
            LOGGER.info(LogContext.builder()
                    .put("path", packPath.toAbsolutePath())
                    .put("size_bytes", size)
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

    private static void addAssetRoot(ZipOutputStream zip, Path root, java.util.Set<String> added) throws IOException {
        Files.walk(root)
                .filter(Files::isRegularFile)
                .forEach(path -> addAsset(path, root, zip, added));
    }

    private static void addAsset(Path file, Path assetsDir, ZipOutputStream zip, java.util.Set<String> added) {
        try {
            String relative = assetsDir.relativize(file).toString().replace('\\', '/');
            String entryName = "assets/" + relative;
            if (!added.add(entryName)) {
                return;
            }
            zip.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zip);
            zip.closeEntry();
        } catch (IOException e) {
            LOGGER.warn("Failed to add asset {} to resource pack", file, e);
        }
    }
}
