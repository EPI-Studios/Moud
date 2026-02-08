package com.moud.client.model;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.rendering.MeshBuffer;
import com.moud.client.rendering.ModelRenderLayers;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ModelRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRenderer.class);
    private static final boolean USE_VERTEX_BUFFERS = false;
    private static final String GLTF_BLEND_MODE_PROPERTY = "moud.gltf.blendMode";
    private static final Set<String> LOGGED_WARNINGS = ConcurrentHashMap.newKeySet();

    public void render(RenderableModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta) {
        if (consumers == null || !model.hasMeshData()) {
            return;
        }

        if (USE_VERTEX_BUFFERS && model.hasMeshBuffer()) {
            renderWithVertexBuffer(model, matrices, consumers, light, tickDelta);
        } else {

            renderImmediateMode(model, matrices, consumers, light, tickDelta);
        }
    }

    private void renderWithVertexBuffer(RenderableModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta) {
        MeshBuffer meshBuffer = model.getMeshBuffer();
        if (meshBuffer == null || !meshBuffer.isUploaded()) {
            return;
        }

        matrices.push();

        Quaternion interpolatedRot = model.getInterpolatedRotation(tickDelta);
        Quaternionf modelRotation = new Quaternionf(interpolatedRot.x, interpolatedRot.y, interpolatedRot.z, interpolatedRot.w);
        matrices.multiply(modelRotation);

        Vector3 scale = model.getInterpolatedScale(tickDelta);
        matrices.scale(scale.x, scale.y, scale.z);

        Identifier shaderId = Identifier.of("moud", "model_phong");
        ShaderProgram shader = VeilRenderSystem.renderer().getShaderManager().getShader(shaderId);

        if (shader != null) {
            RenderLayer renderLayer = ModelRenderLayers.getModelLayer(model.getTexture(), RenderableModel.AlphaMode.OPAQUE, true);

            renderLayer.startDrawing();

            shader.bind();

            Matrix4f modelViewMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
            Matrix4f projectionMatrix = new Matrix4f();
            projectionMatrix.setPerspective(
                    (float) Math.toRadians(70.0f),
                    (float) MinecraftClient.getInstance().getWindow().getFramebufferWidth() /
                            (float) MinecraftClient.getInstance().getWindow().getFramebufferHeight(),
                    0.05f,
                    1000.0f
            );

            shader.setDefaultUniforms(VertexFormat.DrawMode.TRIANGLES, modelViewMatrix, projectionMatrix);

            VertexBuffer vertexBuffer = meshBuffer.getVertexBuffer();
            vertexBuffer.bind();
            vertexBuffer.draw();
            VertexBuffer.unbind();

            ShaderProgram.unbind();

            renderLayer.endDrawing();
        }

        matrices.pop();
    }

    private void renderImmediateMode(RenderableModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta) {
        matrices.push();

        Quaternion interpolatedRot = model.getInterpolatedRotation(tickDelta);
        Quaternionf modelRotation = new Quaternionf(interpolatedRot.x, interpolatedRot.y, interpolatedRot.z, interpolatedRot.w);
        matrices.multiply(modelRotation);

        Vector3 scale = model.getInterpolatedScale(tickDelta);
        matrices.scale(scale.x, scale.y, scale.z);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();

        float[] vertices = model.getVerticesForRender(tickDelta);
        int[] indices = model.getIndices();

        if (vertices != null && indices != null) {
            final int stride = RenderableModel.FLOATS_PER_VERTEX;

            if (model.hasSubmeshes()) {
                for (RenderableModel.Submesh submesh : model.getSubmeshes()) {
                    if (submesh == null || submesh.indexCount() <= 0) {
                        continue;
                    }
                    Identifier baseColor = submesh.baseColorTexture() != null ? submesh.baseColorTexture() : model.getTexture();
                    Identifier normal = submesh.normalTexture();
                    Identifier metallicRoughness = submesh.metallicRoughnessTexture();
                    Identifier emissive = submesh.emissiveTexture();
                    Identifier occlusion = submesh.occlusionTexture();

                    RenderLayer layer = getGltfPbrLayerOrNull(
                            submesh.alphaMode(),
                            baseColor,
                            normal,
                            metallicRoughness,
                            emissive,
                            occlusion
                    );
                    boolean pbr = layer != null;
                    if (!pbr) {
                        String key = "pbr_layer_null:" + submesh.alphaMode() + ":" + baseColor + ":" + normal + ":" + metallicRoughness;
                        if (LOGGED_WARNINGS.add(key)) {
                            LOGGER.warn("PBR layer null for submesh, falling back to simple layer. Textures: base={}, norm={}, mr={}",
                                    baseColor, normal, metallicRoughness);
                        }
                        layer = ModelRenderLayers.getModelLayer(baseColor, submesh.alphaMode(), submesh.doubleSided());
                    }
                    VertexConsumer vertexConsumer = consumers.getBuffer(layer);

                    int r = toColor255(submesh.baseColorR());
                    int g = toColor255(submesh.baseColorG());
                    int b = toColor255(submesh.baseColorB());
                    int a = toColor255(submesh.baseColorA());

                    final int overlayUV = pbr
                            ? packUnorm4(submesh.metallicFactor()) | (packUnorm4(submesh.roughnessFactor()) << 16)
                            : OverlayTexture.DEFAULT_UV;

                    int start = Math.max(0, submesh.indexStart());
                    int end = Math.min(indices.length, start + submesh.indexCount());
                    for (int i = start; i < end; i++) {
                        int base = indices[i] * stride;

                        float x = vertices[base];
                        float y = vertices[base + 1];
                        float z = vertices[base + 2];
                        float u = vertices[base + 3];
                        float v = vertices[base + 4];
                        float nx = vertices[base + 5];
                        float ny = vertices[base + 6];
                        float nz = vertices[base + 7];

                        vertexConsumer.vertex(positionMatrix, x, y, z)
                                .color(r, g, b, a)
                                .texture(u, v)
                                .overlay(overlayUV)
                                .light(light)
                                .normal(entry, nx, ny, nz);
                    }
                }
            } else {
                final int overlayUV = OverlayTexture.DEFAULT_UV;
                RenderLayer renderLayer = ModelRenderLayers.getModelLayer(model.getTexture(), RenderableModel.AlphaMode.OPAQUE, true);
                VertexConsumer vertexConsumer = consumers.getBuffer(renderLayer);

                for (int i = 0; i < indices.length; i++) {
                    int base = indices[i] * stride;

                    float x = vertices[base];
                    float y = vertices[base + 1];
                    float z = vertices[base + 2];
                    float u = vertices[base + 3];
                    float v = vertices[base + 4];
                    float nx = vertices[base + 5];
                    float ny = vertices[base + 6];
                    float nz = vertices[base + 7];

                    vertexConsumer.vertex(positionMatrix, x, y, z)
                            .color(255, 255, 255, 255)
                            .texture(u, v)
                            .overlay(overlayUV)
                            .light(light)
                            .normal(entry, nx, ny, nz);
                }
            }
        }

        matrices.pop();
    }

    private static int toColor255(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        return (int) (clamped * 255.0f + 0.5f);
    }

    private static int packUnorm4(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        return (int) (clamped * 15.0f + 0.5f) & 0xFFFF;
    }

    private static RenderLayer getGltfPbrLayerOrNull(RenderableModel.AlphaMode alphaMode,
                                                     Identifier baseColor,
                                                     Identifier normal,
                                                     Identifier metallicRoughness,
                                                     Identifier emissive,
                                                     Identifier occlusion) {
        RenderableModel.AlphaMode resolvedAlphaMode = resolveGltfPbrAlphaMode(alphaMode);
        Identifier renderTypeId = switch (resolvedAlphaMode) {
            case MASK -> Identifier.of("moud", "gltf/pbr_mask");
            case BLEND -> Identifier.of("moud", "gltf/pbr_blend");
            case OPAQUE -> Identifier.of("moud", "gltf/pbr_opaque");
        };
        try {
            return VeilRenderType.get(renderTypeId,
                    baseColor,
                    normal,
                    metallicRoughness,
                    emissive,
                    occlusion);
        } catch (Throwable e) {
            String key = "pbr_layer_exception:" + renderTypeId;
            if (LOGGED_WARNINGS.add(key)) {
                LOGGER.error("Failed to create PBR render layer: {}", renderTypeId, e);
            }
            return null;
        }
    }

    private static RenderableModel.AlphaMode resolveGltfPbrAlphaMode(RenderableModel.AlphaMode alphaMode) {
        RenderableModel.AlphaMode resolved = alphaMode != null ? alphaMode : RenderableModel.AlphaMode.OPAQUE;
        if (resolved != RenderableModel.AlphaMode.BLEND) {
            return resolved;
        }

        String blendMode = System.getProperty(GLTF_BLEND_MODE_PROPERTY, "blend").trim().toLowerCase();
        return switch (blendMode) {
            case "mask" -> {
                if (LOGGED_WARNINGS.add("blend_mode:mask")) {
                    LOGGER.warn("{}=mask: treating glTF BLEND materials as MASK for deferred PBR.", GLTF_BLEND_MODE_PROPERTY);
                }
                yield RenderableModel.AlphaMode.MASK;
            }
            case "opaque" -> {
                if (LOGGED_WARNINGS.add("blend_mode:opaque")) {
                    LOGGER.warn("{}=opaque: treating glTF BLEND materials as OPAQUE for deferred PBR.", GLTF_BLEND_MODE_PROPERTY);
                }
                yield RenderableModel.AlphaMode.OPAQUE;
            }
            default -> RenderableModel.AlphaMode.BLEND;
        };
    }
}
