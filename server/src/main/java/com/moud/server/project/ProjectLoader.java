package com.moud.server.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class ProjectLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Path findEntryPoint() throws IOException {
        Path projectRoot = findProjectRoot();
        PackageInfo packageInfo = loadPackageInfo(projectRoot);
        return projectRoot.resolve(packageInfo.getMoudMain());
    }

    public static boolean isTypeScriptProject() throws IOException {
        Path entryPoint = findEntryPoint();
        return entryPoint.toString().endsWith(".ts");
    }

    public static Path findProjectRoot() {
        // NEED REMOVAL LATER
        Path rootDir = Paths.get("").toAbsolutePath();

        if (rootDir.getFileName().toString().equals("server")) {
            rootDir = rootDir.getParent();
        }

        Path exampleTsDir = rootDir.resolve("example/ts");

        if (Files.exists(exampleTsDir.resolve("package.json"))) {
            return exampleTsDir;
        }

        Path exampleJsDir = rootDir.resolve("example/js");
        if (Files.exists(exampleJsDir.resolve("package.json"))) {
            return exampleJsDir;
        }

        Path exampleDir = rootDir.resolve("example");
        if (Files.exists(exampleDir.resolve("package.json"))) {
            return exampleDir;
        }

        Path current = rootDir;
        while (current != null) {
            if (Files.exists(current.resolve("package.json"))) {
                return current;
            }
            current = current.getParent();
        }

        throw new IllegalStateException("No package.json found");
    }

    private static PackageInfo loadPackageInfo(Path projectRoot) throws IOException {
        Path packageFile = projectRoot.resolve("package.json");
        return MAPPER.readValue(packageFile.toFile(), PackageInfo.class);
    }

    public static Optional<Path> resolveProjectRoot(String[] launchArgs) {
        for (int i = 0; i < launchArgs.length - 1; i++) {
            if ("--project-root".equalsIgnoreCase(launchArgs[i])) {
                Path projectPath = Paths.get(launchArgs[i + 1]);
                if (Files.isDirectory(projectPath) && Files.exists(projectPath.resolve("package.json"))) {
                    return Optional.of(projectPath);
                }
            }
        }
        return Optional.of(findProjectRoot());
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackageInfo {
        @JsonProperty("moud:main")
        private String moudMain;

        public String getMoudMain() {
            return moudMain != null ? moudMain : "src/main.js";
        }
    }
}