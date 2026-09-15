package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.scene.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record ImportSettings(double scale, boolean nearest, boolean stream, double volume) {

    public static final String SUFFIX = ".import.json";
    public static final ImportSettings DEFAULT = new ImportSettings(1, false, false, 1);
    private static final long STREAM_BYTES = 1_500_000;
    private static final int PIXEL_ART = 64;

    public static Path sidecar(Path file) {
        return file.resolveSibling(file.getFileName() + SUFFIX);
    }

    public static ImportSettings of(Path file) {
        return parse(read(sidecar(file)));
    }

    public static ImportSettings parse(String text) {
        Object parsed;
        try {
            parsed = text == null ? null : Json.parse(text);
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
        if (!(parsed instanceof Map<?, ?> map)) return DEFAULT;
        return new ImportSettings(number(map.get("scale"), 1), map.get("nearest") instanceof Boolean b && b,
                map.get("stream") instanceof Boolean s && s, number(map.get("volume"), 1));
    }

    public void save(Path file) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scale", scale);
        map.put("nearest", nearest);
        map.put("stream", stream);
        map.put("volume", volume);
        Files.writeString(sidecar(file), Json.write(map));
    }

    public static ImportSettings guess(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".png")) {
                byte[] head = new byte[24];
                try (var in = Files.newInputStream(file)) {
                    if (in.read(head) == 24) {
                        int width = (head[16] & 0xFF) << 24 | (head[17] & 0xFF) << 16 | (head[18] & 0xFF) << 8 | head[19] & 0xFF;
                        int height = (head[20] & 0xFF) << 24 | (head[21] & 0xFF) << 16 | (head[22] & 0xFF) << 8 | head[23] & 0xFF;
                        return new ImportSettings(1, width <= PIXEL_ART && height <= PIXEL_ART, false, 1);
                    }
                }
            }
            if (name.endsWith(".ogg") || name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".flac")) {
                return new ImportSettings(1, false, Files.size(file) > STREAM_BYTES, 1);
            }
        } catch (IOException ignored) {
        }
        return DEFAULT;
    }

    private static String read(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}
