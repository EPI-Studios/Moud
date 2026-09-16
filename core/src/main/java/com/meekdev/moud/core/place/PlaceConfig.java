package com.meekdev.moud.core.place;

import com.meekdev.moud.core.asset.Res;
import java.util.ArrayList;
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
        Map<String, Boolean> features,
        Loading loading) {

    public record Loading(String background, String color, String logo, String text, List<String> tips) {
        public static final Loading DEFAULT = new Loading("", "#12151c", "", "Loading", List.of());
    }

    private static final List<String> TOP = List.of("name", "id", "version", "engine", "maxPlayers", "entry", "features", "loading");
    private static final Set<String> LOADING = Set.of("background", "color", "logo", "text", "tips");
    private static final Set<String> ENTRY = Set.of("server", "client", "scene");

    public static final PlaceConfig DEFAULT = new PlaceConfig("place", "place", "0.0.0", "", 16,
            "res://server/main", "res://client/main", "", Map.of(), Loading.DEFAULT);

    public static PlaceConfig parse(String text) {
        Map<String, Object> root = Toml.parse(text);
        for (String key : root.keySet()) {
            if (!TOP.contains(key)) throw new IllegalArgumentException("unknown place.toml setting '" + key + "', expected one of " + String.join(", ", TOP));
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
                Map.copyOf(features),
                loading(table(root, "loading")));
    }

    private static Loading loading(Map<String, Object> table) {
        for (String key : table.keySet()) {
            if (!LOADING.contains(key)) throw new IllegalArgumentException("unknown [loading] setting '" + key + "', expected background, color, logo, text or tips");
        }
        String background = text(table, "background", "");
        String logo = text(table, "logo", "");
        if (!background.isEmpty()) Res.parse(background);
        if (!logo.isEmpty()) Res.parse(logo);
        String color = text(table, "color", Loading.DEFAULT.color());
        if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("[loading] color is a hex colour like #12151c");
        List<String> tips = new ArrayList<>();
        Object raw = table.get("tips");
        if (raw != null) {
            if (!(raw instanceof List<?> list)) throw new IllegalArgumentException("[loading] tips is a list of strings");
            for (Object tip : list) {
                if (!(tip instanceof String s)) throw new IllegalArgumentException("[loading] tips is a list of strings");
                tips.add(s);
            }
        }
        return new Loading(background, color, logo, text(table, "text", Loading.DEFAULT.text()), List.copyOf(tips));
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
        if (!value.isEmpty()) {
            if (key.equals("scene")) Res.parse(value); else Res.script(value);
        }
        return value;
    }
}
