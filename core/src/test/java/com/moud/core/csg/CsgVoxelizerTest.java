package com.moud.core.csg;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CsgVoxelizerTest {
    @Test
    void axisAlignedVoxelCountMatchesVolume() {
        Set<P> voxels = collect(new CsgVoxelizer.VoxelDefinition(10, 20, 30, 3, 4, 5, 0, 0, 0));
        assertEquals(3 * 4 * 5, voxels.size());
        assertTrue(voxels.contains(new P(10, 20, 30)));
        assertTrue(voxels.contains(new P(12, 23, 34)));
        assertFalse(voxels.contains(new P(13, 20, 30)));
    }

    @Test
    void rotatedBoxIsSymmetricForOppositeAngles() {
        var defA = new CsgVoxelizer.VoxelDefinition(0, 0, 0, 5, 3, 4, 0, 30, 0);
        var defB = new CsgVoxelizer.VoxelDefinition(0, 0, 0, 5, 3, 4, 0, -30, 0);
        assertEquals(collect(defA).size(), collect(defB).size());
    }

    @Test
    void fullTurnsDoNotChangeVoxelization() {
        var defA = new CsgVoxelizer.VoxelDefinition(-2, 7, 1, 4, 4, 2, 0, 0, 0);
        var defB = new CsgVoxelizer.VoxelDefinition(-2, 7, 1, 4, 4, 2, 360, 0, 0);
        assertEquals(collect(defA), collect(defB));
    }

    private static Set<P> collect(CsgVoxelizer.VoxelDefinition def) {
        HashSet<P> out = new HashSet<>();
        CsgVoxelizer.forEachVoxel(def, (x, y, z) -> out.add(new P(x, y, z)));
        return out;
    }

    private record P(int x, int y, int z) {
    }
}
