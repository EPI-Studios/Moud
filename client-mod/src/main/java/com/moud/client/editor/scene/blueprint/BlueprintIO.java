package com.moud.client.editor.scene.blueprint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BlueprintIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlueprintIO.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BlueprintIO() {}

    public static void save(Path path, Blueprint blueprint) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(blueprint, writer);
        }
    }

    public static Blueprint load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, Blueprint.class);
        }
    }

    public static Blueprint loadQuiet(Path path) {
        try {
            return load(path);
        } catch (IOException e) {
            LOGGER.error("Failed to load blueprint {}", path, e);
            return null;
        }
    }

    public static String toJsonString(Blueprint blueprint) {
        return GSON.toJson(blueprint);
    }

    public static Blueprint fromJsonString(String json) {
        return GSON.fromJson(json, Blueprint.class);
    }
}
