// File: server/src/main/java/com/moud/server/client/ClientScriptManager.java

package com.moud.server.client;

import com.moud.server.project.ProjectLoader;
import com.moud.server.typescript.TypeScriptTranspiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

public class ClientScriptManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientScriptManager.class);

    private byte[] compiledClientResources;
    private String clientResourcesHash;

    public void initialize() throws IOException {
        Path projectRoot = ProjectLoader.findProjectRoot();
        Path clientDir = projectRoot.resolve("client");
        Path assetsDir = projectRoot.resolve("assets");

        if (!Files.exists(clientDir) && !Files.exists(assetsDir)) {
            LOGGER.info("No client or assets directory found, skipping client resources compilation");
            return;
        }

        LOGGER.info("Compiling client scripts and packaging assets...");
        this.compiledClientResources = packageClientResources(projectRoot, clientDir, assetsDir);
        this.clientResourcesHash = generateHash(compiledClientResources);

        LOGGER.info("Client resources packaged successfully. Hash: {}, Size: {} bytes",
                clientResourcesHash, compiledClientResources.length);
    }

    private byte[] packageClientResources(Path projectRoot, Path clientDir, Path assetsDir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            // Package client scripts
            if (Files.exists(clientDir)) {
                try (Stream<Path> paths = Files.walk(clientDir)) {
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".js"))
                            .forEach(path -> addScriptToZip(clientDir, path, zip));
                }
            }

            // Package all assets
            if (Files.exists(assetsDir)) {
                try (Stream<Path> paths = Files.walk(assetsDir)) {
                    paths.filter(Files::isRegularFile)
                            .forEach(path -> addAssetToZip(projectRoot, path, zip));
                }
            }
        }
        return baos.toByteArray();
    }

    private void addScriptToZip(Path clientDir, Path path, ZipOutputStream zip) {
        try {
            String relativePath = clientDir.relativize(path).toString().replace('\\', '/');
            String content;
            if (path.toString().endsWith(".ts")) {
                LOGGER.debug("Transpiling client script: {}", relativePath);
                content = TypeScriptTranspiler.transpile(path).get();
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

    private void addAssetToZip(Path projectRoot, Path path, ZipOutputStream zip) {
        try {
            String relativePath = projectRoot.relativize(path).toString().replace('\\', '/');
            byte[] content = Files.readAllBytes(path);

            if (relativePath.contains("animation") && relativePath.endsWith(".json")) {
                String fileName = path.getFileName().toString();
                String newPath = "assets/moud/player_animations/" + fileName;

                zip.putNextEntry(new ZipEntry(newPath));
                zip.write(content);
                zip.closeEntry();
                LOGGER.info("Packaged animation file to new path: {}", newPath);
            } else {
                zip.putNextEntry(new ZipEntry(relativePath));
                zip.write(content);
                zip.closeEntry();
                LOGGER.debug("Packaged asset: {}", relativePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to process asset: " + path, e);
        }
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
}