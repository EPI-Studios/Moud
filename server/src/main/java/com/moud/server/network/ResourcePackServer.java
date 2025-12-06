package com.moud.server.network;

import com.moud.server.logging.MoudLogger;
import com.moud.server.logging.LogContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class ResourcePackServer {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(ResourcePackServer.class);
    private static HttpServer server;

    private ResourcePackServer() {
    }

    static ResourcePackInfo start(Path packPath, String bindHost, String publicHost, int port, String urlPath) {
        if (packPath == null || !Files.isRegularFile(packPath)) {
            LOGGER.warn("Resource pack path missing or not a file: {}", packPath);
            return null;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
        try {
            String sha1 = sha1(packPath);
            long size = Files.size(packPath);
            UUID packId = UUID.nameUUIDFromBytes(("moud-resource-pack:" + sha1).getBytes(StandardCharsets.UTF_8));

            if (server == null) {
                server = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
                server.createContext(urlPath, new PackHandler(packPath, size));
                server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
                server.start();
                LOGGER.info(LogContext.builder()
                        .put("bind_host", bindHost)
                        .put("public_host", publicHost)
                        .put("port", server.getAddress().getPort())
                        .put("path", urlPath)
                        .put("size_bytes", size)
                        .put("sha1", sha1)
                        .put("id", packId)
                        .build(), "Started resource pack HTTP server");
            }

            String url = "http://" + publicHost + ":" + server.getAddress().getPort() + urlPath;
            return new ResourcePackInfo(packId, url, sha1);
        } catch (IOException e) {
            LOGGER.warn("Failed to start resource pack server for {}", packPath, e);
            return null;
        }
    }

    private static String sha1(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private record PackHandler(Path packPath, long size) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, size);
            try (OutputStream os = exchange.getResponseBody();
                 var in = Files.newInputStream(packPath)) {
                in.transferTo(os);
            }
        }
    }

    record ResourcePackInfo(UUID id, String url, String sha1) {}
}
