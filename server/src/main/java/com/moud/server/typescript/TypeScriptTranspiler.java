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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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
                    LOGGER.warn("npx not found in PATH, performing enhanced TypeScript to JavaScript conversion");
                    return performEnhancedTranspilation(tsContent);
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
                LOGGER.warn("Falling back to enhanced transpilation");
                return performEnhancedTranspilation(tsContent);
            }

            return Files.readString(jsFile);

        } catch (Exception e) {
            LOGGER.warn("esbuild execution failed, falling back to enhanced transpilation: {}", e.getMessage());
            String tsContent = Files.readString(tsFile);
            return performEnhancedTranspilation(tsContent);
        } finally {
            try {
                if (Files.exists(jsFile)) Files.deleteIfExists(jsFile);
                Files.deleteIfExists(tempDir);
            } catch (IOException e) {
                LOGGER.warn("Failed to clean up temp files", e);
            }
        }
    }

    private static String performEnhancedTranspilation(String tsContent) {
        String result = tsContent;

        result = removeComments(result);
        result = handleImportsExports(result);
        result = handleInterfaces(result);
        result = handleTypeAliases(result);
        result = handleEnums(result);
        result = handleFunctionTypes(result);
        result = handleVariableTypes(result);
        result = handleClassTypes(result);
        result = handleGenerics(result);
        result = handleOptionalChaining(result);
        result = handleNullishCoalescing(result);
        result = handleAsKeyword(result);
        result = handleAccessModifiers(result);
        result = handleDecorators(result);

        return result.trim();
    }

    private static String removeComments(String content) {
        content = content.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        content = content.replaceAll("//.*$", "");
        return content;
    }

    private static String handleImportsExports(String content) {
        content = content.replaceAll("(?m)^\\s*import\\s+.*?from\\s+['\"][^'\"]+['\"]\\s*;?\\s*$", "");
        content = content.replaceAll("(?m)^\\s*import\\s+['\"][^'\"]+['\"]\\s*;?\\s*$", "");
        content = content.replaceAll("(?m)^\\s*export\\s+default\\s+", "");
        content = content.replaceAll("(?m)^\\s*export\\s+", "");
        return content;
    }

    private static String handleInterfaces(String content) {
        Pattern interfacePattern = Pattern.compile("interface\\s+(\\w+)\\s*(?:<[^>]*>)?\\s*(?:extends\\s+[^{]+)?\\s*\\{[^}]*\\}", Pattern.DOTALL);
        return interfacePattern.matcher(content).replaceAll("");
    }

    private static String handleTypeAliases(String content) {
        Pattern typePattern = Pattern.compile("type\\s+(\\w+)\\s*(?:<[^>]*>)?\\s*=\\s*[^;]+;", Pattern.DOTALL);
        return typePattern.matcher(content).replaceAll("");
    }

    private static String handleEnums(String content) {
        Pattern enumPattern = Pattern.compile("enum\\s+(\\w+)\\s*\\{([^}]*)\\}", Pattern.DOTALL);
        Matcher matcher = enumPattern.matcher(content);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String enumName = matcher.group(1);
            String enumBody = matcher.group(2);

            StringBuilder enumObject = new StringBuilder();
            enumObject.append("const ").append(enumName).append(" = {\n");

            String[] members = enumBody.split(",");
            int value = 0;
            for (String member : members) {
                member = member.trim();
                if (member.isEmpty()) continue;

                if (member.contains("=")) {
                    String[] parts = member.split("=", 2);
                    String key = parts[0].trim();
                    String val = parts[1].trim();
                    enumObject.append("  ").append(key).append(": ").append(val).append(",\n");
                    try {
                        value = Integer.parseInt(val) + 1;
                    } catch (NumberFormatException e) {
                        value++;
                    }
                } else {
                    enumObject.append("  ").append(member).append(": ").append(value).append(",\n");
                    value++;
                }
            }
            enumObject.append("};");

            matcher.appendReplacement(result, enumObject.toString());
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String handleFunctionTypes(String content) {
        Pattern functionTypePattern = Pattern.compile("function\\s+(\\w+)\\s*\\([^)]*\\)\\s*:\\s*[^{;]+(?=\\s*[{;])");
        Matcher matcher = functionTypePattern.matcher(content);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String match = matcher.group();
            String replacement = match.replaceAll(":\\s*[^{;]+(?=\\s*[{;])", "");
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String handleVariableTypes(String content) {
        Pattern varTypePattern = Pattern.compile("\\b(let|const|var)\\s+(\\w+)\\s*:\\s*[^=;]+(?=\\s*[=;])");
        return varTypePattern.matcher(content).replaceAll("$1 $2");
    }

    private static String handleClassTypes(String content) {
        Pattern classMethodPattern = Pattern.compile("(\\w+)\\s*\\([^)]*\\)\\s*:\\s*[^{;]+(?=\\s*[{;])");
        Matcher matcher = classMethodPattern.matcher(content);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String match = matcher.group();
            String replacement = match.replaceAll(":\\s*[^{;]+(?=\\s*[{;])", "");
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);

        Pattern propertyPattern = Pattern.compile("(\\w+)\\s*:\\s*[^=;,}]+(?=\\s*[=;,}])");
        return propertyPattern.matcher(result.toString()).replaceAll("$1");
    }

    private static String handleGenerics(String content) {
        Pattern genericPattern = Pattern.compile("<[^<>]*>");
        return genericPattern.matcher(content).replaceAll("");
    }

    private static String handleOptionalChaining(String content) {
        return content.replaceAll("\\?\\.(?!\\s*\\()", "&&");
    }

    private static String handleNullishCoalescing(String content) {
        return content.replaceAll("\\?\\?", "||");
    }

    private static String handleAsKeyword(String content) {
        Pattern asPattern = Pattern.compile("\\s+as\\s+\\w+(?:<[^>]*>)?");
        return asPattern.matcher(content).replaceAll("");
    }

    private static String handleAccessModifiers(String content) {
        Pattern modifierPattern = Pattern.compile("\\b(public|private|protected|readonly|static)\\s+");
        return modifierPattern.matcher(content).replaceAll("");
    }

    private static String handleDecorators(String content) {
        Pattern decoratorPattern = Pattern.compile("@\\w+(?:\\([^)]*\\))?\\s*");
        return decoratorPattern.matcher(content).replaceAll("");
    }
}