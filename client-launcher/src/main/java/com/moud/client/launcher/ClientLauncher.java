package com.moud.client.launcher;

import com.moud.core.update.ArtifactDownloader;
import com.moud.core.update.GitHubReleaseResolver;
import com.moud.core.update.ReleaseKeys;
import com.moud.core.update.UpdateOrchestrator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ClientLauncher {

    private static final String GITHUB_OWNER = "EPI-Studios";
    private static final String GITHUB_REPO = "Moud";
    private static final String TARGET = "client";
    private static final String MANAGED_PROPERTY = "-Dmoud.client.wrapper=true";

    private ClientLauncher() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            return;
        }

        String[] delegatedArgs = "--wrap".equals(args[0])
                ? java.util.Arrays.copyOfRange(args, 1, args.length)
                : args;

        if (delegatedArgs.length == 0) {
            throw new IllegalArgumentException("wrapped java command is required");
        }

        WrappedLaunchCommand command = WrappedLaunchCommand.parse(delegatedArgs);
        Path gameDir = command.gameDir();
        Path baseDir = gameDir.resolve(".moud").resolve("client");

        log("Client launcher starting, game dir: " + gameDir);

        String installedVersion = "";

        String updateStatus = "";
        try {
            String publicKey = ReleaseKeys.loadDefaultPublicKeyPem();
            GitHubReleaseResolver resolver = new GitHubReleaseResolver(GITHUB_OWNER, GITHUB_REPO, publicKey);
            ArtifactDownloader downloader = new ArtifactDownloader();
            UpdateOrchestrator orchestrator = new UpdateOrchestrator(TARGET, baseDir, resolver, downloader);

            var check = orchestrator.check(false);
            if (check.updateAvailable()) {
                log("Update available: " + check.currentVersion() + " -> " + check.latestVersion());
                ProgressMilestones progress = new ProgressMilestones();
                var result = orchestrator.apply(check.manifest(), progress::log);
                if (result.success()) {
                    log("Update applied: " + result.version());
                } else {
                    updateStatus = "Update to " + result.version() + " failed: " + result.error();
                    log(updateStatus);
                }
            } else {
                log("No update available (current: " + check.currentVersion() + ")");
            }

            Path engineDir = orchestrator.currentEngineDir();
            if (engineDir != null) {
                ClientModInstaller.InstallResult install = ClientModInstaller.install(gameDir, baseDir, engineDir);
                installedVersion = readInstalledVersion(engineDir);
                switch (install.status()) {
                    case INSTALLED -> log("Installed client mod for launch: " + install.targetJar());
                    case UP_TO_DATE -> log("Client mod already up to date: " + install.targetJar());
                    case FAILED -> {
                        updateStatus = "Client mod install failed: " + install.message();
                        log(updateStatus);
                    }
                }
            } else {
                updateStatus = "No installed client version — launching with existing mods";
                log(updateStatus);
            }
        } catch (Exception e) {
            updateStatus = "Launcher update flow failed: " + e.getMessage();
            log(updateStatus);
        }

        List<String> delegatedCommand = new ArrayList<>();
        delegatedCommand.add(command.javaExecutable());
        delegatedCommand.add(MANAGED_PROPERTY);
        if (!installedVersion.isBlank()) {
            delegatedCommand.add("-Dmoud.client.version=" + installedVersion);
        }
        if (!updateStatus.isEmpty()) {
            delegatedCommand.add("-Dmoud.client.updateStatus=" + updateStatus);
        }
        delegatedCommand.addAll(command.javaArguments());

        log("Starting Fabric with managed launch handoff");
        Process process = new ProcessBuilder(delegatedCommand)
                .directory(command.workingDirectory().toFile())
                .inheritIO()
                .start();
        System.exit(process.waitFor());
    }

    private static String readInstalledVersion(Path engineDir) {
        Path versionFile = engineDir.resolve("engine").resolve("VERSION");
        if (!java.nio.file.Files.isRegularFile(versionFile)) {
            return "";
        }
        try {
            return java.nio.file.Files.readString(versionFile).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar moud-client-launcher.jar --wrap <java> [args...]");
    }

    static void log(String message) {
        System.out.println("[moud-launcher] " + message);
    }
}
