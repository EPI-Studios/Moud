package com.moud.client.fabric.bootstrap;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class ClientModSync {

    private static final String MOD_ID = "moud";
    private static final String ENGINE_JAR_RELATIVE_PATH = "engine/mods/moud-client.jar";

    private ClientModSync() {}

    static SyncResult syncInstalledVersion(Path versionDir) {
        Path sourceJar = resolveInstalledJar(versionDir);
        if (!Files.isRegularFile(sourceJar)) {
            return new SyncResult(Status.SKIPPED, sourceJar, null, "installed engine jar is missing");
        }

        Optional<Path> targetJar = resolveLoadedModJar();
        if (targetJar.isEmpty()) {
            return new SyncResult(Status.SKIPPED, sourceJar, null, "could not resolve loaded mod jar");
        }

        Path target = targetJar.get();
        try {
            if (Files.isSameFile(sourceJar, target) || Files.mismatch(sourceJar, target) == -1L) {
                return new SyncResult(Status.UP_TO_DATE, sourceJar, target, null);
            }

            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            try {
                Files.copy(sourceJar, temp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }

            return new SyncResult(Status.SYNCED, sourceJar, target, null);
        } catch (IOException e) {
            return new SyncResult(Status.FAILED, sourceJar, target, e.getMessage());
        }
    }

    static Path resolveInstalledJar(Path versionDir) {
        return versionDir.resolve(ENGINE_JAR_RELATIVE_PATH);
    }

    static Optional<Path> selectJarPath(List<Path> paths) {
        return paths.stream()
                .filter(path -> path != null && Files.isRegularFile(path))
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .sorted(Comparator.comparingInt(Path::getNameCount))
                .findFirst();
    }

    private static Optional<Path> resolveLoadedModJar() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(ModContainer::getOrigin)
                .map(origin -> selectJarPath(origin.getPaths()))
                .flatMap(optional -> optional)
                .map(path -> path.toAbsolutePath().normalize());
    }

    enum Status {
        UP_TO_DATE,
        SYNCED,
        SKIPPED,
        FAILED
    }

    record SyncResult(Status status, Path sourceJar, Path targetJar, String message) {}
}
