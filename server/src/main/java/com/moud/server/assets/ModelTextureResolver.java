package com.moud.server.assets;

import com.moud.server.project.ProjectLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public final class ModelTextureResolver {
    private ModelTextureResolver() {
    }

    public static Optional<String> inferTextureId(String modelId) {
        try {
            Path projectRoot = ProjectLoader.findProjectRoot();
            if (projectRoot == null || modelId == null || !modelId.contains(":")) {
                return Optional.empty();
            }

            String[] parts = modelId.split(":", 2);
            if (parts.length != 2) {
                return Optional.empty();
            }

            String namespace = parts[0];
            String path = parts[1];
            if (namespace.isBlank() || path.isBlank()) {
                return Optional.empty();
            }

            Path assetsRoot = projectRoot.resolve("assets").resolve(namespace);
            Path texturesRoot = assetsRoot.resolve("textures");
            if (!Files.isDirectory(texturesRoot)) {
                return Optional.empty();
            }

            Optional<String> mapFile = resolveMapKd(assetsRoot, path);
            String candidateName = mapFile.orElseGet(() -> {
                String base = Path.of(path).getFileName().toString();
                if (base.endsWith(".obj")) {
                    base = base.substring(0, base.length() - 4) + ".png";
                }
                return base;
            });

            if (candidateName.isBlank()) {
                return Optional.empty();
            }

            try (Stream<Path> matches = Files.walk(texturesRoot)) {
                Optional<Path> found = matches
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equalsIgnoreCase(candidateName))
                        .findFirst();
                if (found.isEmpty()) {
                    return Optional.empty();
                }

                Path rel = texturesRoot.relativize(found.get());
                String textureId = namespace + ":textures/" + rel.toString().replace('\\', '/');
                return Optional.of(textureId);
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> resolveMapKd(Path assetsRoot, String modelRelativePath) {
        try {
            Path modelPathFs = assetsRoot.resolve(modelRelativePath);
            Path mtl = modelPathFs.getParent().resolve(replaceExt(modelPathFs.getFileName().toString(), ".mtl"));
            if (!Files.isRegularFile(mtl)) {
                return Optional.empty();
            }

            try (Stream<String> lines = Files.lines(mtl)) {
                return lines
                        .map(String::trim)
                        .filter(l -> l.startsWith("map_Kd"))
                        .map(l -> l.substring("map_Kd".length()).trim())
                        .filter(s -> !s.isBlank())
                        .findFirst();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String replaceExt(String name, String ext) {
        int idx = name.lastIndexOf('.');
        if (idx >= 0) {
            return name.substring(0, idx) + ext;
        }
        return name + ext;
    }
}

