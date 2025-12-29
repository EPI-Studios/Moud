package com.moud.client.editor.scene.blueprint;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.WeakHashMap;

public final class BlueprintSchematicPreviewRenderer implements WorldRenderEvents.AfterEntities {
    private static final BlueprintSchematicPreviewRenderer INSTANCE = new BlueprintSchematicPreviewRenderer();
    private static boolean loggedError;

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(INSTANCE);
    }

    private final Map<Blueprint, BlueprintBlockPreviewCache> blockCaches = new WeakHashMap<>();
    private final Map<Blueprint, BlueprintSchematicMeshCache> meshCaches = new WeakHashMap<>();

    private BlueprintSchematicPreviewRenderer() {
    }

    @Override
    public void afterEntities(WorldRenderContext context) {
        try {
            BlueprintPreviewManager.PreviewState preview = BlueprintPreviewManager.getInstance().getCurrent();
            if (preview == null || preview.blueprint == null || preview.blueprint.blocks == null) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            BlockRenderManager blockRenderManager = client.getBlockRenderManager();
            if (blockRenderManager == null) {
                return;
            }
            if (client.world == null) {
                return;
            }

            float[] origin = preview.position;
            if (origin == null || origin.length < 3) {
                return;
            }

            int rotationSteps = Math.floorMod(Math.round(preview.rotation[1] / 90.0f), 4);
            boolean mirrorX = preview.scale[0] < 0.0f;
            boolean mirrorZ = preview.scale[2] < 0.0f;

            BlueprintBlockPreviewCache blockCache = blockCaches.computeIfAbsent(preview.blueprint, BlueprintBlockPreviewCache::new);
            BlueprintBlockPreviewCache.Variant variant = blockCache.getVariant(rotationSteps, mirrorX, mirrorZ);
            if (variant.chunks == null || variant.chunks.isEmpty()) {
                return;
            }

            Vec3d cameraPos = context.camera().getPos();

            int originX = (int) Math.floor(origin[0]);
            int originY = (int) Math.floor(origin[1]);
            int originZ = (int) Math.floor(origin[2]);

            BlueprintSchematicMeshCache meshCache = meshCaches.computeIfAbsent(
                    preview.blueprint,
                    blueprint -> new BlueprintSchematicMeshCache(blueprint, blockCache)
            );
            float ghostAlpha = 0.40f;
            float ghostBrightness = 1.10f;
            BlueprintSchematicMeshCache.MeshVariant meshVariant = meshCache.getOrBuild(variant, blockRenderManager, client.world, ghostBrightness, ghostAlpha);
            meshCache.render(meshVariant, context, cameraPos, originX, originY, originZ);
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                org.slf4j.LoggerFactory.getLogger(BlueprintSchematicPreviewRenderer.class)
                        .error("Blueprint schematic preview rendering failed", t);
            }
        }
    }
}
