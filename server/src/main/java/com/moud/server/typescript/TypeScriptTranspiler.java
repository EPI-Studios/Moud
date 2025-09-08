package com.moud.server.typescript;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
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

                if (!isNodeAvailable()) {
                    LOGGER.warn("Node.js not available, performing basic TypeScript to JavaScript conversion");
                    return performBasicTranspilation(tsContent);
                }

                return transpileWithEsbuild(tsFile);

            } catch (Exception e) {
                LOGGER.error("Failed to transpile TypeScript file: {}", tsFile, e);
                throw new RuntimeException("Failed to transpile TypeScript", e);
            }
        });
    }

    private static String transpileWithEsbuild(Path tsFile) throws Exception {
        Path tempDir = Files.createTempDirectory("moud-ts");
        Path jsFile = tempDir.resolve(tsFile.getFileName().toString().replace(".ts", ".js"));

        try {
            CommandLine cmdLine = CommandLine.parse("npx");
            cmdLine.addArgument("esbuild");
            cmdLine.addArgument(tsFile.toAbsolutePath().toString());
            cmdLine.addArgument("--outfile=" + jsFile.toAbsolutePath().toString());
            cmdLine.addArgument("--target=es2020");
            cmdLine.addArgument("--format=cjs");
            cmdLine.addArgument("--platform=neutral");

            DefaultExecutor executor = DefaultExecutor.builder().get();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
            executor.setStreamHandler(streamHandler);

            ExecuteWatchdog watchdog = ExecuteWatchdog.builder().setTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).get();
            executor.setWatchdog(watchdog);

            int exitCode = executor.execute(cmdLine);

            if (exitCode != 0) {
                String error = errorStream.toString();
                LOGGER.error("esbuild failed with exit code {}: {}", exitCode, error);

                String tsContent = Files.readString(tsFile);
                LOGGER.warn("Falling back to basic transpilation");
                return performBasicTranspilation(tsContent);
            }

            String transpiledCode = Files.readString(jsFile);
            return transpiledCode;

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
                .replaceAll("\\bfunction\\s+(\\w+)\\s*\\([^)]*\\)\\s*:\\s*[^{]+", "function $1()")
                .replaceAll("\\b(\\w+)\\s*:\\s*[^,;)]+", "$1")
                .replaceAll("(?m)^\\s*import\\s+.*?;?$", "")
                .replaceAll("(?m)^\\s*export\\s+.*?;?$", "")
                .replaceAll("/\\*[\\s\\S]*?\\*/", "")
                .replaceAll("//.*$", "")
                .trim();
    }

    private static boolean isNodeAvailable() {
        try {
            CommandLine cmdLine = CommandLine.parse("node --version");
            DefaultExecutor executor = DefaultExecutor.builder().get();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, outputStream);
            executor.setStreamHandler(streamHandler);

            int exitCode = executor.execute(cmdLine);
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}