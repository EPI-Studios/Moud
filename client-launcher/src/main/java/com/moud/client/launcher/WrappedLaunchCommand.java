package com.moud.client.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

record WrappedLaunchCommand(String javaExecutable, List<String> javaArguments, Path workingDirectory, Path gameDir) {

    static WrappedLaunchCommand parse(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("java executable is required");
        }

        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path gameDir = resolveGameDir(args, workingDirectory);
        List<String> javaArguments = new ArrayList<>();
        javaArguments.addAll(List.of(args).subList(1, args.length));
        return new WrappedLaunchCommand(args[0], javaArguments, workingDirectory, gameDir);
    }

    private static Path resolveGameDir(String[] args, Path workingDirectory) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--gameDir".equals(args[i]) && !args[i + 1].isBlank()) {
                return Path.of(args[i + 1]).toAbsolutePath().normalize();
            }
        }

        if (looksLikeGameDir(workingDirectory)) {
            return workingDirectory;
        }

        Path nested = workingDirectory.resolve("minecraft");
        if (looksLikeGameDir(nested)) {
            return nested.toAbsolutePath().normalize();
        }

        throw new IllegalArgumentException("unable to resolve --gameDir from wrapped launch command");
    }

    private static boolean looksLikeGameDir(Path dir) {
        return Files.isDirectory(dir)
                && (Files.isDirectory(dir.resolve("mods"))
                || Files.isRegularFile(dir.resolve("options.txt"))
                || Files.isDirectory(dir.resolve("logs")));
    }
}
