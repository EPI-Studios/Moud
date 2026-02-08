package com.moud.server.plugin.impl;

import com.moud.plugin.api.services.SceneService;
import com.moud.server.editor.BlueprintStorage;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SceneServiceImpl implements SceneService {
    private final BlueprintStorage storage;
    private final Logger logger;

    public SceneServiceImpl(Path projectRoot, Logger logger) {
        this.logger = logger;
        this.storage = new BlueprintStorage(projectRoot);
    }

    @Override
    public Path blueprintDirectory() {
        return storage.getDirectory();
    }

    @Override
    public List<String> listBlueprints() {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storage.getDirectory(), "*.json")) {
            for (Path path : stream) {
                names.add(path.getFileName().toString());
            }
        } catch (IOException e) {
            logger.error("Failed to list blueprints", e);
        }
        return names;
    }

    @Override
    public Optional<String> loadBlueprint(String name) {
        try {
            return Optional.of(new String(storage.load(name), StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to load blueprint {}", name, e);
            return Optional.empty();
        }
    }

    @Override
    public void saveBlueprint(String name, String json) {
        try {
            storage.save(name, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to save blueprint {}", name, e);
        }
    }
}
