package com.moud.server.minestom.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.moud.server.minestom.util.DebugLog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PersistenceService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path playersDir;
    private final Path worldFile;
    private final Map<UUID, Map<String, String>> players = new ConcurrentHashMap<>();
    private final Map<String, String> world = new ConcurrentHashMap<>();
    private volatile boolean worldLoaded = false;

    public PersistenceService(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Path saves = projectRoot.resolve("saves");
        this.playersDir = saves.resolve("players");
        this.worldFile = saves.resolve("world.json");
        try {
            Files.createDirectories(this.playersDir);
        } catch (IOException e) {
            DebugLog.warn("persistence", "Cannot create saves directory: " + e.getMessage());
        }
    }

    public void loadWorld() {
        if (worldLoaded) return;
        worldLoaded = true;
        if (!Files.exists(worldFile)) return;
        try {
            String text = Files.readString(worldFile, StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(text, JsonObject.class);
            if (obj != null) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                    if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                        world.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
        } catch (Exception e) {
            DebugLog.warn("persistence", "Failed to load world save: " + e.getMessage());
        }
    }

    public void loadPlayer(UUID uuid) {
        if (uuid == null) return;
        Path file = playerFile(uuid);
        if (!Files.exists(file)) {
            players.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            return;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(text, JsonObject.class);
            Map<String, String> bag = new ConcurrentHashMap<>();
            if (obj != null) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                    if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                        bag.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
            players.put(uuid, bag);
        } catch (Exception e) {
            DebugLog.warn("persistence", "Failed to load player save " + uuid + ": " + e.getMessage());
            players.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        }
    }

    public void savePlayer(UUID uuid) {
        if (uuid == null) return;
        Map<String, String> bag = players.get(uuid);
        if (bag == null) return;
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>(bag);
        try {
            Files.createDirectories(playersDir);
            String json = GSON.toJson(snapshot);
            Files.writeString(playerFile(uuid), json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            DebugLog.warn("persistence", "Failed to save player " + uuid + ": " + e.getMessage());
        }
    }

    public void saveWorld() {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>(world);
        try {
            Files.createDirectories(worldFile.getParent());
            String json = GSON.toJson(snapshot);
            Files.writeString(worldFile, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            DebugLog.warn("persistence", "Failed to save world: " + e.getMessage());
        }
    }

    public String getPlayer(UUID uuid, String key) {
        if (uuid == null || key == null) return null;
        Map<String, String> bag = players.get(uuid);
        return bag == null ? null : bag.get(key);
    }

    public void setPlayer(UUID uuid, String key, String value) {
        if (uuid == null || key == null || key.isBlank()) return;
        Map<String, String> bag = players.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        if (value == null) bag.remove(key);
        else bag.put(key, value);
    }

    public String getWorld(String key) {
        if (key == null) return null;
        return world.get(key);
    }

    public void setWorld(String key, String value) {
        if (key == null || key.isBlank()) return;
        if (value == null) world.remove(key);
        else world.put(key, value);
    }

    public Map<String, String> playerSnapshot(UUID uuid) {
        Map<String, String> bag = uuid == null ? null : players.get(uuid);
        return bag == null ? Map.of() : new HashMap<>(bag);
    }

    public void clearPlayer(UUID uuid) {
        if (uuid == null) return;
        players.remove(uuid);
    }

    private Path playerFile(UUID uuid) {
        return playersDir.resolve(uuid.toString() + ".json");
    }
}
