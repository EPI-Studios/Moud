package com.moud.client.primitives;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

final class PrimitiveRenderLayers {
    private static final Map<LayerKey, RenderLayer> LAYER_CACHE = new ConcurrentHashMap<>();
    private static final RenderLayer LINE_LAYER = RenderLayer.getLines();
    private static final RenderLayer LINE_XRAY_LAYER = RenderLayer.of(
            "moud_primitive_lines_xray",
            VertexFormats.LINES,
            VertexFormat.DrawMode.LINES,
            256,
            false,
            true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.LINES_PROGRAM)
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(1.0)))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                    .build(false)
    );
    private PrimitiveRenderLayers() {
    }

    static RenderLayer getLayer(Identifier texture, boolean unlit, boolean doubleSided, boolean xray, boolean opaque) {
        LayerKey key = new LayerKey(texture, unlit, doubleSided, xray, opaque);
        return LAYER_CACHE.computeIfAbsent(key, PrimitiveRenderLayers::buildLayer);
    }

    static RenderLayer getLineLayer(boolean xray) {
        return xray ? LINE_XRAY_LAYER : LINE_LAYER;
    }

    private static RenderLayer buildLayer(LayerKey key) {
        boolean translucent = key.xray() || !key.opaque();
        RenderPhase.ShaderProgram program;
        if (key.unlit()) {
            program = RenderPhase.ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM;
        } else {
            program = translucent ? RenderPhase.ENTITY_TRANSLUCENT_PROGRAM : RenderPhase.ENTITY_CUTOUT_PROGRAM;
        }
        RenderPhase.Cull cull = key.doubleSided() ? RenderPhase.DISABLE_CULLING : RenderPhase.ENABLE_CULLING;
        RenderPhase.DepthTest depthTest = key.xray() ? RenderPhase.ALWAYS_DEPTH_TEST : RenderPhase.LEQUAL_DEPTH_TEST;
        RenderPhase.Lightmap lightmap = key.unlit() ? RenderPhase.DISABLE_LIGHTMAP : RenderPhase.ENABLE_LIGHTMAP;
        RenderPhase.Transparency transparency = translucent ? RenderPhase.TRANSLUCENT_TRANSPARENCY : RenderPhase.NO_TRANSPARENCY;
        RenderPhase.WriteMaskState writeMask = RenderPhase.ALL_MASK;

        RenderLayer.MultiPhaseParameters params = RenderLayer.MultiPhaseParameters.builder()
                .program(program)
                .texture(new RenderPhase.Texture(key.texture(), false, false))
                .transparency(transparency)
                .cull(cull)
                .lightmap(lightmap)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .depthTest(depthTest)
                .writeMaskState(writeMask)
                .build(true);

        return RenderLayer.of(
                "moud_primitive_" + key.texture().getPath() + "_" + (key.unlit() ? "unlit" : "lit"),
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.TRIANGLES,
                1024,
                true,
                true,
                params
        );
    }

    private record LayerKey(Identifier texture, boolean unlit, boolean doubleSided, boolean xray, boolean opaque) {
    }
}
