package com.meekdev.moud.mod.level;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.hollowcube.polar.PolarChunk;
import net.hollowcube.polar.PolarSection;
import net.hollowcube.polar.PolarWorld;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class PolarChunks {

    private static final int SECTION = 16;

    private static final AtomicInteger FILLED =
            new AtomicInteger();
    private static final AtomicLong BLOCKS =
            new AtomicLong();

    public static int filled() {
        return FILLED.get();
    }

    public static long blocks() {
        return BLOCKS.get();
    }

    private PolarChunks() {}

    public static void fill(PolarWorld place, ChunkAccess chunk) {
        PolarChunk source = place.chunkAt(chunk.getPos().x(), chunk.getPos().z());
        if (source == null) return;
        FILLED.incrementAndGet();
        long written = 0;

        HolderLookup<Block> blocks = BuiltInRegistries.BLOCK;
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        PolarSection[] sections = source.sections();
        for (int i = 0; i < sections.length; i++) {
            PolarSection section = sections[i];
            if (section.isEmpty()) continue;

            int sectionY = place.minSection() + i;
            int index = chunk.getSectionIndexFromSectionY(sectionY);
            if (index < 0 || index >= chunk.getSections().length) continue;

            BlockState[] palette = states(blocks, section.blockPalette());
            int[] data = palette.length > 1 ? section.blockData() : null;
            LevelChunkSection target = chunk.getSection(index);

            for (int y = 0; y < SECTION; y++) {
                for (int z = 0; z < SECTION; z++) {
                    for (int x = 0; x < SECTION; x++) {
                        int id = data == null ? 0 : data[(y << 8) | (z << 4) | x];
                        BlockState state = id < palette.length ? palette[id] : palette[0];
                        if (state.isAir()) continue;
                        target.setBlockState(x, y, z, state, false);
                        written++;
                        int worldY = sectionY * SECTION + y;
                        oceanFloor.update(x, worldY, z, state);
                        worldSurface.update(x, worldY, z, state);
                    }
                }
            }
            target.recalcBlockCounts();
        }
        BLOCKS.addAndGet(written);
    }

    private static BlockState[] states(HolderLookup<Block> blocks, String[] palette) {
        BlockState[] states = new BlockState[palette.length];
        for (int i = 0; i < palette.length; i++) {
            states[i] = parse(blocks, palette[i]);
        }
        return states;
    }

    private static BlockState parse(HolderLookup<Block> blocks, String key) {
        try {
            return BlockStateParser.parseForBlock(blocks, key, false).blockState();
        } catch (CommandSyntaxException e) {
            return Blocks.AIR.defaultBlockState();
        }
    }
}
