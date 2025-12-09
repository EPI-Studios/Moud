package com.moud.server.assets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AssetManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetManager.class);
    private static final int MAX_CACHE_ENTRIES = 256;

    private final AssetDiscovery discovery;
    private final ConcurrentHashMap<String, LoadedAsset> loadedAssets;

    public AssetManager(Path projectRoot) {
        this.discovery = new AssetDiscovery(projectRoot);
        this.loadedAssets = new ConcurrentHashMap<>();
    }

    public void initialize() throws IOException {
        discovery.scanAssets();
        LOGGER.info("Asset manager initialized");
    }

    public synchronized void refresh() throws IOException {
        loadedAssets.clear();
        discovery.scanAssets();
        LOGGER.info("Asset cache refreshed");
    }

    public LoadedAsset loadAsset(String assetId) throws IOException {
        String normalizedId = normalizeAssetId(assetId);

        if (loadedAssets.containsKey(assetId)) {
            return loadedAssets.get(assetId);
        }

        if (loadedAssets.containsKey(normalizedId)) {
            return loadedAssets.get(normalizedId);
        }

        AssetDiscovery.AssetMetadata metadata = discovery.getAsset(normalizedId);
        if (metadata == null) {
            throw new IllegalArgumentException("Asset not found: " + assetId);
        }

        LoadedAsset loadedAsset = createLoadedAsset(metadata);
        loadedAssets.put(normalizedId, loadedAsset);
        trimCache();

        LOGGER.debug("Loaded asset: {} (requested as {})", normalizedId, assetId);
        return loadedAsset;
    }

    private String normalizeAssetId(String assetId) {
        String normalized = assetId.replace("\\", "/");
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replace(":", "/");
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        return normalized;
    }

    private void trimCache() {
        int oversize = loadedAssets.size() - MAX_CACHE_ENTRIES;
        if (oversize <= 0) {
            return;
        }
        var iterator = loadedAssets.keySet().iterator();
        while (oversize-- > 0 && iterator.hasNext()) {
            loadedAssets.remove(iterator.next());
        }
    }

    private LoadedAsset createLoadedAsset(AssetDiscovery.AssetMetadata metadata) throws IOException {
        switch (metadata.getType()) {
            case SHADER:
                return new ShaderAsset(metadata.getId(), metadata.getPath());
            case TEXTURE:
                return new TextureAsset(metadata.getId(), metadata.getPath());
            case MODEL:
                return new ModelAsset(metadata.getId(), metadata.getPath());
            case SOUND:
                return new SoundAsset(metadata.getId(), metadata.getPath());
            case DATA:
                return new DataAsset(metadata.getId(), metadata.getPath());
            default:
                return new RawAsset(metadata.getId(), metadata.getPath());
        }
    }

    public abstract static class LoadedAsset {
        protected final String id;
        protected final Path path;
        protected final byte[] data;

        public LoadedAsset(String id, Path path) throws IOException {
            this.id = id;
            this.path = path;
            this.data = Files.readAllBytes(path);
        }

        public String getId() {
            return id;
        }

        public Path getPath() {
            return path;
        }

        public byte[] getData() {
            return data;
        }

        public String getContent() {
            return new String(data);
        }
    }

    public static class ShaderAsset extends LoadedAsset {
        public ShaderAsset(String id, Path path) throws IOException {
            super(id, path);
        }

        public String getShaderCode() {
            return getContent();
        }
    }

    public static class TextureAsset extends LoadedAsset {
        public TextureAsset(String id, Path path) throws IOException {
            super(id, path);
        }
    }

    public static class ModelAsset extends LoadedAsset {
        public ModelAsset(String id, Path path) throws IOException {
            super(id, path);
        }
    }

    public static class SoundAsset extends LoadedAsset {
        public SoundAsset(String id, Path path) throws IOException {
            super(id, path);
        }
    }

    public static class DataAsset extends LoadedAsset {
        public DataAsset(String id, Path path) throws IOException {
            super(id, path);
        }

        public String getJsonContent() {
            return getContent();
        }
    }

    public static class RawAsset extends LoadedAsset {
        public RawAsset(String id, Path path) throws IOException {
            super(id, path);
        }
    }

    public Map<String, AssetDiscovery.AssetMetadata> getDiscoveredAssets() {
        return discovery.getAllAssets();
    }
}
