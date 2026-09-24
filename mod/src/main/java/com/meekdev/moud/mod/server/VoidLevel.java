package com.meekdev.moud.mod.server;

import com.meekdev.moud.mod.level.PlaceChunkGenerator;
import com.meekdev.moud.mod.place.Blocks;
import java.util.Map;
import net.hollowcube.polar.PolarWorld;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.jspecify.annotations.Nullable;

public final class VoidLevel {

    public static final String NAME = "moud";

    private static final ResourceKey<DimensionType> TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath("moud", "void"));

    private VoidLevel() {}

    public static WorldDimensions dimensions(HolderLookup.Provider registries) {
        return dimensions(registries, Blocks.load());
    }

    public static WorldDimensions dimensions(HolderLookup.Provider registries, @Nullable PolarWorld terrain) {
        LevelStem stem = new LevelStem(
                registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(TYPE),
                new PlaceChunkGenerator(
                        registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID),
                        terrain));
        return new WorldDimensions(Map.of(LevelStem.OVERWORLD, stem));
    }

    public static LevelSettings settings() {
        return new LevelSettings(
                NAME,
                GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, true),
                true,
                WorldDataConfiguration.DEFAULT);
    }
}
