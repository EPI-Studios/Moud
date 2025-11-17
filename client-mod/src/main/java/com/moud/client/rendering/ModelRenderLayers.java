package com.moud.client.rendering;

import foundry.veil.api.client.render.rendertype.VeilRenderType;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public final class ModelRenderLayers {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRenderLayers.class);
    private static final Identifier MODEL_RENDERTYPE_ID = Identifier.of("moud", "model");
    private static final String FALLBACK_LAYER_NAME = "moud_model_triangles";

    private static final Function<Identifier, RenderLayer> TRIANGLE_LAYER = Util.memoize(texture -> {
        RenderLayer.MultiPhaseParameters parameters = RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.ENTITY_TRANSLUCENT_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .cull(RenderPhase.DISABLE_CULLING)
                .build(true);

        return RenderLayer.of(
                FALLBACK_LAYER_NAME,
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.TRIANGLES,
                RenderLayer.DEFAULT_BUFFER_SIZE,
                true,
                false,
                parameters
        );
    });

    private ModelRenderLayers() {
    }

    public static RenderLayer getModelLayer(Identifier texture) {
        try {
            RenderLayer veilLayer = VeilRenderType.get(MODEL_RENDERTYPE_ID, texture);
            if (veilLayer != null) {
                return veilLayer;
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to load Veil model render type, falling back to triangle render layer.", t);
        }
        return TRIANGLE_LAYER.apply(texture);
    }
}
