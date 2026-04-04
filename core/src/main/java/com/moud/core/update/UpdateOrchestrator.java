package com.moud.core.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class UpdateOrchestrator {

    private static final int MAX_KEPT_VERSIONS = 3;

    private final String target;
    private final Path baseDir;
    private final GitHubReleaseResolver resolver;
    private final ArtifactDownloader downloader;

    public UpdateOrchestrator(String target, Path baseDir,
                              GitHubReleaseResolver resolver,
                              ArtifactDownloader downloader) {
        if (target == null || target.isBlank()) throw new IllegalArgumentException("target required");
        if (baseDir == null) throw new IllegalArgumentException("baseDir required");
        this.target = target.trim();
        this.baseDir = baseDir;
        this.resolver = resolver;
        this.downloader = downloader;
    }

    public Path versionsDir() { return baseDir.resolve("versions"); }
    public Path cacheDir() { return baseDir.resolve("cache"); }
    public Path stagingDir() { return baseDir.resolve("staging"); }
    public Path statePath() { return baseDir.resolve("update-state.json"); }
    public Path currentLink() { return baseDir.resolve("current"); }
    public Path lockPath() { return baseDir.resolve("update.lock"); }

    public record CheckResult(boolean updateAvailable, String currentVersion,
                              String latestVersion, ReleaseManifest manifest) {}

    public record ApplyResult(boolean success, String version, String error) {}

    public CheckResult check(boolean includePrerelease) throws IOException, InterruptedException {
        UpdateState state = UpdateStateStore.load(statePath(), "stable");
        ReleaseManifest manifest = resolver.fetchLatest(includePrerelease);
        if (manifest == null) {
            return new CheckResult(false, state.currentVersion(), "", null);
        }
        boolean isNew = !manifest.version().equals(state.currentVersion())
                && !state.failedVersions().contains(manifest.version());
        return new CheckResult(isNew, state.currentVersion(), manifest.version(), manifest);
    }

    public ApplyResult apply(ReleaseManifest manifest, ArtifactDownloader.ProgressListener progress) {
        if (manifest == null) return new ApplyResult(false, "", "manifest is null");

        String version = manifest.version();
        try (UpdateLock lock = UpdateLock.acquire(lockPath())) {
            ReleaseSelection selection = ReleaseSelector.select(manifest, target,
                    UpdateStateStore.load(statePath(), "stable").currentVersion());

            Path archivePath = cacheDir().resolve(selection.artifact().file());
            if (!ArtifactDownloader.alreadyDownloaded(archivePath, selection.artifact().sha256())) {
                downloader.download(selection.artifact(), archivePath, progress);
            }

            Path versionDir = versionsDir().resolve(version);
            Path staging = stagingDir().resolve(version);
            deleteRecursive(staging);
            Files.createDirectories(staging);

            if (selection.patch()) {
                Path fromDir = versionsDir().resolve(selection.fromVersion());
                if (!Files.isDirectory(fromDir)) {
                    throw new IOException("Patch base version directory missing: " + selection.fromVersion());
                }
                copyRecursive(fromDir, staging);
            }

            extractTarGz(archivePath, staging);

            deleteRecursive(versionDir);
            Files.createDirectories(versionDir.getParent());
            try {
                Files.move(staging, versionDir, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(staging, versionDir, StandardCopyOption.REPLACE_EXISTING);
            }

            updateCurrentPointer(version);

            UpdateState oldState = UpdateStateStore.load(statePath(), "stable");
            Map<String, String> installed = new HashMap<>(oldState.installedVersions());
            installed.put(version, versionDir.toString());
            UpdateState newState = new UpdateState(
                    1, oldState.channel(), version,
                    oldState.currentVersion().isBlank() ? version : oldState.currentVersion(),
                    version, installed, oldState.failedVersions());
            UpdateStateStore.save(statePath(), newState);

            try {
                pruneOldVersions(newState);
            } catch (IOException pruneError) {
                System.err.println("[updater] Warning: version cleanup failed: " + pruneError.getMessage());
            }

            return new ApplyResult(true, version, null);
        } catch (IOException e) {
            return new ApplyResult(false, version, "could not acquire update lock: " + e.getMessage());
        } catch (Exception e) {
            markFailed(version);
            return new ApplyResult(false, version, e.getMessage());
        }
    }

    public boolean rollback() {
        try (UpdateLock lock = UpdateLock.acquire(lockPath())) {
            UpdateState state = UpdateStateStore.load(statePath(), "stable");
            String lkg = state.lastKnownGoodVersion();
            if (lkg.isBlank() || lkg.equals(state.currentVersion())) return false;

            Path lkgDir = versionsDir().resolve(lkg);
            if (!Files.isDirectory(lkgDir)) return false;

            updateCurrentPointer(lkg);

            List<String> failed = new ArrayList<>(state.failedVersions());
            if (!state.currentVersion().isBlank() && !failed.contains(state.currentVersion())) {
                failed.add(state.currentVersion());
            }
            UpdateState newState = new UpdateState(
                    1, state.channel(), lkg, lkg,
                    state.lastManifestVersion(),
                    state.installedVersions(), failed);
            UpdateStateStore.save(statePath(), newState);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public Path currentEngineDir() {
        try {
            UpdateState state = UpdateStateStore.load(statePath(), "stable");
            if (state.currentVersion().isBlank()) return null;
            Path dir = versionsDir().resolve(state.currentVersion());
            return Files.isDirectory(dir) ? dir : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void updateCurrentPointer(String version) throws IOException {
        Path pointer = currentLink();
        Files.createDirectories(pointer.getParent());
        Files.writeString(pointer, version);
    }

    private void markFailed(String version) {
        try {
            UpdateState state = UpdateStateStore.load(statePath(), "stable");
            if (state.failedVersions().contains(version)) return;
            List<String> failed = new ArrayList<>(state.failedVersions());
            failed.add(version);
            UpdateState newState = new UpdateState(
                    1, state.channel(), state.currentVersion(),
                    state.lastKnownGoodVersion(),
                    state.lastManifestVersion(),
                    state.installedVersions(), failed);
            UpdateStateStore.save(statePath(), newState);
        } catch (IOException ignored) {}
    }

    private void pruneOldVersions(UpdateState state) throws IOException {
        if (!Files.isDirectory(versionsDir())) return;
        List<String> keep = new ArrayList<>();
        if (!state.currentVersion().isBlank()) keep.add(state.currentVersion());
        if (!state.lastKnownGoodVersion().isBlank()) keep.add(state.lastKnownGoodVersion());

        List<Path> dirs;
        try (var stream = Files.list(versionsDir())) {
            dirs = stream.filter(Files::isDirectory).toList();
        }
        if (dirs.size() <= MAX_KEPT_VERSIONS) return;

        IOException firstError = null;
        int deleted = 0;
        for (Path dir : dirs) {
            String name = dir.getFileName().toString();
            if (keep.contains(name)) continue;
            if (dirs.size() - deleted - 1 < MAX_KEPT_VERSIONS) break;
            try {
                deleteRecursive(dir);
                deleted++;
            } catch (IOException e) {
                if (firstError == null) firstError = e;
            }
        }
        if (firstError != null) {
            throw new IOException("Failed to prune at least one old version directory", firstError);
        }
    }

    private static final int TAR_BLOCK = 512;

    private static void extractTarGz(Path archive, Path destDir) throws IOException {
        try (InputStream fileIn = Files.newInputStream(archive);
             GZIPInputStream gzipIn = new GZIPInputStream(fileIn)) {
            extractTar(gzipIn, destDir);
        }
    }

    private static void extractTar(InputStream in, Path rawDestDir) throws IOException {
        Path destDir = rawDestDir.toAbsolutePath().normalize();
        byte[] header = new byte[TAR_BLOCK];
        byte[] buf = new byte[8192];

        while (true) {
            int read = readFully(in, header, 0, TAR_BLOCK);
            if (read < TAR_BLOCK || isZeroBlock(header)) break;

            String name = readTarString(header, 0, 100);

            String prefix = readTarString(header, 345, 155);
            if (!prefix.isEmpty()) {
                name = prefix + "/" + name;
            }

            long size = readTarOctal(header, 124, 12);
            byte typeflag = header[156];
            boolean isDir = typeflag == '5' || name.endsWith("/");

            if (typeflag == '1' || typeflag == '2') {
                throw new IOException("Tar entry contains a symlink or hardlink (rejected): " + name);
            }

            String cleanName = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
            if (cleanName.isEmpty()) continue;

            Path entryPath = destDir.resolve(cleanName).normalize();
            if (!entryPath.startsWith(destDir)) {
                throw new IOException("Tar entry escapes target directory: " + name);
            }

            if (isDir) {
                Files.createDirectories(entryPath);
            } else {
                Files.createDirectories(entryPath.getParent());
                try (var out = Files.newOutputStream(entryPath)) {
                    long remaining = size;
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buf.length, remaining);
                        int n = in.read(buf, 0, toRead);
                        if (n < 0) throw new IOException("Unexpected end of tar stream");
                        out.write(buf, 0, n);
                        remaining -= n;
                    }
                }
            }

            long padding = (TAR_BLOCK - (size % TAR_BLOCK)) % TAR_BLOCK;
            if (padding > 0) {
                long skipped = in.skip(padding);
                if (skipped < padding) {
                    long left = padding - skipped;
                    while (left > 0) {
                        int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                        if (n < 0) break;
                        left -= n;
                    }
                }
            }
        }
    }

    private static int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(buf, off + total, len - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) return false;
        }
        return true;
    }

    private static String readTarString(byte[] header, int offset, int maxLen) {
        int end = offset;
        int limit = Math.min(offset + maxLen, header.length);
        while (end < limit && header[end] != 0) end++;
        return new String(header, offset, end - offset, java.nio.charset.StandardCharsets.UTF_8).trim();
    }

    private static long readTarOctal(byte[] header, int offset, int len) {
        if (len > 0 && (header[offset] & 0x80) != 0) {
            long value = 0;
            for (int i = 1; i < len; i++) {
                value = (value << 8) | (header[offset + i] & 0xFF);
            }
            return value;
        }
        String s = readTarString(header, offset, len);
        if (s.isEmpty()) return 0;
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void copyRecursive(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path dest = target.resolve(source.relativize(dir));
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path dest = target.resolve(source.relativize(file));
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursive(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
