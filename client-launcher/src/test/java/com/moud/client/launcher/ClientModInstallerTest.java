package com.moud.client.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientModInstallerTest {

    @Test
    void readFabricModIdReturnsModId(@TempDir Path tempDir) throws IOException {
        Path jar = tempDir.resolve("moud-client.jar");
        writeJarWithModId(jar, "moud");

        assertEquals("moud", ClientModInstaller.readFabricModId(jar).orElseThrow());
    }

    @Test
    void findExistingMoudModsFiltersOtherJars(@TempDir Path tempDir) throws IOException {
        Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
        writeJarWithModId(modsDir.resolve("moud-client.jar"), "moud");
        writeJarWithModId(modsDir.resolve("other.jar"), "other");

        List<Path> mods = ClientModInstaller.findExistingMoudMods(modsDir);

        assertEquals(List.of(modsDir.resolve("moud-client.jar")), mods);
    }

    @Test
    void wrappedLaunchCommandFindsGameDirFromArgs(@TempDir Path tempDir) throws IOException {
        Path gameDir = Files.createDirectories(tempDir.resolve("minecraft"));
        Files.createDirectories(gameDir.resolve("mods"));

        WrappedLaunchCommand command = WrappedLaunchCommand.parse(new String[]{
                "/usr/bin/java", "-cp", "cp", "main", "--gameDir", gameDir.toString()
        });

        assertEquals(gameDir.toAbsolutePath().normalize(), command.gameDir());
    }

    @Test
    void installMovesAsideLegacyModAndWritesCanonicalJar(@TempDir Path tempDir) throws IOException {
        Path gameDir = Files.createDirectories(tempDir.resolve("game"));
        Path modsDir = Files.createDirectories(gameDir.resolve("mods"));
        Path baseDir = Files.createDirectories(gameDir.resolve(".moud").resolve("client"));
        Path versionDir = Files.createDirectories(baseDir.resolve("versions").resolve("0.0.2-test").resolve("engine").resolve("mods"))
                .getParent().getParent();

        Path sourceJar = versionDir.resolve("engine").resolve("mods").resolve("moud-client.jar");
        writeJarWithModId(sourceJar, "moud");
        writeJarWithModId(modsDir.resolve("client-fabric-0.0.0-dev.jar"), "moud");

        ClientModInstaller.InstallResult result = ClientModInstaller.install(gameDir, baseDir, versionDir);

        assertEquals(ClientModInstaller.Status.INSTALLED, result.status());
        assertTrue(Files.isRegularFile(modsDir.resolve("moud-client.jar")));
        assertFalse(Files.exists(modsDir.resolve("client-fabric-0.0.0-dev.jar")));
        assertTrue(Files.list(baseDir.resolve("disabled-mods")).findAny().isPresent());
    }

    private static void writeJarWithModId(Path jarPath, String modId) throws IOException {
        Files.createDirectories(jarPath.getParent());
        URI uri = URI.create("jar:" + jarPath.toUri());
        try (FileSystem zipfs = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Path fabricModJson = zipfs.getPath("/fabric.mod.json");
            Files.writeString(fabricModJson, "{\"id\":\"" + modId + "\"}", StandardCharsets.UTF_8);
        }
    }
}
