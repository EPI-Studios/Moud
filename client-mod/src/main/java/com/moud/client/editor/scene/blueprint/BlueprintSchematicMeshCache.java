package com.moud.client.editor.scene.blueprint;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.fluid.FluidState;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BlueprintSchematicMeshCache {
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    private final Blueprint blueprint;
    private final BlueprintBlockPreviewCache blockCache;
    private final Map<Integer, MeshVariant> variants = new java.util.HashMap<>();

    public BlueprintSchematicMeshCache(Blueprint blueprint, BlueprintBlockPreviewCache blockCache) {
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.blockCache = Objects.requireNonNull(blockCache, "blockCache");
    }

    public MeshVariant getOrBuild(BlueprintBlockPreviewCache.Variant variant, BlockRenderManager blockRenderManager, ClientWorld world,
                                  float ghostBrightness, float ghostAlpha) {
        int key = variant.rotationSteps + (variant.mirrorX ? 4 : 0) + (variant.mirrorZ ? 8 : 0);
        MeshVariant cached = variants.get(key);
        if (cached != null && !cached.closed) {
            return cached;
        }

        if (cached != null) {
            cached.close();
        }

        MeshVariant built = build(variant, blockRenderManager, world, ghostBrightness, ghostAlpha);
        variants.put(key, built);
        return built;
    }

    public void clear() {
        for (MeshVariant variant : variants.values()) {
            if (variant != null) {
                variant.close();
            }
        }
        variants.clear();
    }

    private MeshVariant build(BlueprintBlockPreviewCache.Variant variant, BlockRenderManager blockRenderManager, ClientWorld world,
                              float ghostBrightness, float ghostAlpha) {
        MeshVariant meshVariant = new MeshVariant(variant.rotationSteps, variant.mirrorX, variant.mirrorZ, variant.sizeX, variant.sizeY, variant.sizeZ);
        if (variant.chunks == null || variant.chunks.isEmpty()) {
            return meshVariant;
        }

        SchematicBlockRenderView renderView = new SchematicBlockRenderView(variant, world);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        Random random = Random.createLocal();

        for (BlueprintBlockPreviewCache.Chunk chunk : variant.chunks) {
            if (chunk == null || chunk.blocks.isEmpty()) {
                continue;
            }

            Map<RenderLayer, java.util.List<BlueprintBlockPreviewCache.BlockEntry>> byLayer = new IdentityHashMap<>();
            for (BlueprintBlockPreviewCache.BlockEntry entry : chunk.blocks) {
                if (entry == null || entry.state == null) {
                    continue;
                }
                RenderLayer layer = RenderLayers.getBlockLayer(entry.state);
                byLayer.computeIfAbsent(layer, k -> new java.util.ArrayList<>()).add(entry);
            }

            ChunkMesh chunkMesh = new ChunkMesh(chunk.chunkX, chunk.chunkY, chunk.chunkZ, chunk.localBounds);
            int baseX = chunk.chunkX * 16;
            int baseY = chunk.chunkY * 16;
            int baseZ = chunk.chunkZ * 16;

            for (Map.Entry<RenderLayer, java.util.List<BlueprintBlockPreviewCache.BlockEntry>> entry : byLayer.entrySet()) {
                RenderLayer layer = entry.getKey();
                List<BlueprintBlockPreviewCache.BlockEntry> blocks = entry.getValue();
                if (blocks == null || blocks.isEmpty()) {
                    continue;
                }

                Tessellator tessellator = new Tessellator(layer.getExpectedBufferSize());
                BufferBuilder builder = tessellator.begin(layer.getDrawMode(), layer.getVertexFormat());

                float layerAlpha = (layer == RenderLayer.getCutout()
                        || layer == RenderLayer.getCutoutMipped()
                        || layer == RenderLayer.getTripwire())
                        ? 1.0f
                        : ghostAlpha;
                VertexConsumer consumer = new GhostVertexConsumer(builder, ghostBrightness, layerAlpha);
                MatrixStack matrices = new MatrixStack();

                for (BlueprintBlockPreviewCache.BlockEntry block : blocks) {
                    int localX = block.x - baseX;
                    int localY = block.y - baseY;
                    int localZ = block.z - baseZ;

                    mutable.set(block.x, block.y, block.z);
                    try {
                        random.setSeed(block.state.getRenderingSeed(mutable));
                    } catch (Throwable ignored) {
                    }

                    matrices.push();
                    matrices.translate(localX, localY, localZ);

                    try {
                        blockRenderManager.renderBlock(block.state, mutable, renderView, matrices, consumer, true, random);
                    } catch (Throwable ignored) {
                    }

                    matrices.pop();
                }

                BuiltBuffer built = builder.endNullable();
                if (built == null) {
                    continue;
                }

                VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vertexBuffer.bind();
                vertexBuffer.upload(built);
                VertexBuffer.unbind();
                built.close();

                chunkMesh.buffers.put(layer, vertexBuffer);
            }

            if (!chunkMesh.buffers.isEmpty()) {
                meshVariant.chunks.add(chunkMesh);
            } else {
                chunkMesh.close();
            }
        }

        return meshVariant;
    }

    public void render(MeshVariant meshVariant, net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context,
                       Vec3d cameraPos, int originX, int originY, int originZ) {
        if (meshVariant == null || meshVariant.closed || meshVariant.chunks.isEmpty()) {
            return;
        }

        Matrix4f projection = context.projectionMatrix();

        Matrix4f view = new Matrix4f();
        Quaternionf cameraRotation = new Quaternionf(context.camera().getRotation());
        cameraRotation.conjugate();
        view.identity()
                .rotate(cameraRotation)
                .translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (ChunkMesh chunk : meshVariant.chunks) {
            if (chunk == null || chunk.closed || chunk.buffers.isEmpty()) {
                continue;
            }
            if (chunk.bounds != null && !isVisible(translate(chunk.bounds, originX, originY, originZ), context)) {
                continue;
            }

            Matrix4f model = new Matrix4f()
                    .translation(
                            originX + chunk.chunkX * 16.0f,
                            originY + chunk.chunkY * 16.0f,
                            originZ + chunk.chunkZ * 16.0f
                    );
            Matrix4f modelView = new Matrix4f(view).mul(model);

            drawIfPresent(chunk, RenderLayer.getSolid(), modelView, projection);
            drawIfPresent(chunk, RenderLayer.getCutout(), modelView, projection);
            drawIfPresent(chunk, RenderLayer.getCutoutMipped(), modelView, projection);
            drawIfPresent(chunk, RenderLayer.getTranslucent(), modelView, projection);
            drawIfPresent(chunk, RenderLayer.getTripwire(), modelView, projection);

            for (Map.Entry<RenderLayer, VertexBuffer> entry : chunk.buffers.entrySet()) {
                RenderLayer layer = entry.getKey();
                if (layer == RenderLayer.getSolid()
                        || layer == RenderLayer.getCutout()
                        || layer == RenderLayer.getCutoutMipped()
                        || layer == RenderLayer.getTranslucent()
                        || layer == RenderLayer.getTripwire()) {
                    continue;
                }
                drawLayer(layer, entry.getValue(), modelView, projection);
            }
        }

        RenderSystem.disableBlend();
    }

    private void drawIfPresent(ChunkMesh chunk, RenderLayer layer, Matrix4f modelView, Matrix4f projection) {
        VertexBuffer buffer = chunk.buffers.get(layer);
        if (buffer == null) {
            return;
        }
        drawLayer(layer, buffer, modelView, projection);
    }

    private void drawLayer(RenderLayer layer, VertexBuffer buffer, Matrix4f modelView, Matrix4f projection) {
        if (layer == null || buffer == null) {
            return;
        }

        ShaderProgram shader = shaderFor(layer);
        if (shader == null) {
            return;
        }

        layer.startDrawing();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        buffer.bind();
        buffer.draw(modelView, projection, shader);
        VertexBuffer.unbind();

        layer.endDrawing();
    }

    private ShaderProgram shaderFor(RenderLayer layer) {
        if (layer == RenderLayer.getSolid()) {
            return GameRenderer.getRenderTypeSolidProgram();
        }
        if (layer == RenderLayer.getCutoutMipped()) {
            return GameRenderer.getRenderTypeCutoutMippedProgram();
        }
        if (layer == RenderLayer.getCutout()) {
            return GameRenderer.getRenderTypeCutoutProgram();
        }
        if (layer == RenderLayer.getTranslucent()) {
            return GameRenderer.getRenderTypeTranslucentProgram();
        }
        if (layer == RenderLayer.getTranslucentMovingBlock()) {
            return GameRenderer.getRenderTypeTranslucentMovingBlockProgram();
        }
        if (layer == RenderLayer.getTripwire()) {
            return GameRenderer.getRenderTypeTripwireProgram();
        }
        return GameRenderer.getRenderTypeTranslucentProgram();
    }

    private boolean isVisible(Box bounds, net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        if (bounds == null) {
            return true;
        }
        try {
            var frustum = context.frustum();
            if (frustum == null) {
                return true;
            }
            return frustum.isVisible(bounds);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private Box translate(Box local, int ox, int oy, int oz) {
        return new Box(
                local.minX + ox,
                local.minY + oy,
                local.minZ + oz,
                local.maxX + ox,
                local.maxY + oy,
                local.maxZ + oz
        );
    }

    public static final class MeshVariant implements AutoCloseable {
        public final int rotationSteps;
        public final boolean mirrorX;
        public final boolean mirrorZ;
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;
        public final java.util.List<ChunkMesh> chunks = new java.util.ArrayList<>();
        private boolean closed;

        private MeshVariant(int rotationSteps, boolean mirrorX, boolean mirrorZ, int sizeX, int sizeY, int sizeZ) {
            this.rotationSteps = rotationSteps;
            this.mirrorX = mirrorX;
            this.mirrorZ = mirrorZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (ChunkMesh chunk : chunks) {
                if (chunk != null) {
                    chunk.close();
                }
            }
            chunks.clear();
        }
    }

    public static final class ChunkMesh implements AutoCloseable {
        public final int chunkX;
        public final int chunkY;
        public final int chunkZ;
        public final Box bounds;
        public final Map<RenderLayer, VertexBuffer> buffers = new IdentityHashMap<>();
        private boolean closed;

        private ChunkMesh(int chunkX, int chunkY, int chunkZ, Box bounds) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.chunkZ = chunkZ;
            this.bounds = bounds;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (VertexBuffer buffer : buffers.values()) {
                if (buffer != null) {
                    buffer.close();
                }
            }
            buffers.clear();
        }
    }

    private static final class GhostVertexConsumer implements VertexConsumer {
        private static final int FULL_BRIGHT = 0x00F000F0;

        private final VertexConsumer delegate;
        private final float brightness;
        private final float alpha;

        private GhostVertexConsumer(VertexConsumer delegate, float brightness, float alpha) {
            this.delegate = delegate;
            this.brightness = brightness;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            int r = clampColor((int) (red * brightness));
            int g = clampColor((int) (green * brightness));
            int b = clampColor((int) (blue * brightness));
            int a = clampColor((int) (alpha * this.alpha));
            delegate.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(FULL_BRIGHT);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        private static int clampColor(int c) {
            return Math.max(0, Math.min(255, c));
        }
    }

    private static final class SchematicBlockRenderView implements BlockRenderView {
        private final BlueprintBlockPreviewCache.Variant variant;
        private final ClientWorld world;

        private SchematicBlockRenderView(BlueprintBlockPreviewCache.Variant variant, ClientWorld world) {
            this.variant = variant;
            this.world = world;
        }

        @Override
        public int getHeight() {
            return variant.sizeY;
        }

        @Override
        public int getBottomY() {
            return 0;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos == null) {
                return AIR;
            }
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (x < 0 || y < 0 || z < 0 || x >= variant.sizeX || y >= variant.sizeY || z >= variant.sizeZ) {
                return AIR;
            }
            int idx = (y * variant.sizeZ + z) * variant.sizeX + x;
            if (variant.indices == null || idx < 0 || idx >= variant.indices.length) {
                return AIR;
            }
            int paletteIndex = variant.indices[idx];
            if (paletteIndex <= 0 || paletteIndex >= variant.paletteStates.length) {
                return AIR;
            }
            BlockState state = variant.paletteStates[paletteIndex];
            return state != null ? state : AIR;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public net.minecraft.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public float getBrightness(Direction direction, boolean shaded) {
            return 1.0f;
        }

        @Override
        public LightingProvider getLightingProvider() {
            ClientWorld clientWorld = world != null ? world : MinecraftClient.getInstance().world;
            if (clientWorld == null) {
                throw new IllegalStateException("Cannot build schematic preview without a client world");
            }
            return clientWorld.getLightingProvider();
        }

        @Override
        public int getColor(BlockPos pos, ColorResolver colorResolver) {
            return 0xFFFFFF;
        }

        @Override
        public int getLightLevel(LightType type, BlockPos pos) {
            return 15;
        }
    }
}
