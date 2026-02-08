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
        Path declaredEntry = projectRoot.resolve(packageInfo.getMoudMain()).normalize();

        Path cacheDir = projectRoot.resolve(".moud/cache");
        Path manifestPath = cacheDir.resolve("manifest.json");
        Path compiledBundle = cacheDir.resolve("server.bundle.js");

        if (Files.exists(manifestPath) && Files.exists(compiledBundle)) {
            CacheManifest manifest = MAPPER.readValue(manifestPath.toFile(), CacheManifest.class);
            Path manifestEntry = projectRoot.resolve(manifest.entryPoint()).normalize();
            if (manifestEntry.equals(declaredEntry)) {
                return compiledBundle;
            }
        }

        return declaredEntry;
    }

    public static boolean isTypeScriptProject() throws IOException {
        Path entryPoint = findEntryPoint();
        return entryPoint.toString().endsWith(".ts");
    }

    public static Path findProjectRoot() {
        Path current = Paths.get("").toAbsolutePath();

        if (current.getFileName() != null && current.getFileName().toString().equals("server")) {
            current = current.getParent();
        }

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
                Path projectPath = Paths.get(launchArgs[i + 1]).toAbsolutePath().normalize();
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CacheManifest(
            @JsonProperty("hash") String hash,
            @JsonProperty("entryPoint") String entryPoint,
            @JsonProperty("generatedAt") String generatedAt
    ) {
    }
}
