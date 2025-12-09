package com.moud.client.editor.assets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AssetThumbnailCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("AssetThumbnailCache");
    private static final AssetThumbnailCache INSTANCE = new AssetThumbnailCache();

    private final Map<String, Identifier> identifiers = new ConcurrentHashMap<>();

    private AssetThumbnailCache() {}

    public static AssetThumbnailCache getInstance() {
        return INSTANCE;
    }

    public int getTextureId(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return 0;
        }
        String key = normalizeKey(resourcePath);
        if (key.isEmpty()) {
            return 0;
        }
        Identifier identifier = identifiers.computeIfAbsent(key, this::parseIdentifier);
        if (identifier == null) {
            return 0;
        }
        return resolveGlId(identifier);
    }

    public void clear() {
        identifiers.clear();
    }

    private int resolveGlId(Identifier identifier) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return 0;
        }
        try {
            AbstractTexture texture = client.getTextureManager().getTexture(identifier);
            if (texture == null) {
                ResourceTexture resourceTexture = new ResourceTexture(identifier);
                resourceTexture.load(client.getResourceManager());
                client.getTextureManager().registerTexture(identifier, resourceTexture);
                texture = resourceTexture;
            }
            if (texture.getGlId() == 0) {
                texture.bindTexture();
            }
            return texture.getGlId();
        } catch (IOException e) {
            LOGGER.debug("Failed to load texture {}", identifier, e);
            return 0;
        } catch (Throwable t) {
            LOGGER.warn("Unexpected error resolving thumbnail texture {}", identifier, t);
            return 0;
        }
    }

    private String normalizeKey(String path) {
        String trimmed = path.trim().replace('\\', '/');
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.startsWith("assets/")) {
            trimmed = trimmed.substring("assets/".length());
        }
        return trimmed;
    }

    private Identifier parseIdentifier(String path) {
        String candidate = path;
        if (!candidate.contains(":") && candidate.contains("/")) {
            int slash = candidate.indexOf('/');
            String namespace = candidate.substring(0, slash);
            String remainder = candidate.substring(slash + 1);
            if (!namespace.isBlank() && !remainder.isBlank()) {
                candidate = namespace + ":" + remainder;
            }
        }
        Identifier parsed = Identifier.tryParse(candidate);
        if (parsed != null) {
            return parsed;
        }
        if (candidate.contains(":")) {
            return null;
        }
        return Identifier.of("moud", candidate);
    }
}
