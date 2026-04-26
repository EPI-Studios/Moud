package com.moud.bootstrap;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;

final class BootstrapPaths {

    private BootstrapPaths() {
    }

    static Path clientBaseDir(Path gameDir) {
        return gameDir.resolve(".moud").resolve("client");
    }

    static Path runtimeDir(Path gameDir) {
        return clientBaseDir(gameDir).resolve("runtime");
    }

    static Path versionsDir(Path clientBaseDir) {
        return clientBaseDir.resolve("versions");
    }

    static Path versionDir(Path clientBaseDir, String version) {
        return versionsDir(clientBaseDir).resolve(version);
    }

    static Path updateStatePath(Path clientBaseDir) {
        return clientBaseDir.resolve("update-state.json");
    }

    static Path currentPointer(Path clientBaseDir) {
        return clientBaseDir.resolve("current");
    }

    static Path currentVersionDir(Path clientBaseDir) throws IOException {
        Path pointer = currentPointer(clientBaseDir);
        if (!Files.isRegularFile(pointer)) {
            return null;
        }
        String version = Files.readString(pointer).trim();
        if (version.isBlank()) {
            return null;
        }
        Path versionDir = versionDir(clientBaseDir, version);
        return Files.isDirectory(versionDir) ? versionDir : null;
    }

    static Path currentInstalledClientJar(Path gameDir) throws IOException {
        Path versionDir = currentVersionDir(clientBaseDir(gameDir));
        if (versionDir == null) {
            return null;
        }
        Path jar = installedClientJar(versionDir);
        return Files.isRegularFile(jar) ? jar : null;
    }

    static Path agentDir(Path gameDir) {
        return clientBaseDir(gameDir).resolve("agent");
    }

    static Path agentJar(Path gameDir) {
        return agentDir(gameDir).resolve("moud-agent.jar");
    }

    static Path runtimeJar(Path gameDir) {
        return runtimeDir(gameDir).resolve("moud-client-runtime.jar");
    }

    static Path installedClientJar(Path engineDir) {
        return engineDir.resolve("engine").resolve("mods").resolve("moud-client.jar");
    }

    static Path installedVersionFile(Path engineDir) {
        return engineDir.resolve("engine").resolve("VERSION");
    }
}
