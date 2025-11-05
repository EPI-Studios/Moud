package com.moud.client.display;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

final class DisplayRenderLayers {
    private static final String LAYER_NAME = "moud_display_quads";

    private static final Function<Identifier, RenderLayer> QUAD_LAYER = Util.memoize(texture -> {
        RenderLayer.MultiPhaseParameters parameters = RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.ENTITY_TRANSLUCENT_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .cull(RenderPhase.DISABLE_CULLING)
                .build(true);

        return RenderLayer.of(
                LAYER_NAME,
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS,
                RenderLayer.DEFAULT_BUFFER_SIZE,
                true,
                false,
                parameters
        );
    });

    private DisplayRenderLayers() {
    }

    static RenderLayer getLayer(Identifier texture) {
        return QUAD_LAYER.apply(texture);
    }
}
