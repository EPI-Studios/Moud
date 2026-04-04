package com.moud.supervisor;

import com.moud.core.update.ArtifactDownloader;
import com.moud.core.update.GitHubReleaseResolver;
import com.moud.core.update.ReleaseKeys;
import com.moud.core.update.UpdateOrchestrator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ServerSupervisor {

    private static final String GITHUB_OWNER = "EPI-Studios";
    private static final String GITHUB_REPO = "Moud";
    private static final String TARGET = "server";

    private static final Duration ROLLBACK_WINDOW = Duration.ofSeconds(30);
    private static final int MAX_RAPID_CRASHES = 3;
    private static final long UPDATE_CHECK_INTERVAL_MS = 5 * 60 * 1000;

    public static void main(String[] args) throws Exception {
        Path baseDir = resolveBaseDir(args);
        log("Server supervisor starting, base dir: " + baseDir);

        String publicKey = ReleaseKeys.loadDefaultPublicKeyPem();
        GitHubReleaseResolver resolver = new GitHubReleaseResolver(GITHUB_OWNER, GITHUB_REPO, publicKey);
        ArtifactDownloader downloader = new ArtifactDownloader();
        UpdateOrchestrator orchestrator = new UpdateOrchestrator(TARGET, baseDir, resolver, downloader);

        int rapidCrashes = 0;
        long lastUpdateCheckMs = 0;

        while (true) {
            long now = System.currentTimeMillis();
            if (now - lastUpdateCheckMs >= UPDATE_CHECK_INTERVAL_MS) {
                lastUpdateCheckMs = now;
                try {
                    var check = orchestrator.check(false);
                    if (check.updateAvailable()) {
                        log("Update available: " + check.currentVersion() + " -> " + check.latestVersion());
                        var result = orchestrator.apply(check.manifest(), (downloaded, total) -> {
                            if (total > 0) {
                                int pct = (int) (downloaded * 100 / total);
                                System.out.printf("\r  downloading... %d%%", pct);
                            }
                        });
                        System.out.println();
                        if (result.success()) {
                            log("Update applied: " + result.version());
                            rapidCrashes = 0;
                        } else {
                            log("Update failed: " + result.error());
                        }
                    } else {
                        log("No update available (current: " + check.currentVersion() + ")");
                    }
                } catch (Exception e) {
                    log("Update check failed: " + e.getMessage());
                }
            }

            Path engineDir = orchestrator.currentEngineDir();
            if (engineDir == null) {
                log("No engine version installed. Waiting 30s before retry...");
                lastUpdateCheckMs = 0;
                Thread.sleep(30_000);
                continue;
            }

            log("Launching engine from: " + engineDir);
            Instant launchTime = Instant.now();
            int exitCode = launchEngine(engineDir);
            Duration runTime = Duration.between(launchTime, Instant.now());

            log("Engine exited with code " + exitCode + " after " + runTime.toSeconds() + "s");

            if (exitCode != 0 && runTime.compareTo(ROLLBACK_WINDOW) < 0) {
                rapidCrashes++;
                log("Rapid crash detected (" + rapidCrashes + "/" + MAX_RAPID_CRASHES + ")");

                if (rapidCrashes >= MAX_RAPID_CRASHES) {
                    log("Too many rapid crashes, attempting rollback...");
                    if (orchestrator.rollback()) {
                        log("Rollback successful");
                        rapidCrashes = 0;
                    } else {
                        log("Rollback failed — no previous version available");
                        log("Waiting 60s before retry...");
                        Thread.sleep(60_000);
                    }
                }
            } else {
                rapidCrashes = 0;
            }

            lastUpdateCheckMs = 0;
            Thread.sleep(2_000);
        }
    }

    private static int launchEngine(Path engineDir) throws IOException, InterruptedException {
        Path serverJar = findServerJar(engineDir);
        if (serverJar == null) {
            log("No server jar found in " + engineDir);
            return 1;
        }

        List<String> command = new ArrayList<>();
        command.add(ProcessHandle.current().info().command().orElse("java"));
        command.add("-jar");
        command.add(serverJar.toAbsolutePath().toString());

        String engineJvmArgs = System.getenv("MOUD_ENGINE_JVM_ARGS");
        if (engineJvmArgs != null && !engineJvmArgs.isBlank()) {
            for (String arg : engineJvmArgs.split("\\s+")) {
                if (!arg.isBlank()) command.add(2, arg);
            }
        }

        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(engineDir.toFile())
                .inheritIO();

        pb.environment().put("MOUD_ENGINE_DIR", engineDir.toAbsolutePath().toString());

        Process process = pb.start();
        int exitCode = process.waitFor();
        if (process.isAlive()) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        return exitCode;
    }

    private static Path findServerJar(Path engineDir) throws IOException {
        Path direct = engineDir.resolve("engine").resolve("moud-server.jar");
        if (Files.isRegularFile(direct)) return direct;

        Path flat = engineDir.resolve("moud-server.jar");
        if (Files.isRegularFile(flat)) return flat;

        Path engineSubDir = engineDir.resolve("engine");
        Path searchDir = Files.isDirectory(engineSubDir) ? engineSubDir : engineDir;
        try (var stream = Files.list(searchDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Path resolveBaseDir(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Path.of(args[0]).toAbsolutePath();
        }
        String env = System.getenv("MOUD_SERVER_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env).toAbsolutePath();
        }
        return Path.of(".").toAbsolutePath().resolve("moud-server");
    }

    private static void log(String message) {
        System.out.println("[supervisor] " + message);
    }
}
