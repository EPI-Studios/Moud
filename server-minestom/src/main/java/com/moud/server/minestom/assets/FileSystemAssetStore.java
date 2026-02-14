package com.moud.server.minestom.assets;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetManifest;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FileSystemAssetStore implements AssetStore {
    private final Path root;
    private final Path blobsDir;
    private final Path manifestFile;
    private final Map<ResPath, AssetMeta> manifest = new HashMap<>();

    public FileSystemAssetStore(Path root) throws IOException {
        this.root = Objects.requireNonNull(root);
        this.blobsDir = root.resolve("blobs");
        this.manifestFile = root.resolve("manifest.tsv");
        Files.createDirectories(blobsDir);
        loadManifest();
    }

    @Override
    public synchronized AssetManifest manifest() {
        return new AssetManifest(Map.copyOf(manifest));
    }

    @Override
    public synchronized AssetMeta meta(ResPath path) {
        return manifest.get(path);
    }

    @Override
    public synchronized AssetMeta metaByHash(AssetHash hash) {
        for (AssetMeta meta : manifest.values()) {
            if (meta.hash().equals(hash)) {
                return meta;
            }
        }
        return null;
    }

    @Override
    public synchronized boolean hasBlob(AssetHash hash) {
        return Files.exists(blobPath(hash));
    }

    @Override
    public synchronized byte[] readBlob(AssetHash hash) throws IOException {
        return Files.readAllBytes(blobPath(hash));
    }

    @Override
    public synchronized void put(ResPath path, AssetMeta meta, byte[] bytes) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(meta, "meta");
        Objects.requireNonNull(bytes, "bytes");

        Path blob = blobPath(meta.hash());
        if (!Files.exists(blob)) {
            Path tmp = blob.resolveSibling(blob.getFileName() + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, blob, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        manifest.put(path, meta);
        persistManifest();
    }

    @Override
    public synchronized void putMapping(ResPath path, AssetMeta meta) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(meta, "meta");
        manifest.put(path, meta);
        persistManifest();
    }

    private Path blobPath(AssetHash hash) {
        return blobsDir.resolve(hash.hex());
    }

    private void loadManifest() throws IOException {
        manifest.clear();
        if (!Files.exists(manifestFile)) {
            return;
        }
        List<String> lines = Files.readAllLines(manifestFile, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t");
            if (parts.length < 4) {
                continue;
            }
            try {
                ResPath path = new ResPath(parts[0]);
                AssetHash hash = new AssetHash(parts[1]);
                long size = Long.parseLong(parts[2]);
                AssetType type = AssetType.valueOf(parts[3]);
                manifest.put(path, new AssetMeta(hash, size, type));
            } catch (Exception ignored) {
            }
        }
    }

    private void persistManifest() throws IOException {
        ArrayList<Map.Entry<ResPath, AssetMeta>> entries = new ArrayList<>(manifest.entrySet());
        entries.sort(Comparator.comparing(e -> e.getKey().value()));

        StringBuilder sb = new StringBuilder(entries.size() * 64);
        for (Map.Entry<ResPath, AssetMeta> entry : entries) {
            ResPath path = entry.getKey();
            AssetMeta meta = entry.getValue();
            if (path == null || meta == null) {
                continue;
            }
            sb.append(path.value()).append('\t')
                    .append(meta.hash().hex()).append('\t')
                    .append(meta.sizeBytes()).append('\t')
                    .append(meta.type().name())
                    .append('\n');
        }
        Files.createDirectories(root);
        Path tmp = manifestFile.resolveSibling(manifestFile.getFileName() + ".tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, manifestFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}

