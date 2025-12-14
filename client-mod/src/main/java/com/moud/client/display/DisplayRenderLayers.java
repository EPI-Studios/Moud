package com.moud.client.display;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import java.util.function.Function;
final class DisplayRenderLayers {
    private static final Function<Identifier, RenderLayer> LAYER_CACHE =
            Util.memoize(texture -> RenderLayer.getEntityTranslucent(texture, true));
    private static final Function<Identifier, RenderLayer> ON_TOP_CACHE =
            Util.memoize(DisplayRenderLayers::createOnTopLayer);
    private DisplayRenderLayers() {
    }
    static RenderLayer getLayer(Identifier texture) {
        return LAYER_CACHE.apply(texture);
    }
    static RenderLayer getOnTopLayer(Identifier texture) {
        return ON_TOP_CACHE.apply(texture);
    }
    private static RenderLayer createOnTopLayer(Identifier texture) {
        RenderLayer.MultiPhaseParameters params = RenderLayer.MultiPhaseParameters.builder()
                .texture(new RenderPhase.Texture(texture, false, false))
                .program(RenderPhase.ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
                .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .cull(RenderPhase.DISABLE_CULLING)
                .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
                .writeMaskState(RenderPhase.COLOR_MASK)
                .build(true);
        return RenderLayer.of(
                "moud_display_on_top_" + texture.getPath(),
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS,
                256,
                true,
                true,
                params
        );
    }
}