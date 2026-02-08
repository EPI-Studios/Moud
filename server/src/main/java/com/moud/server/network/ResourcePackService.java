package com.moud.server.network;

import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ResourcePackServer.ResourcePackInfo;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class ResourcePackService {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            ResourcePackService.class,
            LogContext.builder().put("subsystem", "resource-pack").build()
    );

    private final AtomicReference<CompletableFuture<ResourcePackInfo>> initialization = new AtomicReference<>();
    private volatile ResourcePackInfo resourcePackInfo;

    public CompletableFuture<ResourcePackInfo> initializeAsync() {
        CompletableFuture<ResourcePackInfo> existing = initialization.get();
        if (existing != null) {
            return existing;
        }

        CompletableFuture<ResourcePackInfo> created = new CompletableFuture<>();
        if (!initialization.compareAndSet(null, created)) {
            return initialization.get();
        }

        Thread.ofVirtual().name("resource-pack-builder").start(() -> {
            try {
                LOGGER.info("Starting resource pack building...");
                ResourcePackInfo info = buildResourcePackInfo();
                this.resourcePackInfo = info;
                if (info != null) {
                    LOGGER.info("Resource pack server initialized successfully");
                } else {
                    LOGGER.warn("Resource pack unavailable; clients will miss custom assets.");
                }
                created.complete(info);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize resource pack server", e);
                created.completeExceptionally(e);
            }
        });

        return created;
    }

    public ResourcePackInfo getInfo() {
        return resourcePackInfo;
    }

    public CompletableFuture<ResourcePackInfo> reloadAsync() {
        CompletableFuture<ResourcePackInfo> future = new CompletableFuture<>();
        Thread.ofVirtual().name("resource-pack-reloader").start(() -> {
            try {
                ResourcePackInfo newInfo = buildResourcePackInfo();
                if (newInfo == null) {
                    future.complete(null);
                    return;
                }
                this.resourcePackInfo = newInfo;
                future.complete(newInfo);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private ResourcePackInfo buildResourcePackInfo() {
        String packPathEnv = System.getenv("MOUD_RESOURCE_PACK_PATH");
        Path packPath;
        if (packPathEnv == null || packPathEnv.isBlank()) {
            packPath = ResourcePackBuilder.buildFromProjectAssets();
        } else {
            packPath = Path.of(packPathEnv);
        }
        if (packPath == null) {
            return null;
        }

        String bindHost = System.getenv("MOUD_RESOURCE_PACK_BIND_HOST");
        if (bindHost == null || bindHost.isBlank()) {
            bindHost = "0.0.0.0";
        }

        String publicHost = System.getenv("MOUD_RESOURCE_PACK_HOST");
        if (publicHost == null || publicHost.isBlank()) {
            try {
                publicHost = java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                publicHost = "127.0.0.1";
            }
        }

        int port = 0;
        try {
            String rawPort = System.getenv("MOUD_RESOURCE_PACK_PORT");
            if (rawPort != null && !rawPort.isBlank()) {
                port = Integer.parseInt(rawPort.trim());
            }
        } catch (NumberFormatException ignored) {
        }
        if (port <= 0) {
            port = 8777;
        }

        String urlPath = "/moud-resourcepack.zip";
        return ResourcePackServer.start(packPath, bindHost, publicHost, port, urlPath);
    }
}
