package com.moud.client.editor.scene.blueprint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public final class BlueprintIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlueprintIO.class);
    private static final Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(byte[].class, new ByteArrayAdapter())
            .create();

    private BlueprintIO() {}

    public static void save(Path path, Blueprint blueprint) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(blueprint, writer);
        }
    }

    public static Blueprint load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, Blueprint.class);
        }
    }

    public static Blueprint loadQuiet(Path path) {
        try {
            return load(path);
        } catch (IOException e) {
            LOGGER.error("Failed to load blueprint {}", path, e);
            return null;
        }
    }

    public static String toJsonString(Blueprint blueprint) {
        return GSON.toJson(blueprint);
    }

    public static Blueprint fromJsonString(String json) {
        return GSON.fromJson(json, Blueprint.class);
    }

    private static final class ByteArrayAdapter extends TypeAdapter<byte[]> {
        @Override
        public void write(JsonWriter out, byte[] value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(Base64.getEncoder().encodeToString(value));
        }

        @Override
        public byte[] read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            if (token == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            if (token == JsonToken.STRING) {
                String encoded = in.nextString();
                if (encoded == null || encoded.isEmpty()) {
                    return new byte[0];
                }
                try {
                    return Base64.getDecoder().decode(encoded);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Invalid base64 voxel payload in blueprint JSON; returning empty buffer");
                    return new byte[0];
                }
            }

            if (token == JsonToken.BEGIN_ARRAY) {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                in.beginArray();
                while (in.hasNext()) {
                    if (in.peek() == JsonToken.NUMBER) {
                        int raw = in.nextInt();
                        buffer.write((byte) raw);
                    } else if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        buffer.write(0);
                    } else {
                        in.skipValue();
                    }
                }
                in.endArray();
                return buffer.toByteArray();
            }

            in.skipValue();
            return new byte[0];
        }
    }
}
