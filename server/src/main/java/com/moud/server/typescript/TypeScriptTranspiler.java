package com.moud.server.typescript;

import com.moud.server.project.ProjectLoader;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class TypeScriptTranspiler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeScriptTranspiler.class);
    private static final int TIMEOUT_SECONDS = 30;

    private TypeScriptTranspiler() {
    }

    public static CompletableFuture<String> transpile(Path tsFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!Files.exists(tsFile)) {
                    throw new IllegalArgumentException("TypeScript file not found: " + tsFile);
                }

                String npxPath = findNpxExecutable();
                if (npxPath != null) {
                    return transpileWithEsbuild(tsFile, npxPath);
                }

                Path cachedBundle = resolveCachedBundle();
                if (cachedBundle != null && Files.exists(cachedBundle)) {
                    LOGGER.info("Using cached server bundle from {}", cachedBundle);
                    return Files.readString(cachedBundle, StandardCharsets.UTF_8);
                }

                throw new IllegalStateException(
                        "Unable to locate npx/esbuild and no cached bundle was found. " +
                        "Install Node.js (>=18) so the CLI can transpile, or run `moud dev` to generate cached artifacts."
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to transpile TypeScript", e);
            }
        });
    }

    private static Path resolveCachedBundle() {
        try {
            Path projectRoot = ProjectLoader.findProjectRoot();
            Path bundle = projectRoot.resolve(".moud/cache/server.bundle.js");
            if (Files.exists(bundle)) {
                return bundle;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String findNpxExecutable() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String[] possibleCommands = isWindows
                ? new String[]{"npx.cmd", "npx.exe", "npx"}
                : new String[]{"npx", "npx.cmd", "npx.exe"};
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null || pathEnv.isEmpty()) {
            return null;
        }

        String separator = File.pathSeparator;
        String[] pathDirs = pathEnv.split(separator);

        for (String dir : pathDirs) {
            for (String cmd : possibleCommands) {
                File executable = new File(dir, cmd);
                if (isWindows && "npx".equalsIgnoreCase(cmd)) {
                    continue;
                }
                if (executable.exists() && executable.canExecute()) {
                    LOGGER.debug("Found npx at {}", executable.getAbsolutePath());
                    return executable.getAbsolutePath();
                }
            }
        }

        return null;
    }

    private static String transpileWithEsbuild(Path tsFile, String npxPath) throws Exception {
        Path projectRoot = ProjectLoader.findProjectRoot();
        Path tempDir = Files.createTempDirectory("moud-ts");
        Path jsFile = tempDir.resolve(tsFile.getFileName().toString().replaceFirst("\\.tsx?$", ".js"));

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try {
            CommandLine cmdLine = new CommandLine(npxPath);
            cmdLine.addArgument("esbuild");
            cmdLine.addArgument(tsFile.toAbsolutePath().toString(), true);
            cmdLine.addArgument("--outfile=" + jsFile.toAbsolutePath(), true);
            cmdLine.addArgument("--bundle");
            cmdLine.addArgument("--target=es2020");
            cmdLine.addArgument("--format=esm");
            cmdLine.addArgument("--platform=node");

            DefaultExecutor executor = DefaultExecutor.builder().get();
            executor.setWorkingDirectory(projectRoot.toFile());
            executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));

            ExecuteWatchdog watchdog = ExecuteWatchdog.builder()
                    .setTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .get();
            executor.setWatchdog(watchdog);

            int exitCode = executor.execute(cmdLine);
            if (exitCode != 0) {
                String error = stderr.toString(StandardCharsets.UTF_8);
                LOGGER.error("esbuild failed with exit code {}: {}", exitCode, error);
                Path cachedBundle = resolveCachedBundle();
                if (cachedBundle != null && Files.exists(cachedBundle)) {
                    LOGGER.warn("Falling back to cached bundle {}", cachedBundle);
                    return Files.readString(cachedBundle, StandardCharsets.UTF_8);
                }
                throw new IllegalStateException("esbuild failed and no cached bundle is available");
            }

            return Files.readString(jsFile, StandardCharsets.UTF_8);
        } finally {
            try {
                Files.deleteIfExists(jsFile);
                Files.deleteIfExists(tempDir);
            } catch (IOException cleanupError) {
                LOGGER.debug("Failed to clean up temporary transpilation artifacts", cleanupError);
            }
        }
    }
}
