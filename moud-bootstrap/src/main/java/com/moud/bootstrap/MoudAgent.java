package com.moud.bootstrap;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.StringJoiner;

public final class MoudAgent {

    private MoudAgent() {}

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        System.setProperty("moud.agent", "true");
        agentJar().ifPresent(path -> System.setProperty("moud.agent.jar", path.toString()));
        runtimeJar().ifPresent(MoudAgent::addRuntimeMod);
    }

    private static Optional<Path> agentJar() {
        try {
            URI location = MoudAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<Path> runtimeJar() {
        try {
            Path gameDir = Path.of(".").toAbsolutePath().normalize();
            Path jar = BootstrapPaths.currentInstalledClientJar(gameDir);
            if (jar == null || !Files.isRegularFile(jar)) return Optional.empty();
            return Optional.of(jar);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static void addRuntimeMod(Path jar) {
        String jarPath = jar.toAbsolutePath().normalize().toString();
        String existing = System.getProperty("fabric.addMods", "").trim();

        if (!existing.isBlank()) {
            for (String entry : existing.split(File.pathSeparator)) {
                if (entry.equals(jarPath)) return;
            }
        }

        StringJoiner joiner = new StringJoiner(File.pathSeparator);
        if (!existing.isBlank()) {
            for (String entry : existing.split(File.pathSeparator)) {
                if (!entry.isBlank()) joiner.add(entry);
            }
        }
        joiner.add(jarPath);
        System.setProperty("fabric.addMods", joiner.toString());
    }
}