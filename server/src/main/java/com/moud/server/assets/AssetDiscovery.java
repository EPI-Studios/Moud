package com.moud.server.assets;

import com.moud.api.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class AssetDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetDiscovery.class);

    private final Path projectRoot;
    private final Map<String, AssetMetadata> discoveredAssets;

    public AssetDiscovery(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.discoveredAssets = new HashMap<>();
    }

    public void scanAssets() throws IOException {
        discoveredAssets.clear();
        List<Path> roots = discoverRoots();
        if (roots.isEmpty()) {
            LOGGER.warn("No asset directories found under {}", projectRoot);
            return;
        }

        for (Path root : roots) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .forEach(path -> processAsset(root, path));
            }
        }

        LOGGER.info("Discovered {} assets from {} roots", discoveredAssets.size(), roots.size());
    }

    private List<Path> discoverRoots() throws IOException {
        List<Path> roots = new ArrayList<>();
        addIfExists(roots, projectRoot.resolve("assets"));
        addIfExists(roots, projectRoot.resolve("example").resolve("assets"));
        addIfExists(roots, projectRoot.resolve("example").resolve("ts").resolve("assets"));
        addIfExists(roots, projectRoot.resolve("client").resolve("assets"));

        Path packagesDir = projectRoot.resolve("packages");
        if (Files.isDirectory(packagesDir)) {
            try (Stream<Path> dirs = Files.walk(packagesDir, 3)) {
                dirs.filter(path -> Files.isDirectory(path) && path.getFileName().toString().equals("assets"))
                        .forEach(root -> addIfExists(roots, root));
            }
        }
        return roots;
    }

    private void addIfExists(List<Path> roots, Path path) {
        if (path != null && Files.isDirectory(path)) {
            boolean duplicate = roots.stream().anyMatch(existing -> {
                try {
                    return Files.isSameFile(existing, path);
                } catch (IOException e) {
                    return Objects.equals(existing.toAbsolutePath(), path.toAbsolutePath());
                }
            });
            if (!duplicate) {
                roots.add(path);
            }
        }
    }

    private void processAsset(Path root, Path assetPath) {
        try {
            Path relativePath = root.relativize(assetPath);
            String assetId = PathUtils.normalizeSlashes(relativePath.toString()).toLowerCase(Locale.ROOT);
            AssetType type = determineAssetType(assetPath);

            AssetMetadata metadata = new AssetMetadata(assetId, assetPath, type);
            discoveredAssets.put(assetId, metadata);

            LOGGER.debug("Discovered asset: {} ({}) from {}", assetId, type, root);
        } catch (Exception e) {
            LOGGER.error("Failed to process asset: {}", assetPath, e);
        }
    }

    private AssetType determineAssetType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".glsl") || fileName.endsWith(".vert") || fileName.endsWith(".frag")) {
            return AssetType.SHADER;
        } else if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return AssetType.TEXTURE;
        } else if (fileName.endsWith(".obj") || fileName.endsWith(".gltf") || fileName.endsWith(".glb") || fileName.endsWith(".fbx")) {
            return AssetType.MODEL;
        } else if (fileName.endsWith(".ogg") || fileName.endsWith(".wav") || fileName.endsWith(".mp3")) {
            return AssetType.SOUND;
        } else if (fileName.endsWith(".json")) {
            return AssetType.DATA;
        }

        return AssetType.UNKNOWN;
    }

    public AssetMetadata getAsset(String assetId) {
        return discoveredAssets.get(assetId);
    }

    public Map<String, AssetMetadata> getAllAssets() {
        return new HashMap<>(discoveredAssets);
    }

    public enum AssetType {
        SHADER, TEXTURE, MODEL, SOUND, DATA, UNKNOWN
    }

    public static class AssetMetadata {
        private final String id;
        private final Path path;
        private final AssetType type;

        public AssetMetadata(String id, Path path, AssetType type) {
            this.id = id;
            this.path = path;
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public Path getPath() {
            return path;
        }

        public AssetType getType() {
            return type;
        }
    }
}
