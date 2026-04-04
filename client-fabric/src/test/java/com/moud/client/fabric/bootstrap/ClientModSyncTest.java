package com.moud.client.fabric.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientModSyncTest {

    @Test
    void resolveInstalledJarTargetsPackagedClientJar(@TempDir Path tempDir) {
        Path versionDir = tempDir.resolve("0.0.2-test");

        Path jarPath = ClientModSync.resolveInstalledJar(versionDir);

        assertEquals(versionDir.resolve("engine/mods/moud-client.jar"), jarPath);
    }

    @Test
    void selectJarPathPrefersRegularJarFiles(@TempDir Path tempDir) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("dir"));
        Path jar = Files.writeString(tempDir.resolve("client-fabric.jar"), "jar");
        Path txt = Files.writeString(tempDir.resolve("readme.txt"), "nope");

        Path selected = ClientModSync.selectJarPath(List.of(dir, txt, jar)).orElseThrow();

        assertEquals(jar, selected);
    }

    @Test
    void selectJarPathReturnsEmptyWhenNoJarExists(@TempDir Path tempDir) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("dir"));
        Path txt = Files.writeString(tempDir.resolve("readme.txt"), "nope");

        assertTrue(ClientModSync.selectJarPath(List.of(dir, txt)).isEmpty());
    }
}
