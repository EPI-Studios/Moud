package com.moud.client.rendering;

import com.moud.client.model.RenderableModel;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelRenderLayers {

    private static final Map<LayerKey, RenderLayer> LAYER_CACHE = new ConcurrentHashMap<>();

    private ModelRenderLayers() {
    }

    public static RenderLayer getModelLayer(Identifier texture, RenderableModel.AlphaMode alphaMode, boolean doubleSided) {
        LayerKey key = new LayerKey(
                texture,
                alphaMode != null ? alphaMode : RenderableModel.AlphaMode.OPAQUE,
                doubleSided
        );
        return LAYER_CACHE.computeIfAbsent(key, ModelRenderLayers::buildLayer);
    }

    private static RenderLayer buildLayer(LayerKey key) {
        boolean translucent = key.alphaMode() == RenderableModel.AlphaMode.BLEND;
        boolean cutout = key.alphaMode() == RenderableModel.AlphaMode.MASK;
        RenderPhase.ShaderProgram program = translucent
                ? RenderPhase.ENTITY_TRANSLUCENT_PROGRAM
                : (cutout ? RenderPhase.ENTITY_CUTOUT_PROGRAM : RenderPhase.ENTITY_SOLID_PROGRAM);
        RenderPhase.Transparency transparency = translucent ? RenderPhase.TRANSLUCENT_TRANSPARENCY : RenderPhase.NO_TRANSPARENCY;
        RenderPhase.Cull cull = key.doubleSided() ? RenderPhase.DISABLE_CULLING : RenderPhase.ENABLE_CULLING;
        RenderPhase.WriteMaskState writeMask = translucent ? RenderPhase.COLOR_MASK : RenderPhase.ALL_MASK;

        RenderLayer.MultiPhaseParameters params = RenderLayer.MultiPhaseParameters.builder()
                .program(program)
                .texture(new RenderPhase.Texture(key.texture(), false, false))
                .transparency(transparency)
                .cull(cull)
                .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                .writeMaskState(writeMask)
                .build(true);

        return RenderLayer.of(
                "moud_model_triangles_" + key.alphaMode().name().toLowerCase() + "_" + (key.doubleSided() ? "nocull" : "cull"),
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.TRIANGLES,
                4096,
                true,
                translucent,
                params
        );
    }

    private record LayerKey(Identifier texture, RenderableModel.AlphaMode alphaMode, boolean doubleSided) {
    }
}
