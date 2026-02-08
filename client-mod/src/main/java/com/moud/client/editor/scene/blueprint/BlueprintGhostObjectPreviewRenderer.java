package com.moud.client.editor.scene.blueprint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.model.ModelRenderer;
import com.moud.client.model.RenderableModel;
import com.moud.client.util.IdentifierUtils;
import com.moud.client.util.OBJLoader;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class BlueprintGhostObjectPreviewRenderer implements WorldRenderEvents.AfterEntities {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlueprintGhostObjectPreviewRenderer.class);
    private static final BlueprintGhostObjectPreviewRenderer INSTANCE = new BlueprintGhostObjectPreviewRenderer();

    private static final int COLOR_MODEL_LABEL = 0xFF88B7FF;
    private static final int COLOR_LIGHT_LABEL = 0xFFE6B000;
    private static final int COLOR_EMITTER_LABEL = 0xFFB060FF;
    private static final int COLOR_CAMERA_LABEL = 0xFF4CE6F2;
    private static final int COLOR_DISPLAY_LABEL = 0xFF4C94F2;
    private static final int COLOR_GENERIC_LABEL = 0xFFB0B6BD;
    private static final int COLOR_MARKER_LABEL = 0xFFFFD66B;

    private static final int LIGHT_FULL_BRIGHT = 0x00F000F0;
    private static final String DEFAULT_TEXTURE = "minecraft:textures/block/white_concrete.png";

    private final ModelRenderer modelRenderer = new ModelRenderer();
    private final Map<String, RenderableModel> modelCache = new HashMap<>();
    private final Map<String, Boolean> modelLoadScheduled = new HashMap<>();

    private boolean loggedError;

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(INSTANCE);
    }

    private BlueprintGhostObjectPreviewRenderer() {}

    @Override
    public void afterEntities(WorldRenderContext context) {
        try {
            renderInternal(context);
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                LOGGER.error("Blueprint ghost preview rendering failed", t);
            }
        }
    }

    private void renderInternal(WorldRenderContext context) {
        BlueprintPreviewManager.PreviewState preview = BlueprintPreviewManager.getInstance().getCurrent();
        if (preview == null || preview.blueprint == null) return;

        var objects = preview.blueprint.objects;
        boolean hasObjects = objects != null && !objects.isEmpty();
        var markers = preview.blueprint.markers;
        boolean hasMarkers = markers != null && !markers.isEmpty();

        if (!hasObjects && !hasMarkers) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        VertexConsumerProvider consumers = context.consumers();
        VertexConsumerProvider.Immediate textConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        if (consumers == null) consumers = textConsumers;

        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        float tickDelta = context.tickCounter().getTickDelta(true);
        Quaternionf labelRotation = context.camera().getRotation();

        boolean schematic = preview.blueprint.blocks != null;
        int rotationSteps = schematic ? Math.floorMod(Math.round(preview.rotation[1] / 90.0f), 4) : 0;
        boolean mirrorX = schematic && preview.scale[0] < 0.0f;
        boolean mirrorZ = schematic && preview.scale[2] < 0.0f;

        Vec3d origin = new Vec3d(
                schematic ? Math.floor(preview.position[0]) : preview.position[0],
                schematic ? Math.floor(preview.position[1]) : preview.position[1],
                schematic ? Math.floor(preview.position[2]) : preview.position[2]
        );

        VertexConsumerProvider tintedConsumers = new TintedVertexConsumerProvider(consumers, 1.0f, 0.25f, 0.65f, 0.75f, 1.10f);
        TextRenderer textRenderer = client.textRenderer;

        BufferBuilder markerBuffer = null;
        boolean pendingMarkerDraw = false;

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        if (hasObjects) {
            for (BlueprintObject obj : objects) {
                if (obj == null) continue;

                Vec3d localPos = new Vec3d(obj.position[0], obj.position[1], obj.position[2]);
                Vec3d worldPos = transformPosition(localPos, preview, schematic, rotationSteps, mirrorX, mirrorZ).add(origin);
                String type = obj.type != null ? obj.type.toLowerCase(Locale.ROOT) : "";

                switch (type) {
                    case "model" -> {
                        renderGhostModel(obj, preview, matrices, tintedConsumers, tickDelta, worldPos, schematic, rotationSteps, mirrorX, mirrorZ);
                        renderLabel(textRenderer, textConsumers, matrices, labelRotation, worldPos, getLabel(obj, "Model"), COLOR_MODEL_LABEL);
                    }
                    case "light" -> {
                        renderLabel(textRenderer, textConsumers, matrices, labelRotation, worldPos, getLabel(obj, "Light"), COLOR_LIGHT_LABEL);
                        markerBuffer = ensureBuffer(markerBuffer);
                        addMarkerGeometry(markerBuffer, matrices, worldPos, 1.0f, 0.82f, 0.48f);
                        pendingMarkerDraw = true;
                    }
                    case "particle_emitter" -> renderLabel(textRenderer, textConsumers, matrices, labelRotation, worldPos, getLabel(obj, "Emitter"), COLOR_EMITTER_LABEL);
                    case "camera" -> renderLabel(textRenderer, textConsumers, matrices, labelRotation, worldPos, getLabel(obj, "Camera"), COLOR_CAMERA_LABEL);
                    case "display" -> renderLabel(textRenderer, textConsumers, matrices, labelRotation, worldPos, getLabel(obj, "Display"), COLOR_DISPLAY_LABEL);
                    default -> {
                        if (!type.isBlank() || (obj.label != null && !obj.label.isBlank())) {
                            markerBuffer = ensureBuffer(markerBuffer);
                            addMarkerGeometry(markerBuffer, matrices, worldPos, 0.60f, 0.65f, 0.70f);
                            pendingMarkerDraw = true;
                            renderLabel(textRenderer, textConsumers, matrices, labelRotation, worldPos, getLabel(obj, obj.type), COLOR_GENERIC_LABEL);
                        }
                    }
                }
            }
        }

        if (hasMarkers) {
            for (BlueprintMarker marker : markers) {
                if (marker == null || marker.position == null || marker.position.length < 3) continue;

                Vec3d localPos = new Vec3d(marker.position[0], marker.position[1], marker.position[2]);
                Vec3d worldPos = transformPosition(localPos, preview, schematic, rotationSteps, mirrorX, mirrorZ).add(origin);

                markerBuffer = ensureBuffer(markerBuffer);
                addMarkerGeometry(markerBuffer, matrices, worldPos, 0.95f, 0.86f, 0.30f);
                pendingMarkerDraw = true;

                String label = (marker.name != null && !marker.name.isBlank()) ? marker.name : "Marker";
                renderLabel(textRenderer, textConsumers, matrices, labelRotation, worldPos, label, COLOR_MARKER_LABEL);
            }
        }

        matrices.pop();

        textConsumers.draw();

        if (pendingMarkerDraw && markerBuffer != null) {
            drawMarkers(markerBuffer);
        }
    }

    private void renderGhostModel(BlueprintObject obj, BlueprintPreviewManager.PreviewState preview, MatrixStack matrices,
                                  VertexConsumerProvider consumers, float tickDelta, Vec3d pos,
                                  boolean schematic, int rotationSteps, boolean mirrorX, boolean mirrorZ) {
        if (obj.modelPath == null || obj.modelPath.isBlank()) return;

        String texture = obj.texture != null ? obj.texture : DEFAULT_TEXTURE;
        RenderableModel model = getOrLoadModel(obj.modelPath, texture);
        if (model == null || !model.hasMeshData()) return;

        float pitch = getVectorComponent(obj.rotation, 0);
        float yaw = getVectorComponent(obj.rotation, 1);
        float roll = getVectorComponent(obj.rotation, 2);

        if (schematic) {
            yaw += rotationSteps * 90.0f;
        } else {
            pitch += preview.rotation[0];
            yaw += preview.rotation[1];
            roll += preview.rotation[2];
        }

        float sx = getVectorComponent(obj.scale, 0, 1f);
        float sy = getVectorComponent(obj.scale, 1, 1f);
        float sz = getVectorComponent(obj.scale, 2, 1f);

        if (schematic) {
            sx *= mirrorX ? -1f : 1f;
            sz *= mirrorZ ? -1f : 1f;
        } else {
            sx *= preview.scale[0];
            sy *= preview.scale[1];
            sz *= preview.scale[2];
        }

        Quaternion rot = Quaternion.fromEuler(pitch, yaw, roll).normalize();
        model.updateTransform(Vector3.zero(), rot, new Vector3(sx, sy, sz));

        matrices.push();
        matrices.translate(pos.x, pos.y, pos.z);
        modelRenderer.render(model, matrices, consumers, LIGHT_FULL_BRIGHT, tickDelta);
        matrices.pop();
    }

    private RenderableModel getOrLoadModel(String path, String texture) {
        String key = path + "|" + texture;
        RenderableModel cached = modelCache.get(key);
        if (cached != null) return cached;

        long id = 0x7FFFL << 48 | (key.hashCode() & 0xFFFFFFFFL);
        RenderableModel model = new RenderableModel(id, path);

        Identifier textureId = Identifier.tryParse(texture);
        if (textureId != null) model.setTexture(textureId);

        modelCache.put(key, model);
        scheduleAsyncLoad(key, path, model);
        return model;
    }

    private void scheduleAsyncLoad(String key, String path, RenderableModel model) {
        if (modelLoadScheduled.putIfAbsent(key, Boolean.TRUE) != null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Identifier modelId = IdentifierUtils.resolveModelIdentifier(path);
        if (modelId == null) return;

        client.execute(() -> {
            try {
                Optional<Resource> resource = client.getResourceManager().getResource(modelId);
                if (resource.isPresent()) {
                    try (InputStream in = resource.get().getInputStream()) {
                        model.uploadMesh(OBJLoader.load(in));
                    }
                } else {
                    LOGGER.warn("Missing blueprint model resource: {}", modelId);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load blueprint model {}: {}", modelId, e.getMessage());
            }
        });
    }

    private void renderLabel(TextRenderer renderer, VertexConsumerProvider consumers, MatrixStack matrices, Quaternionf rotation,
                             Vec3d pos, String text, int color) {
        if (text == null || text.isBlank()) return;

        matrices.push();
        matrices.translate(pos.x, pos.y + 0.45, pos.z);
        matrices.multiply(rotation);

        float scale = 0.025f;
        matrices.scale(-scale, -scale, scale);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float xOffset = -renderer.getWidth(text) / 2.0f;

        renderer.draw(text, xOffset, 0.0f, color, false, matrix, consumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0x80000000, LIGHT_FULL_BRIGHT);
        matrices.pop();
    }

    private void drawMarkers(BufferBuilder buffer) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
            built.close();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private BufferBuilder ensureBuffer(BufferBuilder buffer) {
        return buffer != null ? buffer : Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
    }

    private void addMarkerGeometry(BufferBuilder buffer, MatrixStack matrices, Vec3d pos, float r, float g, float b) {
        matrices.push();
        matrices.translate(pos.x, pos.y, pos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float min = -0.15f;
        float max = 0.15f;
        float h = 0.30f;
        float a = 0.95f;

        addLine(buffer, mat, min, 0, min, max, 0, min, r, g, b, a);
        addLine(buffer, mat, max, 0, min, max, 0, max, r, g, b, a);
        addLine(buffer, mat, max, 0, max, min, 0, max, r, g, b, a);
        addLine(buffer, mat, min, 0, max, min, 0, min, r, g, b, a);

        addLine(buffer, mat, min, h, min, max, h, min, r, g, b, a);
        addLine(buffer, mat, max, h, min, max, h, max, r, g, b, a);
        addLine(buffer, mat, max, h, max, min, h, max, r, g, b, a);
        addLine(buffer, mat, min, h, max, min, h, min, r, g, b, a);

        addLine(buffer, mat, min, 0, min, min, h, min, r, g, b, a);
        addLine(buffer, mat, max, 0, min, max, h, min, r, g, b, a);
        addLine(buffer, mat, max, 0, max, max, h, max, r, g, b, a);
        addLine(buffer, mat, min, 0, max, min, h, max, r, g, b, a);

        addLine(buffer, mat, 0, 0, 0, 0, h, 0, r, g, b, a);

        matrices.pop();
    }

    private void addLine(BufferBuilder buffer, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a);
    }

    private Vec3d transformPosition(Vec3d local, BlueprintPreviewManager.PreviewState state, boolean schematic, int steps, boolean mirrorX, boolean mirrorZ) {
        if (schematic) {
            return BlueprintSchematicTransform.transformPosition(
                    state.blueprint.blocks,
                    local.x, local.y, local.z,
                    steps, mirrorX, mirrorZ
            );
        }
        return transformGeneric(local, state);
    }

    private Vec3d transformGeneric(Vec3d local, BlueprintPreviewManager.PreviewState state) {
        double sx = local.x * state.scale[0];
        double sy = local.y * state.scale[1];
        double sz = local.z * state.scale[2];

        double radP = Math.toRadians(state.rotation[0]);
        double radY = Math.toRadians(state.rotation[1]);
        double radR = Math.toRadians(state.rotation[2]);

        double cR = Math.cos(radR), sR = Math.sin(radR);
        double cP = Math.cos(radP), sP = Math.sin(radP);
        double cY = Math.cos(radY), sY = Math.sin(radY);

        double x1 = sx * cR - sy * sR;
        double y1 = sx * sR + sy * cR;
        double z1 = sz;

        double x2 = x1;
        double y2 = y1 * cP - z1 * sP;
        double z2 = y1 * sP + z1 * cP;

        double x3 = x2 * cY + z2 * sY;
        double y3 = y2;
        double z3 = -x2 * sY + z2 * cY;

        return new Vec3d(x3, y3, z3);
    }

    private float getVectorComponent(float[] vec, int index) {
        return (vec != null && vec.length > index) ? vec[index] : 0f;
    }

    private float getVectorComponent(float[] vec, int index, float def) {
        return (vec != null && vec.length > index) ? vec[index] : def;
    }

    private String getLabel(BlueprintObject obj, String fallback) {
        return (obj.label != null && !obj.label.isBlank()) ? obj.label : fallback;
    }

    private static final class TintedVertexConsumerProvider implements VertexConsumerProvider {
        private final VertexConsumerProvider delegate;
        private final float[] params;

        private TintedVertexConsumerProvider(VertexConsumerProvider delegate, float brightness, float alpha, float r, float g, float b) {
            this.delegate = delegate;
            this.params = new float[]{brightness, alpha, r, g, b};
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return new TintedVertexConsumer(delegate.getBuffer(layer), params);
        }
    }

    private static final class TintedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float brightness, alpha, tr, tg, tb;

        private TintedVertexConsumer(VertexConsumer delegate, float[] params) {
            this.delegate = delegate;
            this.brightness = params[0];
            this.alpha = params[1];
            this.tr = params[2];
            this.tg = params[3];
            this.tb = params[4];
        }

        @Override public VertexConsumer vertex(float x, float y, float z) { delegate.vertex(x, y, z); return this; }
        @Override public VertexConsumer texture(float u, float v) { delegate.texture(u, v); return this; }
        @Override public VertexConsumer overlay(int u, int v) { delegate.overlay(u, v); return this; }
        @Override public VertexConsumer light(int u, int v) { delegate.light(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color(
                    clamp((int)(r * brightness * tr)),
                    clamp((int)(g * brightness * tg)),
                    clamp((int)(b * brightness * tb)),
                    clamp((int)(a * alpha))
            );
            return this;
        }

        private int clamp(int v) { return Math.max(0, Math.min(255, v)); }
    }
}