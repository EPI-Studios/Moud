package com.moud.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.server.MoudEngine;
import com.moud.server.logging.MoudLogger;
import com.moud.server.project.ProjectLoader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class HotReloadEndpoint {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(HotReloadEndpoint.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MoudEngine engine;
    private HttpServer server;
    private boolean enabled;

    public HotReloadEndpoint(MoudEngine engine, boolean enabled) {
        this.engine = engine;
        this.enabled = enabled;
    }

    public void start(int port) {
        if (!enabled) {
            LOGGER.info("Hot reload endpoint disabled");
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress("localhost", port + 1000), 0);
            server.createContext("/moud/api/reload", new ReloadHandler());
            server.setExecutor(null);
            server.start();

            LOGGER.info("Hot reload endpoint started on port {}", port + 1000);
        } catch (IOException e) {
            LOGGER.error("Failed to start hot reload endpoint", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            LOGGER.info("Hot reload endpoint stopped");
        }
    }

    private class ReloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                InputStream inputStream = exchange.getRequestBody();
                JsonNode request = MAPPER.readTree(inputStream);

                if (!request.has("action") || !"reload_scripts".equals(request.get("action").asText())) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid action\"}");
                    return;
                }

                LOGGER.info("Hot reload request received");

                CompletableFuture.runAsync(() -> {
                    try {
                        engine.reloadUserScripts();
                    } catch (Exception e) {
                        LOGGER.error("Failed to reload scripts", e);
                    }
                });

                sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Scripts reloaded\"}");

            } catch (Exception e) {
                LOGGER.error("Error handling reload request", e);
                sendResponse(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            byte[] responseBytes = response.getBytes();
            exchange.sendResponseHeaders(statusCode, responseBytes.length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        }
    }
}