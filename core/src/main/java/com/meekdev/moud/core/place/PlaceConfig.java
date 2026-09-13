package com.meekdev.moud.core.place;

import com.meekdev.moud.core.asset.Res;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record PlaceConfig(
        String name,
        String id,
        String version,
        String engine,
        int maxPlayers,
        String server,
        String client,
        String scene,
        Map<String, Boolean> features) {

    private static final Set<String> TOP = Set.of("name", "id", "version", "engine", "maxPlayers", "entry", "features");
    private static final Set<String> ENTRY = Set.of("server", "client", "scene");

    public static final PlaceConfig DEFAULT = new PlaceConfig("place", "place", "0.0.0", "", 16,
            "res://server/main.luau", "res://client/main.luau", "", Map.of());

    public static PlaceConfig parse(String text) {
        Map<String, Object> root = Toml.parse(text);
        for (String key : root.keySet()) {
            if (!TOP.contains(key)) throw new IllegalArgumentException("unknown place.toml setting '" + key + "', expected one of name, id, version, engine, maxPlayers, entry, features");
        }
        Map<String, Object> entry = table(root, "entry");
        for (String key : entry.keySet()) {
            if (!ENTRY.contains(key)) throw new IllegalArgumentException("unknown [entry] setting '" + key + "', expected server, client or scene");
        }
        Map<String, Boolean> features = new LinkedHashMap<>();
        for (Map.Entry<String, Object> one : table(root, "features").entrySet()) {
            if (!(one.getValue() instanceof Boolean on)) {
                throw new IllegalArgumentException("[features] " + one.getKey() + " must be true or false");
            }
            features.put(one.getKey(), on);
        }
        long players = number(root, "maxPlayers", DEFAULT.maxPlayers);
        if (players < 1) throw new IllegalArgumentException("maxPlayers must be at least 1");
        return new PlaceConfig(
                text(root, "name", DEFAULT.name),
                text(root, "id", DEFAULT.id),
                text(root, "version", DEFAULT.version),
                text(root, "engine", DEFAULT.engine),
                (int) players,
                path(entry, "server", DEFAULT.server),
                path(entry, "client", DEFAULT.client),
                path(entry, "scene", ""),
                Map.copyOf(features));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> table(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value == null) return Map.of();
        if (!(value instanceof Map)) throw new IllegalArgumentException(key + " must be a table");
        return (Map<String, Object>) value;
    }

    private static String text(Map<String, Object> table, String key, String fallback) {
        Object value = table.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String s)) throw new IllegalArgumentException(key + " must be a string");
        return s;
    }

    private static long number(Map<String, Object> table, String key, long fallback) {
        Object value = table.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Long n)) throw new IllegalArgumentException(key + " must be an integer");
        return n;
    }

    private static String path(Map<String, Object> table, String key, String fallback) {
        String value = text(table, key, fallback);
        if (!value.isEmpty()) Res.parse(value);
        return value;
    }
}
