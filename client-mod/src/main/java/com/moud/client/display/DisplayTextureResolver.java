package com.moud.client.display;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class DisplayTextureResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayTextureResolver.class);
    private static final DisplayTextureResolver INSTANCE = new DisplayTextureResolver();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Map<String, RemoteTextureHandle> remoteTextures = new ConcurrentHashMap<>();
    private final AtomicInteger dynamicCounter = new AtomicInteger();

    private DisplayTextureResolver() {
    }

    static DisplayTextureResolver getInstance() {
        return INSTANCE;
    }

    Identifier normalize(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String trimmed = path.trim();
        if (!trimmed.contains(":") && !trimmed.startsWith("http")) {
            if (trimmed.startsWith("moud/")) {
                trimmed = "moud:" + trimmed.substring(5);
            } else {
                trimmed = "moud:" + trimmed;
            }
        }

        Identifier identifier = Identifier.tryParse(trimmed);
        if (identifier == null) {
            LOGGER.warn("Failed to parse texture identifier '{}'", trimmed);
        }
        return identifier;
    }

    CompletableFuture<Identifier> acquireRemote(String source) {
        RemoteTextureHandle handle = remoteTextures.compute(source, (key, existing) -> {
            if (existing != null) {
                existing.retain();
                return existing;
            }
            RemoteTextureHandle created = new RemoteTextureHandle();
            created.retain();
            created.future = downloadAndRegister(source, created)
                    .whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            LOGGER.error("Failed to download texture from {}", source, throwable);
                        }
                    });
            return created;
        });

        return handle.future;
    }

    void releaseRemote(String source) {
        RemoteTextureHandle handle = remoteTextures.get(source);
        if (handle == null) {
            return;
        }

        if (handle.release() == 0) {
            remoteTextures.remove(source);
            handle.future.whenComplete((identifier, throwable) -> {
                if (identifier == null) {
                    return;
                }
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> {
                    TextureManager textureManager = client.getTextureManager();
                    textureManager.destroyTexture(identifier);
                    if (handle.texture != null) {
                        handle.texture.close();
                    }
                });
            });
        }
    }

    void clear() {
        remoteTextures.forEach((source, handle) -> handle.future.whenComplete((id, throwable) -> {
            if (id == null) {
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                TextureManager textureManager = client.getTextureManager();
                textureManager.destroyTexture(id);
                if (handle.texture != null) {
                    handle.texture.close();
                }
            });
        }));
        remoteTextures.clear();
    }

    private CompletableFuture<Identifier> downloadAndRegister(String source, RemoteTextureHandle handle) {
        if (source.startsWith("data:")) {
            return decodeDataUrl(source).thenCompose(data -> registerTexture(data, handle));
        }

        return CompletableFuture.supplyAsync(() -> fetchBytes(source))
                .thenCompose(data -> registerTexture(data, handle));
    }

    private CompletableFuture<byte[]> decodeDataUrl(String dataUrl) {
        return CompletableFuture.supplyAsync(() -> {
            int commaIndex = dataUrl.indexOf(',');
            if (commaIndex == -1) {
                throw new IllegalArgumentException("Invalid data URL format");
            }
            String metadata = dataUrl.substring(5, commaIndex);
            String encoded = dataUrl.substring(commaIndex + 1);
            if (metadata.endsWith(";base64")) {
                return Base64.getDecoder().decode(encoded);
            }
            return encoded.getBytes(StandardCharsets.UTF_8);
        });
    }

    private byte[] fetchBytes(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            throw new IllegalStateException("Unexpected status code " + response.statusCode());
        } catch (Exception e) {
            throw new RuntimeException("Failed to download texture from " + url, e);
        }
    }

    private CompletableFuture<Identifier> registerTexture(byte[] data, RemoteTextureHandle handle) {
        CompletableFuture<Identifier> future = new CompletableFuture<>();
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                Identifier identifier = Identifier.of("moud", "display/remote_" + dynamicCounter.incrementAndGet());
                TextureManager textureManager = client.getTextureManager();
                textureManager.registerTexture(identifier, texture);
                handle.texture = texture;
                handle.textureId = identifier;
                future.complete(identifier);
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private static final class RemoteTextureHandle {
        private CompletableFuture<Identifier> future = CompletableFuture.completedFuture(null);
        private final AtomicInteger refCount = new AtomicInteger(0);
        private NativeImageBackedTexture texture;
        private Identifier textureId;

        void retain() {
            refCount.incrementAndGet();
        }

        int release() {
            return refCount.decrementAndGet();
        }
    }
}
