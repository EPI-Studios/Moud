package com.moud.server.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ProjectLoader {

    public static Path findEntryPoint() {
        Path projectRoot = findProjectRoot();
        return projectRoot.resolve("src/main.js");
    }

    private static Path findProjectRoot() {
        Path current = Paths.get("").toAbsolutePath();

        while (current != null) {
            if (Files.exists(current.resolve("package.json"))) {
                return current;
            }
            current = current.getParent();
        }

        Path exampleDir = Paths.get("example").toAbsolutePath();
        if (Files.exists(exampleDir.resolve("package.json"))) {
            return exampleDir;
        }

        throw new IllegalStateException("No package.json found");
    }
}