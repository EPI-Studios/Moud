package com.moud.client.rendering;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PostProcessingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostProcessingManager.class);
    private final Set<Identifier> activeEffects = new HashSet<>();
    private final PostEffectUniformManager uniformManager = new PostEffectUniformManager();

    public PostProcessingManager() {
        VeilEventPlatform.INSTANCE.preVeilPostProcessing((pipelineId, pipeline, context) -> {
            // This callback runs on the Render Thread
            synchronized (activeEffects) {
                if (activeEffects.contains(pipelineId)) {
                    uniformManager.applyUniforms(pipelineId, context);
                }
            }
        });
    }

    public void applyEffect(String effectId) {
        try {
            Identifier pipelineId = Identifier.tryParse(effectId);
            if (pipelineId == null) {
                LOGGER.error("Invalid Identifier format: {}", effectId);
                return;
            }

            // CRITICAL FIX: Ensure this runs on the main client thread
            MinecraftClient.getInstance().execute(() -> {
                synchronized (activeEffects) {
                    if (activeEffects.add(pipelineId)) {
                        try {
                            VeilRenderSystem.renderer().getPostProcessingManager().add(pipelineId);
                            LOGGER.info("Applied post-processing effect: {}", pipelineId);
                        } catch (Exception e) {
                            LOGGER.error("Failed to add Veil pipeline {}", pipelineId, e);
                            activeEffects.remove(pipelineId);
                        }
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to apply post-processing effect: {}", effectId, e);
        }
    }

    public void removeEffect(String effectId) {
        try {
            Identifier pipelineId = Identifier.tryParse(effectId);
            if (pipelineId == null) return;

            // CRITICAL FIX: Ensure this runs on the main client thread
            MinecraftClient.getInstance().execute(() -> {
                synchronized (activeEffects) {
                    if (activeEffects.remove(pipelineId)) {
                        VeilRenderSystem.renderer().getPostProcessingManager().remove(pipelineId);
                        LOGGER.info("Removed post-processing effect: {}", pipelineId);
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to remove post-processing effect: {}", effectId, e);
        }
    }

    public void clearAllEffects() {
        // CRITICAL FIX: Ensure this runs on the main client thread
        MinecraftClient.getInstance().execute(() -> {
            synchronized (activeEffects) {
                for (Identifier pipelineId : activeEffects) {
                    VeilRenderSystem.renderer().getPostProcessingManager().remove(pipelineId);
                }
                activeEffects.clear();
                LOGGER.info("Cleared all active post-processing effects.");
            }
        });
    }

    public void updateUniforms(String effectId, Map<String, Object> uniforms) {
        // This just updates a ConcurrentHashMap, safe to call from any thread
        uniformManager.updateUniforms(effectId, uniforms);
    }
}