package com.moud.client.editor.scene.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public final class BlueprintBlockPreviewCache {

    private final Blueprint blueprint;
    private final Blueprint.BlockVolume volume;
    private final List<String> palette;
    private final BlockState[] basePaletteStates;
    private final Variant[] variants = new Variant[16];

    public BlueprintBlockPreviewCache(Blueprint blueprint) {
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.volume = Objects.requireNonNull(blueprint.blocks, "blueprint.blocks");
        this.palette = volume.palette != null ? volume.palette : List.of("minecraft:air");
        this.basePaletteStates = new BlockState[this.palette.size()];
        for (int i = 0; i < this.palette.size(); i++) {
            this.basePaletteStates[i] = parseBlockState(this.palette.get(i));
        }
    }

    public Variant getVariant(int rotationSteps, boolean mirrorX, boolean mirrorZ) {
        rotationSteps = ((rotationSteps % 4) + 4) % 4;
        int key = rotationSteps + (mirrorX ? 4 : 0) + (mirrorZ ? 8 : 0);
        Variant cached = variants[key];
        if (cached != null) {
            return cached;
        }
        Variant built = buildVariant(rotationSteps, mirrorX, mirrorZ);
        variants[key] = built;
        return built;
    }

    private Variant buildVariant(int rotationSteps, boolean mirrorX, boolean mirrorZ) {
        int sizeX = Math.max(1, volume.sizeX);
        int sizeY = Math.max(1, volume.sizeY);
        int sizeZ = Math.max(1, volume.sizeZ);
        int outSizeX = (rotationSteps % 2 == 0) ? sizeX : sizeZ;
        int outSizeZ = (rotationSteps % 2 == 0) ? sizeZ : sizeX;

        BlockState[] transformedPalette = new BlockState[basePaletteStates.length];
        BlockRotation rotation = rotationFromSteps(rotationSteps);
        for (int i = 0; i < basePaletteStates.length; i++) {
            BlockState state = basePaletteStates[i];
            if (mirrorX) {
                try {
                    state = state.mirror(BlockMirror.FRONT_BACK);
                } catch (Throwable ignored) {}
            }
            if (mirrorZ) {
                try {
                    state = state.mirror(BlockMirror.LEFT_RIGHT);
                } catch (Throwable ignored) {}
            }
            if (rotation != null) {
                try {
                    state = state.rotate(rotation);
                } catch (Throwable ignored) {}
            }
            transformedPalette[i] = state;
        }

        int transformedCellCount = outSizeX * sizeY * outSizeZ;
        int[] transformedIndices = new int[transformedCellCount];

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int paletteIndex = getPaletteIndex(volume, x, y, z);
                    if (paletteIndex <= 0 || paletteIndex >= transformedPalette.length) {
                        continue;
                    }
                    int mx = mirrorX ? (sizeX - 1 - x) : x;
                    int mz = mirrorZ ? (sizeZ - 1 - z) : z;
                    int tx, tz;
                    switch (rotationSteps) {
                        case 1 -> {
                            tx = outSizeX - 1 - mz;
                            tz = mx;
                        }
                        case 2 -> {
                            tx = outSizeX - 1 - mx;
                            tz = outSizeZ - 1 - mz;
                        }
                        case 3 -> {
                            tx = mz;
                            tz = outSizeZ - 1 - mx;
                        }
                        default -> {
                            tx = mx;
                            tz = mz;
                        }
                    }
                    int idx = (y * outSizeZ + tz) * outSizeX + tx;
                    if (idx >= 0 && idx < transformedIndices.length) {
                        transformedIndices[idx] = paletteIndex;
                    }
                }
            }
        }

        Map<Long, Chunk> chunks = new HashMap<>();
        for (int y = 0; y < sizeY; y++) {
            for (int tz = 0; tz < outSizeZ; tz++) {
                for (int tx = 0; tx < outSizeX; tx++) {
                    int cellIndex = (y * outSizeZ + tz) * outSizeX + tx;
                    int paletteIndex = transformedIndices[cellIndex];
                    if (paletteIndex <= 0 || paletteIndex >= transformedPalette.length) {
                        continue;
                    }
                    boolean occluded = true;
                    occluded &= (tx > 0) && transformedIndices[cellIndex - 1] != 0;
                    occluded &= (tx + 1 < outSizeX) && transformedIndices[cellIndex + 1] != 0;
                    occluded &= (tz > 0) && transformedIndices[cellIndex - outSizeX] != 0;
                    occluded &= (tz + 1 < outSizeZ) && transformedIndices[cellIndex + outSizeX] != 0;
                    occluded &= (y > 0) && transformedIndices[cellIndex - outSizeX * outSizeZ] != 0;
                    occluded &= (y + 1 < sizeY) && transformedIndices[cellIndex + outSizeX * outSizeZ] != 0;
                    if (occluded) {
                        continue;
                    }
                    int chunkX = tx >> 4;
                    int chunkY = y >> 4;
                    int chunkZ = tz >> 4;
                    long key = chunkKey(chunkX, chunkY, chunkZ);
                    Chunk chunk = chunks.get(key);
                    if (chunk == null) {
                        chunk = new Chunk(chunkX, chunkY, chunkZ);
                        chunks.put(key, chunk);
                    }
                    chunk.blocks.add(new BlockEntry(tx, y, tz, transformedPalette[paletteIndex]));
                }
            }
        }

        List<Chunk> chunkList = new ArrayList<>(chunks.values());
        for (Chunk chunk : chunkList) {
            chunk.computeBounds();
        }

        Variant variant = new Variant();
        variant.rotationSteps = rotationSteps;
        variant.mirrorX = mirrorX;
        variant.mirrorZ = mirrorZ;
        variant.sizeX = outSizeX;
        variant.sizeY = sizeY;
        variant.sizeZ = outSizeZ;
        variant.chunks = chunkList;
        variant.paletteStates = transformedPalette;
        variant.indices = transformedIndices;
        return variant;
    }

    private static BlockRotation rotationFromSteps(int steps) {
        return switch (steps & 3) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    private static long chunkKey(int x, int y, int z) {
        return (((long) x) & 0x1FFFFF) << 42 | (((long) y) & 0x3FF) << 32 | (((long) z) & 0xFFFFFFFFL);
    }

    private static int getPaletteIndex(Blueprint.BlockVolume volume, int x, int y, int z) {
        int sizeX = Math.max(1, volume.sizeX);
        int sizeY = Math.max(1, volume.sizeY);
        int sizeZ = Math.max(1, volume.sizeZ);
        x = Math.min(Math.max(0, x), sizeX - 1);
        y = Math.min(Math.max(0, y), sizeY - 1);
        z = Math.min(Math.max(0, z), sizeZ - 1);
        int idx = (y * sizeZ + z) * sizeX + x;
        if (volume.useShortIndices) {
            int offset = idx * 2;
            if (volume.voxels == null || offset + 1 >= volume.voxels.length) {
                return 0;
            }
            int lo = volume.voxels[offset] & 0xFF;
            int hi = volume.voxels[offset + 1] & 0xFF;
            return (hi << 8) | lo;
        }
        if (volume.voxels == null || idx >= volume.voxels.length) {
            return 0;
        }
        return volume.voxels[idx] & 0xFF;
    }

    private static BlockState parseBlockState(String stateString) {
        if (stateString == null || stateString.isBlank()) {
            return Blocks.AIR.getDefaultState();
        }
        String raw = stateString.trim();
        String base = raw;
        String props = null;
        int bracketIdx = raw.indexOf('[');
        if (bracketIdx >= 0 && raw.endsWith("]")) {
            base = raw.substring(0, bracketIdx);
            props = raw.substring(bracketIdx + 1, raw.length() - 1);
        }
        Identifier id = Identifier.tryParse(base);
        if (id == null) {
            return Blocks.AIR.getDefaultState();
        }
        Block block = Registries.BLOCK.get(id);
        if (block == null) {
            return Blocks.AIR.getDefaultState();
        }
        BlockState state = block.getDefaultState();
        if (props == null || props.isBlank()) {
            return state;
        }
        String[] parts = props.split(",");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (key.isEmpty() || value.isEmpty()) continue;
            var property = state.getBlock().getStateManager().getProperty(key);
            if (property == null) continue;
            try {
                state = applyProperty(state, property, value);
            } catch (Throwable ignored) {}
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, Property property, String value) {
        if (state == null || property == null || value == null) {
            return state;
        }
        try {
            var parsed = property.parse(value);
            if (parsed.isPresent()) {
                return state.with(property, (Comparable) parsed.get());
            }
        } catch (Throwable ignored) {}
        return state;
    }

    public static final class Variant {
        public int rotationSteps;
        public boolean mirrorX;
        public boolean mirrorZ;
        public int sizeX;
        public int sizeY;
        public int sizeZ;
        public List<Chunk> chunks = List.of();
        public BlockState[] paletteStates = new BlockState[0];
        public int[] indices = new int[0];
    }

    public static final class Chunk {
        public final int chunkX;
        public final int chunkY;
        public final int chunkZ;
        public final List<BlockEntry> blocks = new ArrayList<>();
        public Box localBounds;

        private Chunk(int chunkX, int chunkY, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.chunkZ = chunkZ;
        }

        private void computeBounds() {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (BlockEntry entry : blocks) {
                minX = Math.min(minX, entry.x);
                minY = Math.min(minY, entry.y);
                minZ = Math.min(minZ, entry.z);
                maxX = Math.max(maxX, entry.x + 1);
                maxY = Math.max(maxY, entry.y + 1);
                maxZ = Math.max(maxZ, entry.z + 1);
            }
            if (!Double.isFinite(minX)) {
                localBounds = null;
                return;
            }
            localBounds = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    public static final class BlockEntry {
        public final int x;
        public final int y;
        public final int z;
        public final BlockState state;

        private BlockEntry(int x, int y, int z, BlockState state) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.state = state;
        }

        public BlockPos toWorldPos(float[] origin) {
            int ox = origin != null && origin.length > 0 ? MathHelper.floor(origin[0]) : 0;
            int oy = origin != null && origin.length > 1 ? MathHelper.floor(origin[1]) : 0;
            int oz = origin != null && origin.length > 2 ? MathHelper.floor(origin[2]) : 0;
            return new BlockPos(ox + x, oy + y, oz + z);
        }
    }
}