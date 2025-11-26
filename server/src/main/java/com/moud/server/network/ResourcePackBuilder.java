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

    /**
     * Build a resource pack zip from the project's assets directory.
     *
     * @return path to the created zip, or null if assets are missing or an error occurs.
     */
    static Path buildFromProjectAssets() {
        Path projectRoot = ProjectLoader.findProjectRoot();
        Path assetsDir = projectRoot.resolve("assets");
        if (!Files.isDirectory(assetsDir)) {
            LOGGER.warn("No assets directory found at {}, skipping resource pack build", assetsDir);
            return null;
        }
        Path cacheDir = projectRoot.resolve(".moud/cache");
        Path packPath = cacheDir.resolve("moud-resourcepack.zip");
        try {
            Files.createDirectories(cacheDir);
            try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(packPath));
                 ZipOutputStream zip = new ZipOutputStream(os)) {
                writePackMeta(zip);
                Files.walk(assetsDir)
                        .filter(Files::isRegularFile)
                        .forEach(path -> addAsset(path, assetsDir, zip));
            }
            long size = Files.size(packPath);
            LOGGER.info(LogContext.builder()
                    .put("path", packPath.toAbsolutePath())
                    .put("size_bytes", size)
                    .build(), "Built resource pack from project assets");
            return packPath;
        } catch (Exception e) {
            LOGGER.warn("Failed to build resource pack from assets at {}", assetsDir, e);
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
                    "description": "Moud dynamic resources"
                  }
                }
                """.formatted(PACK_FORMAT_1_21);
        zip.write(json.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void addAsset(Path file, Path assetsDir, ZipOutputStream zip) {
        try {
            String relative = assetsDir.relativize(file).toString().replace('\\', '/');
            String entryName = "assets/" + relative;
            zip.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zip);
            zip.closeEntry();
        } catch (IOException e) {
            LOGGER.warn("Failed to add asset {} to resource pack", file, e);
        }
    }
}
