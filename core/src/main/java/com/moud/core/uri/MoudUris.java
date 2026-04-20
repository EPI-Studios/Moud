package com.moud.core.uri;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MoudUris {
    public static final String SCHEME = "moud";
    public static final String ACTION_JOIN = "join";
    public static final String ACTION_PLAY = "play";

    private MoudUris() {
    }

    public static MoudUri parse(String rawUri) {
        if (rawUri == null || rawUri.isBlank()) {
            throw new IllegalArgumentException("URI is required");
        }

        String raw = rawUri.trim();
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage(), e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !SCHEME.equalsIgnoreCase(scheme.trim())) {
            throw new IllegalArgumentException("Unsupported URI scheme");
        }

        String action = resolveAction(uri);
        if (!ACTION_JOIN.equals(action) && !ACTION_PLAY.equals(action)) {
            throw new IllegalArgumentException("Unsupported action: " + action);
        }

        Map<String, String> query = parseQuery(uri.getRawQuery());
        String[] pathSegments = pathSegments(uri);

        String serverAddress = firstNonBlank(
                query.get("server"),
                query.get("address")
        );
        String sceneId = firstNonBlank(
                query.get("scene"),
                query.get("experience"),
                query.get("scene_id"),
                resolveScenePathSegment(pathSegments, action)
        );

        if (serverAddress == null && sceneId == null) {
            throw new IllegalArgumentException("URI must include at least a server or scene target");
        }

        return new MoudUri(raw, action, serverAddress, sceneId);
    }

    private static String resolveAction(URI uri) {
        String action = firstNonBlank(uri.getHost(), firstPathSegment(uri), schemeSpecificAction(uri));
        if (action == null) {
            throw new IllegalArgumentException("URI action is required");
        }
        return decode(action).trim().toLowerCase(Locale.ROOT);
    }

    private static String firstPathSegment(URI uri) {
        String[] segments = pathSegments(uri);
        return segments.length == 0 ? null : segments[0];
    }

    private static String schemeSpecificAction(URI uri) {
        String part = uri.getRawSchemeSpecificPart();
        if (part == null || part.isBlank()) {
            return null;
        }
        String clean = part;
        if (clean.startsWith("//")) {
            clean = clean.substring(2);
        }
        int query = clean.indexOf('?');
        if (query >= 0) {
            clean = clean.substring(0, query);
        }
        int slash = clean.indexOf('/');
        if (slash >= 0) {
            clean = clean.substring(0, slash);
        }
        clean = decode(clean).trim();
        return clean.isEmpty() ? null : clean;
    }

    private static String[] pathSegments(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return new String[0];
        }
        return java.util.Arrays.stream(path.split("/"))
                .filter(segment -> segment != null && !segment.isBlank())
                .map(MoudUris::decode)
                .toArray(String[]::new);
    }

    private static String resolveScenePathSegment(String[] pathSegments, String action) {
        if (pathSegments == null || pathSegments.length == 0) {
            return null;
        }
        if (action != null && action.equalsIgnoreCase(pathSegments[0])) {
            return pathSegments.length > 1 ? pathSegments[1] : null;
        }
        return pathSegments[0];
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            String decodedKey = decode(key).trim().toLowerCase(Locale.ROOT);
            if (decodedKey.isEmpty()) {
                continue;
            }
            String decodedValue = decode(value).trim();
            if (!decodedValue.isEmpty()) {
                map.put(decodedKey, decodedValue);
            }
        }
        return Map.copyOf(map);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
