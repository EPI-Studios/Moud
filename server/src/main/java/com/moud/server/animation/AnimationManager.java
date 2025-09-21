package com.moud.server.animation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.server.project.ProjectLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AnimationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, Map<String, Object>> animations = new ConcurrentHashMap<>();

    public void initialize() {
        try {
            Path animationsDir = ProjectLoader.findProjectRoot().resolve("assets").resolve("animations");
            if (!Files.exists(animationsDir) || !Files.isDirectory(animationsDir)) {
                LOGGER.info("No animations directory found, skipping animation loading.");
                return;
            }

            try (Stream<Path> paths = Files.walk(animationsDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .forEach(this::loadAnimation);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan for animations", e);
        }
    }

    private void loadAnimation(Path path) {
        String fileName = path.getFileName().toString();
        String animationName = fileName.substring(0, fileName.lastIndexOf('.'));
        try {
            Map<String, Object> data = MAPPER.readValue(path.toFile(), new TypeReference<>() {});
            animations.put(animationName, data);
            LOGGER.info("Loaded animation: {}", animationName);
        } catch (IOException e) {
            LOGGER.error("Failed to load animation file: {}", path, e);
        }
    }

    public Map<String, Map<String, Object>> getAllAnimations() {
        return animations;
    }
}