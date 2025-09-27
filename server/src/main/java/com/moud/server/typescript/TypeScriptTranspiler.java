package com.moud.server.typescript;

import com.moud.server.project.ProjectLoader;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class TypeScriptTranspiler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeScriptTranspiler.class);
    private static final int TIMEOUT_SECONDS = 30;

    public static CompletableFuture<String> transpile(Path tsFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!Files.exists(tsFile)) {
                    throw new IllegalArgumentException("TypeScript file not found: " + tsFile);
                }

                String tsContent = Files.readString(tsFile);

                String npxPath = findNpxExecutable();
                if (npxPath == null) {
                    LOGGER.warn("npx not found in PATH, performing basic TypeScript to JavaScript conversion");
                    return performBasicTranspilation(tsContent);
                }

                return transpileWithEsbuild(tsFile, npxPath);

            } catch (Exception e) {
                throw new RuntimeException("Failed to transpile TypeScript", e);
            }
        });
    }

    private static String findNpxExecutable() {
        String[] possibleCommands = {"npx", "npx.cmd", "npx.exe"};
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        String pathSeparator = System.getProperty("os.name").toLowerCase().startsWith("windows") ? ";" : ":";
        String[] pathDirs = pathEnv.split(pathSeparator);

        for (String dir : pathDirs) {
            for (String cmd : possibleCommands) {
                File executable = new File(dir, cmd);
                if (executable.exists() && executable.canExecute()) {
                    LOGGER.debug("Found npx at: {}", executable.getAbsolutePath());
                    return executable.getAbsolutePath();
                }
            }
        }

        return null;
    }

    private static String transpileWithEsbuild(Path tsFile, String npxPath) throws Exception {
        Path projectRoot = ProjectLoader.findProjectRoot();
        Path tempDir = Files.createTempDirectory("moud-ts");
        Path jsFile = tempDir.resolve(tsFile.getFileName().toString().replace(".ts", ".js"));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        try {
            CommandLine cmdLine = new CommandLine(npxPath);
            cmdLine.addArgument("esbuild");
            cmdLine.addArgument(tsFile.toAbsolutePath().toString(), true);
            cmdLine.addArgument("--outfile=" + jsFile.toAbsolutePath().toString(), true);
            cmdLine.addArgument("--target=es2020");
            cmdLine.addArgument("--format=cjs");
            cmdLine.addArgument("--platform=neutral");

            DefaultExecutor executor = DefaultExecutor.builder().get();
            executor.setWorkingDirectory(projectRoot.toFile());

            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
            executor.setStreamHandler(streamHandler);

            ExecuteWatchdog watchdog = ExecuteWatchdog.builder().setTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).get();
            executor.setWatchdog(watchdog);

            int exitCode = executor.execute(cmdLine);

            if (exitCode != 0) {
                String error = errorStream.toString();
                LOGGER.error("esbuild failed with exit code {}:\n{}", exitCode, error);

                String tsContent = Files.readString(tsFile);
                LOGGER.warn("Falling back to basic transpilation");
                return performBasicTranspilation(tsContent);
            }

            return Files.readString(jsFile);

        } catch (Exception e) {
            LOGGER.warn("esbuild execution failed, falling back to basic transpilation: {}", e.getMessage());
            String tsContent = Files.readString(tsFile);
            return performBasicTranspilation(tsContent);
        } finally {
            try {
                if (Files.exists(jsFile)) Files.deleteIfExists(jsFile);
                Files.deleteIfExists(tempDir);
            } catch (IOException e) {
                LOGGER.warn("Failed to clean up temp files", e);
            }
        }
    }

    private static String performBasicTranspilation(String tsContent) {
        return tsContent
                .replaceAll("\\binterface\\s+\\w+\\s*\\{[^}]*\\}", "")
                .replaceAll("\\btype\\s+\\w+\\s*=.*?;", "")
                .replaceAll("\\b(let|const|var)\\s+(\\w+)\\s*:\\s*[^=;]+", "$1 $2")
                .replaceAll("\\bfunction\\s+(\\w+)\\s*\\([^)]*\\}\\s*:\\s*[^{]+", "function $1()")
                .replaceAll("\\b(\\w+)\\s*:\\s*[^,;)]+", "$1")
                .replaceAll("(?m)^\\s*import\\s+.*?;?$", "")
                .replaceAll("(?m)^\\s*export\\s+.*?;?$", "")
                .replaceAll("/\\*[\\s\\S]*?\\*/", "")
                .replaceAll("//.*$", "")
                .trim();
    }
}