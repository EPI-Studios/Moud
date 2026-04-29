package com.moud.server.minestom.physics.rapier.bake;

import java.util.Map;
import net.minestom.server.instance.block.Block;

/**
 * Snapshot of a section's block data captured on the server tick thread,
 * suitable for off-thread baking. The {@code solid} array is the 16x16x16
 * full-cube mask consumed by {@link GreedyMesher}; {@code customBlocks} is
 * a sparse map keyed by section-local block index for non-cube blocks
 * routed through {@link CustomShapeEmitter}.
 */
public record BakeJob(long sectionPos, boolean[] solid, Map<Integer, Block> customBlocks) {

    public static int blockIndex(int localX, int localY, int localZ) {
        return localX * 256 + localY * 16 + localZ;
    }
}
