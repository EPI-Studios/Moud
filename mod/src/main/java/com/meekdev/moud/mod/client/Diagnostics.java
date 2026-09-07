package com.meekdev.moud.mod.client;

import com.meekdev.bkun.sublevel.SubLevelIndex;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.level.PolarChunks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import com.meekdev.bkun.sublevel.SubLevelEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

// the same numbers the editor shows, written to the log a few seconds in. a symptom described in
// words has cost several runs to chase; these are the values that decide between the causes
final class Diagnostics {

    // the stone is one layer at y=60 spanning chunks -4..3 on both axes
    private static final int MIN_CHUNK = -4;
    private static final int MAX_CHUNK = 3;

    private double elapsed;
    private int reports;

    void tick(double dt) {
        if (reports >= 2) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            elapsed = 0;
            return;
        }
        elapsed += dt;
        if (elapsed < (reports == 0 ? 6.0 : 20.0)) return;
        reports++;
        report(level);
    }

    private void report(Level level) {
        InstanceTree tree = ClientScene.tree();
        MoudMod.LOG.info("diag parts={} drawnStill={} drawnMoving={} moving={}",
                tree == null ? -1 : tree.ofClass(Classes.PART).size(),
                Parts.stillCount(), Parts.movingCount(), ClientScene.motion().moving().size());
        MoudMod.LOG.info("diag boxesServer={} boxesClient={} subLevelsServer={} subLevelsClient={}",
                Physics.boxes().size(), ClientPhysics.boxes().size(),
                Physics.shapes().size(), SubLevelIndex.in(level).size());

        // every chunk the polar file covers, asked of the client's own level: which arrived and
        // which of those actually carry the stone layer. a fixed probe list only measured where
        // the player happened to be standing
        int loaded = 0;
        int stone = 0;
        StringBuilder missing = new StringBuilder();
        for (int cx = MIN_CHUNK; cx <= MAX_CHUNK; cx++) {
            for (int cz = MIN_CHUNK; cz <= MAX_CHUNK; cz++) {
                BlockPos pos = new BlockPos(cx * 16 + 8, 60, cz * 16 + 8);
                boolean here = level.isLoaded(pos);
                if (here) loaded++;
                if (here && !level.getBlockState(pos).isAir()) {
                    stone++;
                } else if (missing.length() < 200) {
                    missing.append(' ').append(cx).append(',').append(cz)
                            .append(here ? "=air" : "=unloaded");
                }
            }
        }
        var player = Minecraft.getInstance().player;
        MoudMod.LOG.info("diag chunksFilled={} blocksWritten={} polarChunksOnClient={}/64 withStone={}"
                        + " clientChunksTotal={} renderDistance={} at={}{}",
                PolarChunks.filled(), PolarChunks.blocks(), loaded, stone,
                level.getChunkSource().getLoadedChunksCount(),
                Minecraft.getInstance().options.renderDistance().get(),
                player == null ? "?" : player.blockPosition(), missing);

        // what the client would actually collide against, and whether it is rotated at all
        for (SubLevelEntity platform : SubLevelIndex.in(level)) {
            AABB body = new AABB(-64, -64, -64, 64, 64, 64);
            MoudMod.LOG.info("diag subLevel id={} at={} rot={} model={} boxes={}",
                    platform.getId(), platform.position(), platform.rotation(),
                    platform.isModel(), platform.collisionBoxesNear(body).size());
        }
    }
}
