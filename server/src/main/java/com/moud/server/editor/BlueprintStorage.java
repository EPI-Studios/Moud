package com.moud.server.editor;

import com.moud.server.logging.MoudLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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

    public List<String> list() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return names;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString())
                    .map(n -> n.substring(0, n.length() - 5)) // remove .json
                    .sorted()
                    .forEach(names::add);
        } catch (IOException e) {
            LOGGER.error("Failed to list blueprints in {}", directory, e);
        }
        return names;
    }


    public boolean delete(String name) {
        Path path = resolvePath(name);
        if (!Files.exists(path)) {
            return false;
        }
        try {
            Files.delete(path);
            LOGGER.info("Deleted blueprint: {}", name);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to delete blueprint {}", name, e);
            return false;
        }
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
