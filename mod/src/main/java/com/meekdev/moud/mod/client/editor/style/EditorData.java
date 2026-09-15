package com.meekdev.moud.mod.client.editor.style;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class EditorData {

    private EditorData() {}

    public static JsonElement read(String path) {
        InputStream in = EditorData.class.getResourceAsStream(path);
        if (in == null) throw new IllegalStateException("editor resource " + path + " is missing");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("editor resource " + path + " could not be read", e);
        }
    }

    public static Map<String, String> strings(String path) {
        Map<String, String> strings = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : read(path).getAsJsonObject().entrySet()) {
            strings.put(entry.getKey(), entry.getValue().getAsString());
        }
        return Map.copyOf(strings);
    }
}
