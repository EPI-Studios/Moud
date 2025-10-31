package com.moud.server.assets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class AssetDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetDiscovery.class);

    private final Path assetsDirectory;
    private final Map<String, AssetMetadata> discoveredAssets;

    public AssetDiscovery(Path projectRoot) {
        this.assetsDirectory = projectRoot.resolve("assets");
        this.discoveredAssets = new HashMap<>();
    }

    public void scanAssets() throws IOException {
        discoveredAssets.clear();
        if (!Files.exists(assetsDirectory)) {
            LOGGER.warn("Assets directory not found: {}", assetsDirectory);
            return;
        }

        try (Stream<Path> paths = Files.walk(assetsDirectory)) {
            paths.filter(Files::isRegularFile)
                    .forEach(this::processAsset);
        }

        LOGGER.info("Discovered {} assets", discoveredAssets.size());
    }

    private void processAsset(Path assetPath) {
        try {
            Path relativePath = assetsDirectory.relativize(assetPath);
            String assetId = relativePath.toString().replace('\\', '/');
            AssetType type = determineAssetType(assetPath);

            AssetMetadata metadata = new AssetMetadata(assetId, assetPath, type);
            discoveredAssets.put(assetId, metadata);

            LOGGER.debug("Discovered asset: {} ({})", assetId, type);
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
        } else if (fileName.endsWith(".obj") || fileName.endsWith(".gltf") || fileName.endsWith(".fbx")) {
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
