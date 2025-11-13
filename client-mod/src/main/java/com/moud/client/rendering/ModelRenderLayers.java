package com.moud.client.rendering;

import foundry.veil.api.client.render.rendertype.VeilRenderType;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelRenderLayers {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRenderLayers.class);
    public static final boolean ENABLE_BLOOM = true;

    private static final Identifier EMISSIVE_DISPLAY_RENDERTYPE_ID = Identifier.of("moud", "emissive_display");

    public static RenderLayer getModelLayer(Identifier texture) {
        if (ENABLE_BLOOM) {
            try {
                RenderLayer layer = VeilRenderType.get(EMISSIVE_DISPLAY_RENDERTYPE_ID, texture);
                if (layer != null) {
                    return layer;
                }
            } catch (Throwable t) {
                LOGGER.warn("Failed to load Veil bloom render type, falling back to standard rendering.", t);
            }
        }

        return RenderLayer.getEntityTranslucent(texture, true);
    }
}