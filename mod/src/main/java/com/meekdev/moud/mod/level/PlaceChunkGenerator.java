package com.meekdev.moud.mod.level;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.hollowcube.polar.PolarWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.jspecify.annotations.Nullable;

public final class PlaceChunkGenerator extends ChunkGenerator {

    public static final MapCodec<PlaceChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome))
                    .apply(instance, PlaceChunkGenerator::new));

    private static final BlockState[] EMPTY_COLUMN = new BlockState[0];

    private final Holder<Biome> biome;
    private final @Nullable PolarWorld place;

    public PlaceChunkGenerator(Holder<Biome> biome) {
        this(biome, null);
    }

    public PlaceChunkGenerator(Holder<Biome> biome, @Nullable PolarWorld place) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
        this.place = MoudMod.features().isOn(Feature.TERRAIN) ? place : null;
        MoudMod.LOG.info("place has {} chunks, terrain {}",
                place == null ? 0 : place.chunks().size(), this.place == null ? "off" : "on");
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
            StructureManager structures, ChunkAccess chunk) {
        if (place != null) PolarChunks.fill(place, chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
            StructureManager structures, ChunkAccess chunk) {}

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random,
            ChunkAccess chunk) {}

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return -64;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return level.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        return new NoiseColumn(level.getMinY(), EMPTY_COLUMN);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos pos) {}
}
