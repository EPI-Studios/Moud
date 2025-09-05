package com.moud.server.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    private static Path findProjectRoot() {
        Path current = Paths.get("").toAbsolutePath();

        while (current != null) {
            if (Files.exists(current.resolve("package.json"))) {
                return current;
            }
            current = current.getParent();
        }

        Path exampleTsDir = Paths.get("example/ts").toAbsolutePath();
        if (Files.exists(exampleTsDir.resolve("package.json"))) {
            return exampleTsDir;
        }

        Path exampleJsDir = Paths.get("example/js").toAbsolutePath();
        if (Files.exists(exampleJsDir.resolve("package.json"))) {
            return exampleJsDir;
        }

        Path exampleDir = Paths.get("example").toAbsolutePath();
        if (Files.exists(exampleDir.resolve("package.json"))) {
            return exampleDir;
        }

        throw new IllegalStateException("No package.json found");
    }

    private static PackageInfo loadPackageInfo(Path projectRoot) throws IOException {
        Path packageFile = projectRoot.resolve("package.json");
        return MAPPER.readValue(packageFile.toFile(), PackageInfo.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackageInfo {
        @JsonProperty("moud:main")
        private String moudMain = "src/main.js";

        public String getMoudMain() {
            return moudMain;
        }
    }
}