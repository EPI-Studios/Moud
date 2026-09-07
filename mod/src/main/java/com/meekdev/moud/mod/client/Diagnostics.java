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
import net.minecraft.world.level.Level;

// the same numbers the editor shows, written to the log a few seconds in. a symptom described in
// words has cost several runs to chase; these are the values that decide between the causes
final class Diagnostics {

    // stone spans -64..63, so these sample the middle and all four quadrants
    private static final int[][] PROBES = {
        {0, 60, 0}, {-40, 60, -40}, {40, 60, -40}, {-40, 60, 40}, {40, 60, 40}, {-60, 60, 60},
    };

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

        StringBuilder blocks = new StringBuilder();
        for (int[] probe : PROBES) {
            BlockPos pos = new BlockPos(probe[0], probe[1], probe[2]);
            blocks.append(' ').append(probe[0]).append(',').append(probe[2]).append('=')
                    .append(level.getBlockState(pos).getBlock().toString())
                    .append(level.isLoaded(pos) ? "" : "(unloaded)");
        }
        MoudMod.LOG.info("diag chunksFilled={} blocksWritten={} clientBlocks{}",
                PolarChunks.filled(), PolarChunks.blocks(), blocks);
    }
}
