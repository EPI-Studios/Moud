package com.moud.core.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UpdateStateStoreTest {

    @Test
    void saveWritesViaTmpFileAndRename(@TempDir Path tmp) throws IOException {
        Path statePath = tmp.resolve("update-state.json");
        UpdateState state = new UpdateState(1, "stable", "1.0.0", "1.0.0", "1.0.0", Map.of(), List.of());
        UpdateStateStore.save(statePath, state);

        assertTrue(Files.isRegularFile(statePath));
        assertFalse(Files.exists(statePath.resolveSibling("update-state.json.tmp")),
                "tmp file should not remain after save");

        UpdateState loaded = UpdateStateStore.load(statePath, "stable");
        assertEquals("1.0.0", loaded.currentVersion());
    }

    @Test
    void savePreservesExistingStateIfTmpWriteFails(@TempDir Path tmp) throws IOException {
        Path statePath = tmp.resolve("update-state.json");
        UpdateState original = new UpdateState(1, "stable", "0.9.0", "0.9.0", "0.9.0", Map.of(), List.of());
        UpdateStateStore.save(statePath, original);

        UpdateState loaded = UpdateStateStore.load(statePath, "stable");
        assertEquals("0.9.0", loaded.currentVersion());
    }

    @Test
    void loadReturnsEmptyForMissingFile(@TempDir Path tmp) throws IOException {
        Path statePath = tmp.resolve("nonexistent.json");
        UpdateState state = UpdateStateStore.load(statePath, "beta");
        assertEquals("beta", state.channel());
        assertTrue(state.currentVersion().isBlank());
    }

    @Test
    void loadReturnsEmptyForCorruptFile(@TempDir Path tmp) throws IOException {
        Path statePath = tmp.resolve("corrupt.json");
        Files.writeString(statePath, "not valid json {{{");
        assertThrows(Exception.class, () -> UpdateStateStore.load(statePath, "stable"));
    }
}
