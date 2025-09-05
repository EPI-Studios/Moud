package com.moud.server.typescript;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
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

                if (!isNodeAvailable()) {
                    throw new RuntimeException("Node.js not found in PATH");
                }

                Path tempDir = Files.createTempDirectory("moud-ts");
                Path jsFile = tempDir.resolve(tsFile.getFileName().toString().replace(".ts", ".js"));

                String esbuildCommand = buildEsbuildCommand(tsFile, jsFile);

                CommandLine cmdLine = CommandLine.parse(esbuildCommand);
                DefaultExecutor executor = DefaultExecutor.builder().get();

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

                PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
                executor.setStreamHandler(streamHandler);

                ExecuteWatchdog watchdog = ExecuteWatchdog.builder().setTimeout(Duration.ofDays(TIMEOUT_SECONDS * 1000L)).get();
                executor.setWatchdog(watchdog);

                int exitCode = executor.execute(cmdLine);

                if (exitCode != 0) {
                    String error = errorStream.toString();
                    throw new RuntimeException("TypeScript transpilation failed: " + error);
                }

                String transpiledCode = Files.readString(jsFile);

                Files.deleteIfExists(jsFile);
                Files.deleteIfExists(tempDir);

                return transpiledCode;

            } catch (Exception e) {
                throw new RuntimeException("Failed to transpile TypeScript", e);
            }
        });
    }

    private static boolean isNodeAvailable() {
        try {
            CommandLine cmdLine = CommandLine.parse("node --version");
            DefaultExecutor executor = DefaultExecutor.builder().get();
            return executor.execute(cmdLine) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildEsbuildCommand(Path inputFile, Path outputFile) {
        return String.format(
                "npx esbuild %s --outfile=%s --target=es2020 --format=cjs --sourcemap=inline",
                inputFile.toAbsolutePath(),
                outputFile.toAbsolutePath()
        );
    }
}