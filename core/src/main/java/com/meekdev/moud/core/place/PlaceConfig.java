package com.meekdev.moud.core.place;

import com.meekdev.moud.core.asset.Res;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        String loadingScript,
        String scene,
        Map<String, Boolean> features,
        Loading loading,
        Streaming streaming,
        Host host) {

    public record Host(String lookup, String address) {
        public static final Host DEFAULT = new Host("", "");

        public boolean set() {
            return !lookup.isEmpty() || !address.isEmpty();
        }
    }

    public record Streaming(boolean enabled, double radius) {
        public static final Streaming DEFAULT = new Streaming(true, 256);
    }

    public record Loading(String background, String color, String logo, String text, List<String> tips) {
        public static final Loading DEFAULT = new Loading("", "#12151c", "", "Loading", List.of());
    }

    private static final List<String> TOP = List.of("name", "id", "version", "engine", "maxPlayers", "entry", "features", "loading", "streaming", "server");
    private static final Set<String> LOADING = Set.of("background", "color", "logo", "text", "tips");
    private static final Set<String> STREAMING = Set.of("enabled", "radius");
    private static final double NEAREST = 32;
    private static final double FARTHEST = 4096;
    private static final Set<String> ENTRY = Set.of("server", "client", "loading", "scene");
    private static final Set<String> SERVER = Set.of("lookup", "address");
    private static final Set<String> LOCAL = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    public static final PlaceConfig DEFAULT = new PlaceConfig("place", "place", "0.0.0", "", 16,
            "res://server/main", "res://client/main", "res://client/loading", "", Map.of(), Loading.DEFAULT, Streaming.DEFAULT, Host.DEFAULT);

    public static PlaceConfig parse(String text) {
        Map<String, Object> root = Toml.parse(text);
        for (String key : root.keySet()) {
            if (!TOP.contains(key)) throw new IllegalArgumentException("unknown place.toml setting '" + key + "', expected one of " + String.join(", ", TOP));
        }
        Map<String, Object> entry = table(root, "entry");
        for (String key : entry.keySet()) {
            if (!ENTRY.contains(key)) throw new IllegalArgumentException("unknown [entry] setting '" + key + "', expected server, client, loading or scene");
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
                path(entry, "loading", DEFAULT.loadingScript),
                path(entry, "scene", ""),
                Map.copyOf(features),
                loading(table(root, "loading")),
                streaming(table(root, "streaming")),
                host(table(root, "server")));
    }

    private static Host host(Map<String, Object> table) {
        for (String key : table.keySet()) {
            if (!SERVER.contains(key)) throw new IllegalArgumentException("unknown [server] setting '" + key + "', expected lookup or address");
        }
        String lookup = text(table, "lookup", "").strip();
        String address = text(table, "address", "").strip();
        if (!lookup.isEmpty() && !address.isEmpty()) throw new IllegalArgumentException("[server] takes lookup or address, not both");
        if (!lookup.isEmpty()) lookup(lookup);
        if (!address.isEmpty() && (address.contains("://") || address.chars().anyMatch(Character::isWhitespace))) {
            throw new IllegalArgumentException("[server] address looks like play.example.net or play.example.net:25565");
        }
        return new Host(lookup, address);
    }

    public static void lookup(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("[server] lookup is not a link: " + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (uri.getHost() == null || !scheme.equals("https") && !scheme.equals("http")) {
            throw new IllegalArgumentException("[server] lookup must be an https:// link");
        }
        if (scheme.equals("http") && !LOCAL.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("[server] lookup must use https, plain http is only for localhost");
        }
    }

    private static Streaming streaming(Map<String, Object> table) {
        for (String key : table.keySet()) {
            if (!STREAMING.contains(key)) throw new IllegalArgumentException("unknown [streaming] setting '" + key + "', expected enabled or radius");
        }
        boolean enabled = !Boolean.FALSE.equals(table.get("enabled"));
        Object radius = table.get("radius");
        if (radius == null) return new Streaming(enabled, Streaming.DEFAULT.radius());
        if (!(radius instanceof Number number)) throw new IllegalArgumentException("[streaming] radius must be a number");
        double metres = number.doubleValue();
        if (metres < NEAREST || metres > FARTHEST) {
            throw new IllegalArgumentException("[streaming] radius must be between " + (int) NEAREST + " and " + (int) FARTHEST + " metres");
        }
        return new Streaming(enabled, metres);
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
