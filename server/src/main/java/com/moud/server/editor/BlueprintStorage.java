package com.moud.server.editor;

import com.moud.server.logging.MoudLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BlueprintStorage {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(BlueprintStorage.class);
    private final Path directory;

    public BlueprintStorage(Path projectRoot) {
        Path root = projectRoot != null ? projectRoot : Path.of(".");
        this.directory = root.resolve(".moud").resolve("blueprints");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            LOGGER.error("Failed to create blueprint directory {}", directory, e);
        }
    }

    public Path getDirectory() {
        return directory;
    }

    public void save(String name, byte[] data) throws IOException {
        Files.write(resolvePath(name), data);
    }

    public byte[] load(String name) throws IOException {
        return Files.readAllBytes(resolvePath(name));
    }

    public boolean exists(String name) {
        return Files.exists(resolvePath(name));
    }

    private Path resolvePath(String name) {
        String sanitized = sanitize(name);
        if (sanitized.isEmpty()) {
            sanitized = "blueprint";
        }
        if (!sanitized.endsWith(".json")) {
            sanitized += ".json";
        }
        return directory.resolve(sanitized);
    }

    private String sanitize(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
