package com.meekdev.moud.mod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class JsonResources {

    private JsonResources() {}

    public static JsonObject read(String path) {
        try (InputStream in = JsonResources.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException(path + " is missing from the mod");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(path + " could not be read", e);
        }
    }
}
