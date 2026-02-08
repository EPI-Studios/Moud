package com.moud.client.editor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EditorConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoudEditorConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("moud_editor.json");
    private static final EditorConfig INSTANCE = new EditorConfig();

    private boolean loaded;
    private Camera camera = new Camera();

    public static EditorConfig getInstance() {
        if (!INSTANCE.loaded) {
            INSTANCE.load();
        }
        return INSTANCE;
    }

    public Camera camera() {
        return camera;
    }

    public void reload() {
        loaded = false;
        load();
    }

    private void load() {
        ensureFileExists();
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            EditorConfig data = GSON.fromJson(bufferedReader, EditorConfig.class);
            if (data != null && data.camera != null) {
                this.camera = data.camera;
            }
            this.loaded = true;
        } catch (IOException e) {
            LOGGER.error("Failed to read editor config, using defaults", e);
            this.camera = new Camera();
            this.loaded = true;
        }
    }

    private void ensureFileExists() {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create default editor config", e);
        }
    }

    @SuppressWarnings("unused")
    public static final class Camera {
        public double orbitSensitivity = 0.25;
        public double panSpeed = 0.004;
        public double zoomSensitivity = 0.18;
        public double orbitSmoothing = 0.65;
        public double minDistance = 0.5;
        public double maxDistance = 512.0;
        public double defaultDistance = 12.0;
        public double focusDistance = 6.0;
        public float flySpeed = 0.35f;
        public float flyBoostMultiplier = 3.0f;
        public int focusKey = 70;
    }
}