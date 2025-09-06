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

        if (!Files.exists(clientDir)) {
            LOGGER.info("No client directory found, skipping client script compilation");
            return;
        }

        LOGGER.info("Compiling client scripts from: {}", clientDir);
        this.compiledClientScripts = compileClientScripts(clientDir);
        this.clientScriptsHash = generateHash(compiledClientScripts);

        LOGGER.info("Client scripts compiled successfully, size: {} bytes", compiledClientScripts.length);
    }

    private byte[] compileClientScripts(Path clientDir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(baos);
             Stream<Path> paths = Files.walk(clientDir)) {

            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".js"))
                    .forEach(path -> {
                        try {
                            String relativePath = clientDir.relativize(path).toString();
                            String content;

                            if (path.toString().endsWith(".ts")) {
                                LOGGER.debug("Transpiling client script: {}", relativePath);
                                content = transpileTypeScript(path);
                                relativePath = relativePath.replace(".ts", ".js");
                            } else {
                                content = Files.readString(path);
                            }

                            ZipEntry entry = new ZipEntry(relativePath);
                            zip.putNextEntry(entry);
                            zip.write(content.getBytes());
                            zip.closeEntry();

                        } catch (IOException | ExecutionException | InterruptedException e) {
                            throw new RuntimeException("Failed to process client script: " + path, e);
                        }
                    });
        }

        return baos.toByteArray();
    }

    private String transpileTypeScript(Path tsFile) throws ExecutionException, InterruptedException {
        return TypeScriptTranspiler.transpile(tsFile).get();
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