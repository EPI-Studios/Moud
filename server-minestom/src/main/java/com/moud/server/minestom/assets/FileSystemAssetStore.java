package com.moud.server.minestom.assets;

import com.moud.core.assets.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class FileSystemAssetStore implements AssetStore {
    private final Path root;
    private final Path projectRoot;
    private final Path blobsDir;
    private final Path manifestFile;
    private final Map<ResPath, AssetMeta> manifest = new HashMap<>();

    public FileSystemAssetStore(Path root) throws IOException {
        this.root = Objects.requireNonNull(root);
        this.projectRoot = this.root.getParent() == null ? this.root : this.root.getParent();
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
        return manifest.values().stream()
                .filter(m -> m.hash().equals(hash))
                .findFirst()
                .orElse(null);
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
            atomicWrite(blob, bytes);
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

    public synchronized void reloadManifest() {
        try {
            loadManifest();
            scanForNewAssets();
        } catch (IOException e) {
            System.err.println("[moud-server] Failed to reload manifest: " + e.getMessage());
        }
    }

    private static final Map<String, Map<String, AssetType>> SCAN_DIRS = Map.of(
            "materials", Map.of(".moudmat", AssetType.TEXT),
            "shaders",   Map.of(".moudshader", AssetType.TEXT),
            "textures",  Map.of(".png", AssetType.IMAGE, ".jpg", AssetType.IMAGE, ".jpeg", AssetType.IMAGE),
            "models",    Map.of(".bbmodel", AssetType.MODEL),
            "scripts",   Map.of(".js", AssetType.TEXT, ".mjs", AssetType.TEXT, ".cjs", AssetType.TEXT, ".luau", AssetType.TEXT)
    );

    private void scanForNewAssets() throws IOException {
        for (String dir : SCAN_DIRS.keySet()) {
            Files.createDirectories(root.resolve(dir));
        }

        boolean changed = false;

        Set<String> knownHashes = new HashSet<>();
        for (AssetMeta meta : manifest.values()) {
            knownHashes.add(meta.hash().hex());
        }

        try (Stream<Path> blobFiles = Files.list(blobsDir)) {
            for (Path blobFile : (Iterable<Path>) blobFiles::iterator) {
                if (Files.isDirectory(blobFile)) continue;
                String fileName = blobFile.getFileName().toString();
                if (fileName.endsWith(".tmp")) continue;
                if (AssetHash.validate(fileName).ok() && !knownHashes.contains(fileName)) {
                    byte[] bytes = Files.readAllBytes(blobFile);
                    AssetHash hash = new AssetHash(fileName);
                    ResPath path = new ResPath("res://blobs/" + fileName);
                    manifest.put(path, new AssetMeta(hash, bytes.length, AssetType.BINARY));
                    changed = true;
                }
            }
        }

        for (var dirEntry : SCAN_DIRS.entrySet()) {
            String dirName = dirEntry.getKey();
            var extensions = dirEntry.getValue();
            if ("scripts".equals(dirName)) {
                changed |= scanDir(root.resolve("scripts"), "scripts", extensions);
                changed |= scanDir(projectRoot.resolve("scripts"), "scripts", extensions);
            } else {
                changed |= scanDir(root.resolve(dirName), dirName, extensions);
            }
        }

        if (changed) persistManifest();
    }

    private boolean scanDir(Path dir, String resPrefix, Map<String, AssetType> extensions) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }

        boolean changed = false;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(file)) continue;

                String name = file.getFileName().toString().toLowerCase();
                AssetType type = extensions.entrySet().stream()
                        .filter(e -> name.endsWith(e.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
                if (type == null) continue;

                String relative = dir.relativize(file).toString().replace('\\', '/');
                ResPath resPath = new ResPath("res://" + resPrefix + "/" + relative);
                byte[] bytes = Files.readAllBytes(file);
                AssetHash hash = AssetHash.sha256(bytes);
                AssetMeta meta = new AssetMeta(hash, bytes.length, type);
                AssetMeta existing = manifest.get(resPath);
                if (existing != null
                        && existing.hash().equals(meta.hash())
                        && existing.sizeBytes() == meta.sizeBytes()
                        && existing.type() == meta.type()) {
                    continue;
                }

                Path blob = blobPath(hash);
                if (!Files.exists(blob)) {
                    atomicWrite(blob, bytes);
                }

                manifest.put(resPath, meta);
                changed = true;
            }
        }
        return changed;
    }

    private void loadManifest() throws IOException {
        manifest.clear();
        if (!Files.exists(manifestFile)) return;

        for (String line : Files.readAllLines(manifestFile, StandardCharsets.UTF_8)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\t");
            if (parts.length < 4) continue;
            try {
                manifest.put(
                        new ResPath(parts[0]),
                        new AssetMeta(new AssetHash(parts[1]), Long.parseLong(parts[2]), AssetType.valueOf(parts[3]))
                );
            } catch (Exception ignored) {}
        }
    }

    private void persistManifest() throws IOException {
        var entries = new ArrayList<>(manifest.entrySet());
        entries.sort(Comparator.comparing(e -> e.getKey().value()));

        var sb = new StringBuilder(entries.size() * 64);
        for (var entry : entries) {
            ResPath path = entry.getKey();
            AssetMeta meta = entry.getValue();
            if (path == null || meta == null) continue;
            sb.append(path.value()).append('\t')
                    .append(meta.hash().hex()).append('\t')
                    .append(meta.sizeBytes()).append('\t')
                    .append(meta.type().name()).append('\n');
        }

        Files.createDirectories(root);
        atomicWriteString(manifestFile, sb.toString());
    }

    private Path blobPath(AssetHash hash) {
        return blobsDir.resolve(hash.hex());
    }

    private void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void atomicWriteString(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
