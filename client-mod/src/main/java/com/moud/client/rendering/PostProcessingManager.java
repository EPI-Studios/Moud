package com.moud.client.rendering;
import foundry.veil.api.client.render.VeilRenderSystem;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashSet;
import java.util.Set;

public final class PostProcessingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostProcessingManager.class);
    private final Set<Identifier> activeEffects = new HashSet<>();

    /**
     * Enable a Veil's post-processing pipeline
     *
     * @param effectId Effect identifier (ex: "mygame:my_cool_effect").
     */
    public void applyEffect(String effectId) {
        try {
            Identifier pipelineId = Identifier.tryParse(effectId);
            if (pipelineId == null) {
                LOGGER.error("Invalid Identifier format: {}", effectId);
                return;
            }

            synchronized (activeEffects) {
                if (activeEffects.add(pipelineId)) {
                    VeilRenderSystem.renderer().getPostProcessingManager().add(pipelineId);
                    LOGGER.debug("Applied post-processing effect: {}", pipelineId);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to apply post-processing effect with invalid ID: {}", effectId, e);
        }
    }

    /**
     * Disable a Veil's post-processing pipeline
     *
     * @param effectId Effect identifier to remove
     */
    public void removeEffect(String effectId) {
        try {
            Identifier pipelineId = Identifier.tryParse(effectId);
            if (pipelineId == null) {
                LOGGER.error("Invalid Identifier format: {}", effectId);
                return;
            }

            synchronized (activeEffects) {
                if (activeEffects.remove(pipelineId)) {
                    VeilRenderSystem.renderer().getPostProcessingManager().remove(pipelineId);
                    LOGGER.debug("Removed post-processing effect: {}", pipelineId);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to remove post-processing effect with invalid ID: {}", effectId, e);
        }
    }

    public void clearAllEffects() {
        synchronized (activeEffects) {
            for (Identifier pipelineId : activeEffects) {
                VeilRenderSystem.renderer().getPostProcessingManager().remove(pipelineId);
            }
            activeEffects.clear();
            LOGGER.info("Cleared all active post-processing effects.");
        }
    }
}
