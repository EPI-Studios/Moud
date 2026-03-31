package com.moud.core.update;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReleaseManifestParser {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private ReleaseManifestParser() {
    }

    public static ReleaseManifest parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("release manifest json is empty");
        }
        ReleaseManifest manifest = GSON.fromJson(json, ReleaseManifest.class);
        if (manifest == null) {
            throw new IllegalArgumentException("release manifest json did not produce a manifest");
        }
        manifest.validate();
        return manifest;
    }

    public static ReleaseManifest parse(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ReleaseManifest manifest = GSON.fromJson(reader, ReleaseManifest.class);
            if (manifest == null) {
                throw new IllegalArgumentException("release manifest file did not produce a manifest: " + path);
            }
            manifest.validate();
            return manifest;
        }
    }
}
