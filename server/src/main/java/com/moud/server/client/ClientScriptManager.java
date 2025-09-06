package com.moud.server.client;

import com.moud.server.project.ProjectLoader;
import com.moud.server.typescript.TypeScriptTranspiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

public class ClientScriptManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientScriptManager.class);

    private byte[] compiledClientScripts;
    private String clientScriptsHash;

    public void initialize() throws IOException {
        Path projectRoot = ProjectLoader.findProjectRoot();
        Path clientDir = projectRoot.resolve("client");
        Path assetsDir = projectRoot.resolve("assets");
        if (!Files.exists(clientDir) && !Files.exists(assetsDir)) {
            LOGGER.info("No client or assets directory found, skipping client resources compilation");
            return;
        }

        LOGGER.info("Compiling client scripts and packaging assets...");
        this.compiledClientScripts = packageClientResources(projectRoot, clientDir, assetsDir);
        this.clientScriptsHash = generateHash(compiledClientScripts);

        LOGGER.info("Client resources packaged successfully, size: {} bytes", compiledClientScripts.length);
    }

    private byte[] packageClientResources(Path projectRoot, Path clientDir, Path assetsDir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            if (Files.exists(clientDir)) {
                try (Stream<Path> paths = Files.walk(clientDir)) {
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".js"))
                            .forEach(path -> addScriptToZip(clientDir, path, zip));
                }
            }

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
        } catch (IOException | ExecutionException | InterruptedException e) {
            throw new RuntimeException("Failed to process client script: " + path, e);
        }
    }

    private void addAssetToZip(Path projectRoot, Path path, ZipOutputStream zip) {
        try {
            String relativePath = projectRoot.relativize(path).toString().replace('\\', '/');
            byte[] content = Files.readAllBytes(path);

            zip.putNextEntry(new ZipEntry(relativePath));
            zip.write(content);
            zip.closeEntry();
            LOGGER.debug("Packaging asset: {}", relativePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to process asset: " + path, e);
        }
    }

    private String generateHash(byte[] data) {
        return String.valueOf(data.length);
    }

    public byte[] getCompiledScripts() {
        return compiledClientScripts;
    }
    public String getScriptsHash() {
        return clientScriptsHash;
    }
    public boolean hasClientScripts() {
        return compiledClientScripts != null && compiledClientScripts.length > 0;
    }
}