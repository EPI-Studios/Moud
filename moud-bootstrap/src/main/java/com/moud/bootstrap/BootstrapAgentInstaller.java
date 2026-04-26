package com.moud.bootstrap;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

final class BootstrapAgentInstaller {

    enum Status {
        ALREADY_ACTIVE,
        INSTALLED,
        UP_TO_DATE,
        UNSUPPORTED_LAUNCHER,
        FAILED
    }

    record InstallationResult(Status status, Path agentJar, LauncherProfileLocator.LauncherProfile profile, String message) {
    }

    private final RuntimeJarSynchronizer jarSynchronizer = new RuntimeJarSynchronizer();
    private final LauncherProfileLocator locator = new LauncherProfileLocator();
    private final LauncherAgentWriter writer = new LauncherAgentWriter();

    InstallationResult installIfNeeded(Path gameDir) {
        if (isRunningAsAgent()) {
            return new InstallationResult(Status.ALREADY_ACTIVE, agentJarPath(gameDir), null, "");
        }

        try {
            Path sourceJar = resolveCurrentBootstrapJar();
            Path agentJar = agentJarPath(gameDir);
            RuntimeJarSynchronizer.SyncResult syncResult = jarSynchronizer.sync(sourceJar, agentJar);
            if (syncResult.status() == RuntimeJarSynchronizer.Status.FAILED) {
                return new InstallationResult(Status.FAILED, agentJar, null, syncResult.message());
            }

            LauncherProfileLocator.LauncherProfile profile = locator.detect(gameDir);
            if (profile.type() == LauncherProfileLocator.LauncherType.UNKNOWN || profile.file() == null) {
                return new InstallationResult(Status.UNSUPPORTED_LAUNCHER, agentJar, profile,
                        "Could not find a supported launcher profile to patch");
            }

            LauncherAgentWriter.WriteResult writeResult = writer.write(profile, agentJar);
            return switch (writeResult) {
                case MODIFIED -> new InstallationResult(Status.INSTALLED, agentJar, profile, profile.type().name());
                case UNCHANGED -> new InstallationResult(Status.UP_TO_DATE, agentJar, profile, profile.type().name());
                case NO_TARGET -> new InstallationResult(Status.UNSUPPORTED_LAUNCHER, agentJar, profile,
                        "Found launcher metadata but could not identify the active profile");
            };
        } catch (Exception e) {
            return new InstallationResult(Status.FAILED, agentJarPath(gameDir), null, e.getMessage());
        }
    }

    static boolean isRunningAsAgent() {
        return Boolean.getBoolean("moud.agent");
    }

    private static Path resolveCurrentBootstrapJar() throws Exception {
        URI location = MoudBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path path = Path.of(location).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IOException("bootstrap is not running from a jar");
        }
        return path;
    }

    private static Path agentJarPath(Path gameDir) {
        return BootstrapPaths.agentJar(gameDir);
    }
}
