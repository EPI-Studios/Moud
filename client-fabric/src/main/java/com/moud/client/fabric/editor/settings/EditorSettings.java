package com.moud.client.fabric.editor.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EditorSettings {
    public static final String KEY_EXTERNAL_SCRIPT_EDITOR = "editor.externalScriptEditor";

    private final Path file;
    private final Map<String, String> values = new LinkedHashMap<>();

    public EditorSettings(Path file) {
        this.file = file;
        load();
    }

    public String get(String key, String fallback) {
        String v = values.get(key);
        return v == null ? fallback : v;
    }

    public void set(String key, String value) {
        if (key == null) return;
        if (value == null || value.isBlank()) values.remove(key);
        else values.put(key, value);
        persist();
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        } catch (IOException ignored) {
        }
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : values.entrySet()) {
                sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
