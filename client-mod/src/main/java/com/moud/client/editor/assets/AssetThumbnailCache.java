package com.moud.client.editor.assets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
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

    private final Map<String, Integer> textureIds = new ConcurrentHashMap<>();

    private AssetThumbnailCache() {}

    public static AssetThumbnailCache getInstance() {
        return INSTANCE;
    }

    public int getTextureId(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return 0;
        }
        String normalized = resourcePath.trim().toLowerCase(Locale.ROOT);
        return textureIds.computeIfAbsent(normalized, this::loadTexture);
    }

    private int loadTexture(String path) {
        Identifier identifier = parseIdentifier(path);
        if (identifier == null) {
            return 0;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return 0;
        }
        AbstractTexture texture = client.getTextureManager().getTexture(identifier);
        if (texture == null) {
            ResourceTexture resourceTexture = new ResourceTexture(identifier);
            try {
                resourceTexture.load(client.getResourceManager());
            } catch (IOException e) {
                LOGGER.debug("Failed to load texture {}", identifier, e);
                return 0;
            }
            client.getTextureManager().registerTexture(identifier, resourceTexture);
            texture = resourceTexture;
        }
        return texture.getGlId();
    }

    private Identifier parseIdentifier(String path) {
        Identifier identifier = Identifier.tryParse(path);
        if (identifier != null) {
            return identifier;
        }
        if (path.contains(":")) {
            return null;
        }
        return Identifier.of("moud", path);
    }
}
