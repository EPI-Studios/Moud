package com.moud.client.rendering;

import com.moud.client.api.service.RenderTypeDefinition;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomRenderTypeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomRenderTypeManager.class);
    private final Map<Integer, Identifier> renderTypeCache = new ConcurrentHashMap<>();

    public Identifier getOrCreate(RenderTypeDefinition definition) {
        int hash = definition.hashCode();

        return renderTypeCache.computeIfAbsent(hash, k -> {
            try {
                return createShaderReference(definition);
            } catch (Exception e) {
                LOGGER.error("Failed to create shader reference", e);
                throw new RuntimeException("Failed to create shader reference", e);
            }
        });
    }

    private Identifier createShaderReference(RenderTypeDefinition definition) {
        Identifier shaderId = definition.shader();

        LOGGER.debug("Created shader reference: {}", shaderId);
        return shaderId;
    }

    public void clearCache() {
        renderTypeCache.clear();
    }
}