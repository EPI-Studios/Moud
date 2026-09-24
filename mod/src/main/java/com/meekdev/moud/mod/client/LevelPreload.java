package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.server.VoidLevel;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.jspecify.annotations.Nullable;

final class LevelPreload {

    private record Pending(LevelStorageSource.LevelStorageAccess access, PackRepository packs,
                           CompletableFuture<WorldStem> stem) {}

    private static @Nullable Pending pending;

    private LevelPreload() {}

    static void start(Minecraft client) {
        if (pending != null) return;
        try {
            LevelStorageSource.LevelStorageAccess access = client.getLevelSource().validateAndCreateAccess(VoidLevel.NAME);
            PackRepository packs = ServerPacksSource.createPackRepository(access);
            LevelSettings settings = VoidLevel.settings();
            WorldOptions options = WorldOptions.defaultWithRandomSeed();
            WorldLoader.InitConfig config = new WorldLoader.InitConfig(
                    new WorldLoader.PackConfig(packs, settings.dataConfiguration(), false, false),
                    Commands.CommandSelection.INTEGRATED, LevelBasedPermissionSet.GAMEMASTER);
            CompletableFuture<WorldStem> stem = WorldLoader.load(config, context -> {
                WorldDimensions dimensions = VoidLevel.dimensions(context.datapackWorldgen());
                WorldDimensions.Complete complete = dimensions.bake(context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
                return new WorldLoader.DataLoadOutput<>(
                        new LevelDataAndDimensions.WorldDataAndGenSettings(
                                new PrimaryLevelData(settings, complete.specialWorldProperty(), complete.lifecycle()),
                                new WorldGenSettings(options, dimensions)),
                        complete.dimensionsRegistryAccess());
            }, WorldStem::new, Util.backgroundExecutor(), client);
            pending = new Pending(access, packs, stem);
        } catch (Exception e) {
            MoudMod.LOG.warn("could not load the level ahead: {}", e.getMessage());
        }
    }

    static boolean open(Minecraft client) {
        Pending taken = pending;
        pending = null;
        if (taken == null) return false;
        client.managedBlock(taken.stem::isDone);
        try {
            client.doWorldLoad(taken.access, taken.packs, taken.stem.get(), Optional.empty(), true);
            return true;
        } catch (Exception e) {
            MoudMod.LOG.warn("the level loaded ahead failed, loading it again: {}", e.getMessage());
            taken.access.safeClose();
            return false;
        }
    }
}
