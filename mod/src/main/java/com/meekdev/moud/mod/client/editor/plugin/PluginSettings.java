package com.meekdev.moud.mod.client.editor.plugin;

import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.mod.MoudMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class PluginSettings {

    private static final long WRITE_NANOS = 1_000_000_000L;

    private final Path file;
    private @Nullable Map<String, Map<String, Object>> kept;
    private boolean dirty;
    private long writtenAt = System.nanoTime() - WRITE_NANOS;

    PluginSettings(Path file) {
        this.file = file;
    }

    @Nullable Object get(String plugin, String key) {
        Map<String, Object> values = all().get(plugin);
        return values == null ? null : values.get(key);
    }

    void set(String plugin, String key, @Nullable Object value) {
        Map<String, Map<String, Object>> all = all();
        if (value == null) {
            Map<String, Object> values = all.get(plugin);
            if (values == null || values.remove(key) == null) return;
            if (values.isEmpty()) all.remove(plugin);
        } else {
            all.computeIfAbsent(plugin, name -> new LinkedHashMap<>()).put(key, value);
        }
        dirty = true;
    }

    void tick() {
        if (dirty && System.nanoTime() - writtenAt >= WRITE_NANOS) flush();
    }

    void flush() {
        if (!dirty || kept == null) return;
        dirty = false;
        writtenAt = System.nanoTime();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.write(kept));
        } catch (IOException e) {
            MoudMod.LOG.warn("could not keep plugin settings in {}", file, e);
        }
    }

    private Map<String, Map<String, Object>> all() {
        if (kept != null) return kept;
        kept = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) return kept;
        try {
            if (Json.parse(Files.readString(file)) instanceof Map<?, ?> read) {
                for (Map.Entry<?, ?> entry : read.entrySet()) {
                    if (!(entry.getValue() instanceof Map<?, ?> values)) continue;
                    Map<String, Object> copy = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> value : values.entrySet()) copy.put(String.valueOf(value.getKey()), value.getValue());
                    kept.put(String.valueOf(entry.getKey()), copy);
                }
            }
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.warn("could not read plugin settings from {}", file, e);
        }
        return kept;
    }
}
