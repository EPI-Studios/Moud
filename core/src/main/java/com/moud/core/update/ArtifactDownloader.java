package com.moud.core.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

public final class ArtifactDownloader {

    private static final Duration TIMEOUT = Duration.ofSeconds(300);
    private static final int BUFFER_SIZE = 256 * 1024;

    private final HttpClient http;

    public ArtifactDownloader() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public ArtifactDownloader(HttpClient http) {
        this.http = http;
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(long bytesDownloaded, long totalBytes);
    }

    public void download(ReleaseArtifact artifact, Path destPath, ProgressListener listener)
            throws IOException, InterruptedException {

        if (artifact == null) throw new IllegalArgumentException("artifact is required");
        if (destPath == null) throw new IllegalArgumentException("destPath is required");

        Path parent = destPath.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path tmpFile = destPath.resolveSibling(destPath.getFileName() + ".tmp");

        try {
            downloadToFile(artifact.url(), tmpFile, artifact.size(), listener);
            verifyHash(tmpFile, artifact.sha256(), artifact.file());
            Files.move(tmpFile, destPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.deleteIfExists(tmpFile);
            throw e;
        }
    }

    public static boolean alreadyDownloaded(Path path, String expectedSha256) {
        if (path == null || !Files.isRegularFile(path)) return false;
        try {
            return Sha256.matches(path, expectedSha256);
        } catch (IOException e) {
            return false;
        }
    }

    private void downloadToFile(String url, Path dest, long expectedSize, ProgressListener listener)
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "moud-updater/1.0")
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
        }

        long totalBytes = response.headers()
                .firstValueAsLong("content-length")
                .orElse(expectedSize);

        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(dest)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long downloaded = 0;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (listener != null) {
                    listener.onProgress(downloaded, totalBytes);
                }
            }
        }
    }

    private static void verifyHash(Path file, String expectedSha256, String artifactName) throws IOException {
        if (expectedSha256 == null || expectedSha256.isBlank()) return;
        String actual = Sha256.hex(file);
        if (!expectedSha256.equalsIgnoreCase(actual)) {
            throw new SecurityException("SHA-256 mismatch for " + artifactName
                    + ": expected " + expectedSha256 + ", got " + actual);
        }
    }
}
