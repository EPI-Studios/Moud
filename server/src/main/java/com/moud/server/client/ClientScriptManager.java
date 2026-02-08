package com.moud.server.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.api.util.PathUtils;
import com.moud.server.project.ProjectLoader;
import com.moud.server.typescript.TypeScriptTranspiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

public class ClientScriptManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientScriptManager.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_CLIENT_BUNDLE_BYTES = 2 * 1024 * 1024; // keep under Minestom framed packet cap
    private static final long MAX_BUNDLED_ASSET_BYTES = 64L * 1024L * 1024L;

    private byte[] compiledClientResources;
    private String clientResourcesHash;

    public void initialize() throws IOException {
        boolean resourcePackConfigured = isResourcePackConfigured();
        Path projectRoot = ProjectLoader.findProjectRoot();
        Path clientDir = projectRoot.resolve("client");
        Path cacheDir = projectRoot.resolve(".moud/cache");
        Path cachedBundle = cacheDir.resolve("client.bundle");
        Path manifestPath = cacheDir.resolve("manifest.json");

        if (!resourcePackConfigured && Files.exists(cachedBundle)) {
            LOGGER.info("Using cached client bundle from {}", cachedBundle);
            this.compiledClientResources = Files.readAllBytes(cachedBundle);
            this.clientResourcesHash = readHashFromManifest(manifestPath)
                    .orElseGet(() -> generateHash(compiledClientResources));
            LOGGER.info("Loaded cached client resources. Hash: {}, Size: {} bytes",
                    clientResourcesHash, compiledClientResources.length);
            return;
        } else if (resourcePackConfigured && Files.exists(cachedBundle)) {
            LOGGER.info("Resource pack configured; rebuilding client bundle (may include select assets).");
        }

        if (!Files.exists(clientDir)) {
            LOGGER.info("No client scripts directory found, skipping client resources compilation");
            return;
        }

        LOGGER.info("Compiling client scripts...");
        this.compiledClientResources = packageClientResources(projectRoot, clientDir, resourcePackConfigured);
        this.clientResourcesHash = generateHash(compiledClientResources);

        LOGGER.info("Client scripts packaged successfully. Hash: {}, Size: {} bytes",
                clientResourcesHash, compiledClientResources.length);
    }

    private byte[] packageClientResources(Path projectRoot, Path clientDir, boolean resourcePackConfigured) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {

            if (Files.exists(clientDir)) {
                try (Stream<Path> paths = Files.walk(clientDir)) {
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".js"))
                            .forEach(path -> addScriptToZip(clientDir, path, zip));
                }
            }

            addSharedPhysicsToZip(projectRoot, zip);
            if (resourcePackConfigured) {
                addAssetsFromResourcePack(zip);
            }
        }
        byte[] bundled = baos.toByteArray();
        if (bundled.length > MAX_CLIENT_BUNDLE_BYTES) {
            LOGGER.warn("Compiled client bundle exceeds safe size ({} bytes > {} bytes). Consider reducing script size or assets.",
                    bundled.length, MAX_CLIENT_BUNDLE_BYTES);
        }
        return bundled;
    }

    private void addAssetsFromResourcePack(ZipOutputStream zip) {
        String packPathEnv = System.getenv("MOUD_RESOURCE_PACK_PATH");
        if (packPathEnv == null || packPathEnv.isBlank()) {
            return;
        }
        Path packPath = Paths.get(packPathEnv);
        if (!Files.isRegularFile(packPath)) {
            LOGGER.warn("MOUD_RESOURCE_PACK_PATH is set but is not a file: {}", packPath);
            return;
        }

        String prefixesProp = System.getProperty("moud.clientBundleAssetPrefixes", "assets/moud/");
        String[] prefixes = Stream.of(prefixesProp.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);

        if (prefixes.length == 0) {
            return;
        }

        long totalBytes = 0L;
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(packPath.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = PathUtils.normalizeSlashes(entry.getName());
                if (!startsWithAny(name, prefixes)) {
                    continue;
                }

                byte[] data = zipFile.getInputStream(entry).readAllBytes();
                totalBytes += data.length;
                if (totalBytes > MAX_BUNDLED_ASSET_BYTES) {
                    LOGGER.warn("Bundled assets exceeded {} bytes, stopping early (set -Dmoud.clientBundleAssetPrefixes to reduce scope)",
                            MAX_BUNDLED_ASSET_BYTES);
                    break;
                }

                zip.putNextEntry(new ZipEntry(name));
                zip.write(data);
                zip.closeEntry();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to bundle assets from resource pack at {}", packPath, e);
            return;
        }

        LOGGER.info("Bundled {} bytes of assets from resource pack (prefixes={})",
                totalBytes, java.util.Arrays.toString(prefixes));
    }

    private static boolean startsWithAny(String value, String[] prefixes) {
        if (value == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void addScriptToZip(Path clientDir, Path path, ZipOutputStream zip) {
        try {
            String relativePath = PathUtils.normalizeSlashes(clientDir.relativize(path).toString());
            String content;
            if (path.toString().endsWith(".ts")) {
                LOGGER.debug("Transpiling client script: {}", relativePath);
                content = TypeScriptTranspiler.transpile(path, true).get(); // true = isClientScript
                relativePath = relativePath.replace(".ts", ".js");
            } else {
                content = Files.readString(path);
            }
            zip.putNextEntry(new ZipEntry("scripts/" + relativePath));
            zip.write(content.getBytes());
            zip.closeEntry();
            LOGGER.debug("Packaged script: {}", relativePath);
        } catch (IOException | ExecutionException | InterruptedException e) {
            throw new RuntimeException("Failed to process client script: " + path, e);
        }
    }

    private void addSharedPhysicsToZip(Path projectRoot, ZipOutputStream zip) {
        if (projectRoot == null || zip == null) {
            return;
        }
        Path sharedPhysics = projectRoot.resolve("shared/physics/index.ts");
        if (!Files.exists(sharedPhysics)) {
            return;
        }

        try {
            LOGGER.info("Transpiling shared physics: {}", projectRoot.relativize(sharedPhysics));
            String content = TypeScriptTranspiler.transpileSharedPhysics(sharedPhysics).get();
            zip.putNextEntry(new ZipEntry("scripts/shared/physics.js"));
            zip.write(content.getBytes());
            zip.closeEntry();
            LOGGER.info("Packaged shared physics script: scripts/shared/physics.js");
        } catch (Exception e) {
            throw new RuntimeException("Failed to process shared physics script: " + sharedPhysics, e);
        }
    }

    private Optional<String> readHashFromManifest(Path manifestPath) {
        if (!Files.exists(manifestPath)) {
            return Optional.empty();
        }
        try {
            var tree = JSON.readTree(manifestPath.toFile());
            if (tree.hasNonNull("hash")) {
                return Optional.of(tree.get("hash").asText());
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read bundle manifest at {}", manifestPath, e);
        }
        return Optional.empty();
    }

    private String generateHash(byte[] data) {
        if (data == null || data.length == 0) {
            return "empty";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("SHA-256 algorithm not found! Falling back to simple hash.", e);
            return String.valueOf(data.length);
        }
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public byte[] getCompiledScripts() {
        return compiledClientResources;
    }

    public String getScriptsHash() {
        return clientResourcesHash;
    }

    public boolean hasClientScripts() {
        return compiledClientResources != null && compiledClientResources.length > 0;
    }

    public synchronized void updateClientBundle(byte[] bundle, String hash) {
        if (bundle == null || bundle.length == 0) {
            LOGGER.warn("Received empty client bundle during update, ignoring");
            return;
        }
        this.compiledClientResources = bundle;
        this.clientResourcesHash = (hash != null && !hash.isBlank()) ? hash : generateHash(bundle);
        LOGGER.info("Client bundle updated. Hash: {}, Size: {} bytes", clientResourcesHash, compiledClientResources.length);
    }

    private boolean isResourcePackConfigured() {
        String packPathEnv = System.getenv("MOUD_RESOURCE_PACK_PATH");
        if (packPathEnv == null || packPathEnv.isBlank()) {
            return false;
        }
        Path packPath = Paths.get(packPathEnv);
        return Files.isRegularFile(packPath);
    }
}
