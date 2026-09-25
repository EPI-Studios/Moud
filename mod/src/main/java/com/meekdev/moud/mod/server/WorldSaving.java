package com.meekdev.moud.mod.server;

import com.meekdev.moud.mod.level.PlaceChunkGenerator;
import com.meekdev.moud.mod.place.PlaceToml;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;

public final class WorldSaving {

    private static final boolean DEDICATED = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    private static final boolean PLACE_SERVER = DEDICATED && PlaceToml.chosenAtLaunch();
    private static volatile boolean placeLevel;

    private WorldSaving() {}

    public static boolean off() {
        return PLACE_SERVER || placeLevel;
    }

    public static boolean off(String levelId) {
        return off() || !DEDICATED && levelId.equals(VoidLevel.NAME);
    }

    static void install() {
        if (DEDICATED) return;
        ServerLifecycleEvents.SERVER_STARTING.register(server -> placeLevel = placeLevel(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> placeLevel = false);
    }

    private static boolean placeLevel(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getOptional(LevelStem.OVERWORLD)
                .map(stem -> stem.generator() instanceof PlaceChunkGenerator)
                .orElse(false);
    }
}
