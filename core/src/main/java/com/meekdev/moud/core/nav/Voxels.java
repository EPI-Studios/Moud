package com.meekdev.moud.core.nav;

import java.util.Arrays;

final class Voxels {

    private static final float[] NONE = new float[0];

    final int x0;
    final int z0;
    final int width;
    final int depth;
    final int base;
    final int layers;
    private final BlockShape[] shapes;
    private final float[][] floors;
    private final float height;

    Voxels(NavMeshes.World world, int x0, int z0, int x1, int z1, int base, int layers, float height) {
        this.x0 = x0;
        this.z0 = z0;
        this.width = x1 - x0;
        this.depth = z1 - z0;
        this.base = base;
        this.layers = layers;
        this.height = height;
        shapes = new BlockShape[width * depth * layers];
        floors = new float[width * depth][];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                for (int i = 0; i < layers; i++) shapes[(x * depth + z) * layers + i] = world.block(x0 + x, base + i, z0 + z);
            }
        }
    }

    BlockShape at(int x, int layer, int z) {
        int lx = x - x0, lz = z - z0;
        if (lx < 0 || lz < 0 || lx >= width || lz >= depth || layer < 0 || layer >= layers) return BlockShape.AIR;
        return shapes[(lx * depth + lz) * layers + layer];
    }

    static float top(BlockShape shape) {
        float[] b = shape.boxes();
        float top = 0;
        for (int k = 4; k < b.length; k += 6) top = Math.max(top, b[k]);
        return top;
    }

    boolean clear(int x, int z, float from, float to) {
        int first = (int) Math.floor(from - base) - 1, last = (int) Math.floor(to - base);
        for (int i = Math.max(0, first); i <= last && i < layers; i++) {
            float[] b = at(x, i, z).boxes();
            for (int k = 0; k + 5 < b.length; k += 6) {
                float low = base + i + b[k + 1], high = base + i + b[k + 4];
                if (high > from + 0.01f && low < to - 0.01f) return false;
            }
        }
        return true;
    }

    float[] floors(int x, int z) {
        int lx = x - x0, lz = z - z0;
        if (lx < 0 || lz < 0 || lx >= width || lz >= depth) return NONE;
        float[] known = floors[lx * depth + lz];
        if (known != null) return known;
        float[] found = new float[layers];
        int n = 0;
        for (int i = 0; i < layers; i++) {
            BlockShape shape = at(x, i, z);
            if (shape.empty()) continue;
            float surface = base + i + top(shape);
            int feet = i + (int) Math.floor(top(shape) + 1e-3f);
            if (at(x, feet, z).liquid() == BlockShape.Liquid.LAVA) continue;
            if (!clear(x, z, surface, surface + height)) continue;
            found[n++] = surface;
        }
        float[] out = n == 0 ? NONE : Arrays.copyOf(found, n);
        floors[lx * depth + lz] = out;
        return out;
    }
}
