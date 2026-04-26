package com.moud.bootstrap;

import com.moud.core.update.UpdateState;
import com.moud.core.update.UpdateStateStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;

final class BundledRuntimeSeeder {

    private static final String RUNTIME_JAR_RESOURCE = "/bootstrap-runtime/moud-client-runtime.jar";
    private static final String VERSION_RESOURCE = "/bootstrap-runtime/VERSION";
    private static final String RELEASE_TAG_RESOURCE = "/bootstrap-runtime/RELEASE_TAG";

    interface ResourceSource {
        InputStream open(String resourcePath) throws IOException;
    }

    enum Status {
        INSTALLED,
        MISSING_RESOURCE,
        FAILED
    }

    record SeedResult(Status status, String version, Path versionDir, String message) {
        boolean success() {
            return status == Status.INSTALLED;
        }
    }

    private final ResourceSource resourceSource;

    BundledRuntimeSeeder() {
        this(resourcePath -> BundledRuntimeSeeder.class.getResourceAsStream(resourcePath));
    }

    BundledRuntimeSeeder(ResourceSource resourceSource) {
        this.resourceSource = resourceSource;
    }

    SeedResult seed(Path clientBaseDir) {
        try {
            String version = readTextResource(VERSION_RESOURCE);
            if (version.isBlank()) {
                version = "bundled";
            }
            String releaseTag = readTextResource(RELEASE_TAG_RESOURCE);
            if (releaseTag.isBlank()) {
                releaseTag = version;
            }

            Path versionDir = BootstrapPaths.versionDir(clientBaseDir, version);
            Path installedJar = BootstrapPaths.installedClientJar(versionDir);
            Path tempJar = installedJar.resolveSibling(installedJar.getFileName() + ".tmp");

            try (InputStream jarIn = resourceSource.open(RUNTIME_JAR_RESOURCE)) {
                if (jarIn == null) {
                    return new SeedResult(Status.MISSING_RESOURCE, version, null, "bundled runtime jar resource missing");
                }

                Files.createDirectories(installedJar.getParent());
                Files.copy(jarIn, tempJar, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(tempJar, installedJar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempJar, installedJar, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(tempJar);
                }
            }

            Files.writeString(BootstrapPaths.installedVersionFile(versionDir), version + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.writeString(versionDir.resolve("engine").resolve("RELEASE_TAG"), releaseTag + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.writeString(BootstrapPaths.currentPointer(clientBaseDir), version + System.lineSeparator(), StandardCharsets.UTF_8);

            UpdateState oldState = UpdateStateStore.load(BootstrapPaths.updateStatePath(clientBaseDir), "stable");
            var installedVersions = new HashMap<>(oldState.installedVersions());
            installedVersions.put(version, versionDir.toString());
            UpdateStateStore.save(BootstrapPaths.updateStatePath(clientBaseDir), new UpdateState(
                    1,
                    oldState.channel(),
                    version,
                    version,
                    version,
                    installedVersions,
                    oldState.failedVersions()
            ));

            return new SeedResult(Status.INSTALLED, version, versionDir, "bundled runtime installed");
        } catch (IOException e) {
            return new SeedResult(Status.FAILED, "", null, e.getMessage());
        }
    }

    private String readTextResource(String resourcePath) throws IOException {
        try (InputStream in = resourceSource.open(resourcePath)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
