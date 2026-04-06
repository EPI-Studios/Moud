package com.moud.client.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ClientModInstaller {

    private static final String MOD_ID = "moud";
    private static final String CANONICAL_MOD_FILE = "moud-client.jar";
    private static final String ENGINE_JAR_RELATIVE_PATH = "engine/mods/moud-client.jar";

    private ClientModInstaller() {}

    static InstallResult install(Path gameDir, Path baseDir, Path versionDir) {
        Path sourceJar = versionDir.resolve(ENGINE_JAR_RELATIVE_PATH);
        if (!Files.isRegularFile(sourceJar)) {
            return new InstallResult(Status.FAILED, sourceJar, "installed engine jar is missing");
        }

        Path modsDir = gameDir.resolve("mods");
        Path targetJar = modsDir.resolve(CANONICAL_MOD_FILE);
        Path disabledDir = baseDir.resolve("disabled-mods");

        try {
            Files.createDirectories(modsDir);
            Files.createDirectories(disabledDir);

            List<Path> existingMoudMods = findExistingMoudMods(modsDir);
            for (Path existing : existingMoudMods) {
                if (existing.equals(targetJar)) {
                    continue;
                }
                moveAside(existing, disabledDir);
            }

            if (Files.isRegularFile(targetJar)) {
                Optional<String> modId = readFabricModId(targetJar);
                if (modId.isPresent() && !MOD_ID.equals(modId.get()) && Files.mismatch(sourceJar, targetJar) != -1L) {
                    return new InstallResult(Status.FAILED, targetJar, "target jar is occupied by a different mod");
                }
            }

            if (Files.isRegularFile(targetJar) && Files.mismatch(sourceJar, targetJar) == -1L) {
                return new InstallResult(Status.UP_TO_DATE, targetJar, null);
            }

            Path tempJar = targetJar.resolveSibling(targetJar.getFileName() + ".tmp");
            try {
                Files.copy(sourceJar, tempJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                try {
                    Files.move(tempJar, targetJar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempJar);
            }

            return new InstallResult(Status.INSTALLED, targetJar, null);
        } catch (IOException e) {
            return new InstallResult(Status.FAILED, targetJar, e.getMessage());
        }
    }

    static List<Path> findExistingMoudMods(Path modsDir) throws IOException {
        if (!Files.isDirectory(modsDir)) {
            return List.of();
        }

        try (var stream = Files.list(modsDir)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> MOD_ID.equals(readFabricModId(path).orElse(null)))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    static Optional<String> readFabricModId(Path jarPath) {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                return Optional.empty();
            }
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!json.has("id")) {
                    return Optional.empty();
                }
                return Optional.ofNullable(json.get("id").getAsString());
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static void moveAside(Path source, Path disabledDir) throws IOException {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(java.time.ZoneOffset.UTC)
                .format(Instant.now());
        Path target = disabledDir.resolve(timestamp + "-" + source.getFileName());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    enum Status {
        INSTALLED,
        UP_TO_DATE,
        FAILED
    }

    record InstallResult(Status status, Path targetJar, String message) {}
}
