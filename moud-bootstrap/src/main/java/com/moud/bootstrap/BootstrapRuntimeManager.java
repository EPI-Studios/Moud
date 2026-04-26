package com.moud.bootstrap;

import com.moud.core.update.ArtifactDownloader;
import com.moud.core.update.GitHubReleaseResolver;
import com.moud.core.update.ReleaseKeys;
import com.moud.core.update.UpdateOrchestrator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class BootstrapRuntimeManager {

    private static final String GITHUB_OWNER = "EPI-Studios";
    private static final String GITHUB_REPO = "Moud";
    private static final String TARGET = "client";

    private final RuntimeJarSynchronizer synchronizer = new RuntimeJarSynchronizer();
    private final BundledRuntimeSeeder bundledRuntimeSeeder = new BundledRuntimeSeeder();

    PreparedRuntime prepare(Path gameDir) throws Exception {
        Path baseDir = BootstrapPaths.clientBaseDir(gameDir);
        Files.createDirectories(baseDir);

        String publicKey = ReleaseKeys.loadDefaultPublicKeyPem();
        GitHubReleaseResolver resolver = new GitHubReleaseResolver(GITHUB_OWNER, GITHUB_REPO, publicKey);
        ArtifactDownloader downloader = new ArtifactDownloader();
        UpdateOrchestrator orchestrator = new UpdateOrchestrator(TARGET, baseDir, resolver, downloader);

        String updateStatus = "Up to date";
        try {
            var check = orchestrator.check(false);
            if (check.updateAvailable()) {
                var result = orchestrator.apply(check.manifest(), (downloaded, total) -> { });
                if (!result.success()) {
                    updateStatus = "Update to " + result.version() + " failed: " + result.error();
                } else {
                    updateStatus = "Updated to " + result.version();
                }
            } else if (check.manifest() == null) {
                updateStatus = "Release manifest unavailable";
            }
        } catch (Exception e) {
            updateStatus = "Update check failed: " + e.getMessage();
        }

        Path engineDir = orchestrator.currentEngineDir();
        if (engineDir == null) {
            BundledRuntimeSeeder.SeedResult seedResult = bundledRuntimeSeeder.seed(baseDir);
            if (!seedResult.success()) {
                throw new IOException("no installed client engine version available: " + seedResult.message());
            }
            engineDir = seedResult.versionDir();
            updateStatus = updateStatus + " | " + seedResult.message();
        }

        Path sourceJar = BootstrapPaths.installedClientJar(engineDir);
        Path runtimeJar = BootstrapPaths.runtimeJar(gameDir);
        RuntimeJarSynchronizer.SyncResult sync = synchronizer.sync(sourceJar, runtimeJar);
        if (sync.status() == RuntimeJarSynchronizer.Status.FAILED) {
            throw new IOException(sync.message());
        }

        String installedVersion = readInstalledVersion(engineDir);
        return new PreparedRuntime(runtimeJar, installedVersion, sync.status(), updateStatus);
    }

    private static String readInstalledVersion(Path engineDir) {
        Path versionFile = BootstrapPaths.installedVersionFile(engineDir);
        if (!Files.isRegularFile(versionFile)) {
            return "";
        }
        try {
            return Files.readString(versionFile).trim();
        } catch (IOException e) {
            return "";
        }
    }

    record PreparedRuntime(Path runtimeJar, String installedVersion,
                           RuntimeJarSynchronizer.Status syncStatus, String updateStatus) {
    }
}
