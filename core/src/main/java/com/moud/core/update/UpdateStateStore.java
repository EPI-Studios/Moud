package com.moud.core.update;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class UpdateStateStore {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private UpdateStateStore() {
    }

    public static UpdateState load(Path path, String defaultChannel) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }
        if (!Files.isRegularFile(path)) {
            return UpdateState.empty(defaultChannel);
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            UpdateState state = GSON.fromJson(reader, UpdateState.class);
            if (state == null) {
                return UpdateState.empty(defaultChannel);
            }
            state.validate();
            return state;
        }
    }

    public static void save(Path path, UpdateState state) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state is null");
        }
        state.validate();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmpFile = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmpFile, StandardCharsets.UTF_8)) {
            GSON.toJson(state, writer);
        }
        try {
            Files.move(tmpFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpFile, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
