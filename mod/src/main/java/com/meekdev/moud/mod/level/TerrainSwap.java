package com.meekdev.moud.mod.level;

import com.meekdev.moud.mod.MoudMod;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.hollowcube.polar.PolarWorld;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public final class TerrainSwap {

    private static final int SECTION = 16;
    private static final int MARGIN = 2;

    private TerrainSwap() {}

    public static int apply(ServerLevel level, @Nullable PolarWorld terrain) {
        if (!(level.getChunkSource().getGenerator() instanceof PlaceChunkGenerator generator)) return 0;
        generator.terrain(terrain);
        PolarWorld used = generator.terrain();
        List<LevelChunk> chunks = loaded(level);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (LevelChunk chunk : chunks) {
            chunk.clearAllBlockEntities();
            LevelChunkSection[] sections = chunk.getSections();
            for (LevelChunkSection section : sections) {
                if (section.hasOnlyAir()) continue;
                for (int y = 0; y < SECTION; y++) {
                    for (int z = 0; z < SECTION; z++) {
                        for (int x = 0; x < SECTION; x++) section.setBlockState(x, y, z, air, false);
                    }
                }
                section.recalcBlockCounts();
            }
            if (used != null) PolarChunks.fill(used, chunk);
            Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
            chunk.markUnsaved();
        }
        relight(level, chunks);
        MoudMod.LOG.info("swapped the terrain of {} loaded chunks", chunks.size());
        return chunks.size();
    }

    private static void relight(ServerLevel level, List<LevelChunk> chunks) {
        ThreadedLevelLightEngine light = level.getChunkSource().getLightEngine();
        List<CompletableFuture<?>> pending = new ArrayList<>();
        for (LevelChunk chunk : chunks) {
            chunk.setLightCorrect(false);
            pending.add(light.initializeLight(chunk, false).thenCompose(lit -> light.lightChunk(lit, false)));
        }
        light.tryScheduleUpdate();
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).whenComplete((done, failure) -> level.getServer().execute(() -> {
            if (failure != null) MoudMod.LOG.warn("relighting the new terrain failed", failure);
            for (LevelChunk chunk : chunks) {
                ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
                for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false)) player.connection.send(packet);
            }
        }));
    }

    private static List<LevelChunk> loaded(ServerLevel level) {
        Map<Long, LevelChunk> found = new LinkedHashMap<>();
        int reach = level.getServer().getPlayerList().getViewDistance() + MARGIN;
        for (ServerPlayer player : level.players()) {
            ChunkPos centre = player.chunkPosition();
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(centre.x() + dx, centre.z() + dz);
                    if (chunk != null) found.putIfAbsent(ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z()), chunk);
                }
            }
        }
        return List.copyOf(found.values());
    }
}
