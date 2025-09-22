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
import java.util.stream.Stream;

public class AnimationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, Map<String, Object>> animations = new ConcurrentHashMap<>();
    private final Map<String, String> animationJsonContent = new ConcurrentHashMap<>();

    private static AnimationManager instance;

    public AnimationManager() {
        instance = this;
    }

    public static AnimationManager getInstance() {
        return instance;
    }

    public void initialize() {
        try {
            Path projectRoot = ProjectLoader.findProjectRoot();

            Path[] animationDirs = {
                    projectRoot.resolve("assets").resolve("moud").resolve("data").resolve("animation"),
                    projectRoot.resolve("assets").resolve("animations"),
                    projectRoot.resolve("assets").resolve("animation"),
                    projectRoot.resolve("client").resolve("assets").resolve("animations")
            };

            boolean foundAnimations = false;
            for (Path animationsDir : animationDirs) {
                if (Files.exists(animationsDir) && Files.isDirectory(animationsDir)) {
                    LOGGER.info("Found animations directory: {}", animationsDir);
                    loadAnimationsFromDirectory(animationsDir);
                    foundAnimations = true;
                }
            }

            if (!foundAnimations) {
                LOGGER.info("No animations directory found, creating built-in animations only.");
            }

            LOGGER.info("Animation manager initialized with {} animations", animations.size());

        } catch (IOException e) {
            LOGGER.error("Failed to scan for animations", e);
        }
    }

    private void loadAnimationsFromDirectory(Path animationsDir) throws IOException {
        try (Stream<Path> paths = Files.walk(animationsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(this::loadAnimation);
        }
    }

    private void loadAnimation(Path path) {
        String fileName = path.getFileName().toString();
        String animationName = fileName.substring(0, fileName.lastIndexOf('.'));

        try {
            String jsonContent = Files.readString(path);
            Map<String, Object> data = MAPPER.readValue(jsonContent, new TypeReference<>() {});

            animations.put(animationName, data);
            animationJsonContent.put(animationName, jsonContent);

            LOGGER.info("Loaded animation: {} from {}", animationName, path);

        } catch (IOException e) {
            LOGGER.error("Failed to load animation file: {}", path, e);
        }
    }

    public Map<String, Map<String, Object>> getAllAnimations() {
        return animations;
    }

    public Map<String, String> getAllAnimationJsonContent() {
        return animationJsonContent;
    }

    public String getAnimationJson(String animationName) {
        return animationJsonContent.get(animationName);
    }

    public boolean hasAnimation(String animationName) {
        return animations.containsKey(animationName);
    }
}