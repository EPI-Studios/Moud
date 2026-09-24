package com.meekdev.moud.core.nav;

import java.util.Arrays;

final class Voxels {

    static final float EDGE = 1e-3f;

    private static final float[] NONE = new float[0];
    private static final float CENTRE = 0.5f;
    private static final float TOUCH = 0.01f;

    final int x0;
    final int z0;
    final int width;
    final int depth;
    final int base;
    final int layers;
    final float height;
    private final BlockShape[] shapes;
    private final float[][] floors;

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
                for (int layer = 0; layer < layers; layer++) {
                    shapes[index(x, z, layer)] = world.block(x0 + x, base + layer, z0 + z);
                }
            }
        }
    }

    BlockShape at(int x, int layer, int z) {
        int column = x - x0;
        int row = z - z0;
        if (!inside(column, row) || layer < 0 || layer >= layers) return BlockShape.AIR;
        return shapes[index(column, row, layer)];
    }

    BlockShape onTop(int x, int layer, int z, float top) {
        return at(x, layer + (int) Math.floor(top + EDGE), z);
    }

    static float top(BlockShape shape) {
        float top = -1;
        for (int box = 0; box < shape.boxCount(); box++) {
            boolean coversCentre = shape.minX(box) <= CENTRE && shape.maxX(box) >= CENTRE
                    && shape.minZ(box) <= CENTRE && shape.maxZ(box) >= CENTRE;
            if (coversCentre) top = Math.max(top, shape.maxY(box));
        }
        return top;
    }

    boolean clear(int x, int z, float from, float to) {
        int first = (int) Math.floor(from - base) - 1;
        int last = (int) Math.floor(to - base);
        for (int layer = Math.max(0, first); layer <= last && layer < layers; layer++) {
            BlockShape shape = at(x, layer, z);
            if (shape.climbable()) continue;
            for (int box = 0; box < shape.boxCount(); box++) {
                float bottom = base + layer + shape.minY(box);
                float top = base + layer + shape.maxY(box);
                if (top > from + TOUCH && bottom < to - TOUCH) return false;
            }
        }
        return true;
    }

    float[] floors(int x, int z) {
        int column = x - x0;
        int row = z - z0;
        if (!inside(column, row)) return NONE;
        float[] known = floors[column * depth + row];
        if (known != null) return known;
        float[] found = new float[layers];
        int count = 0;
        for (int layer = 0; layer < layers; layer++) {
            float top = top(at(x, layer, z));
            if (top < 0) continue;
            float surface = base + layer + top;
            if (onTop(x, layer, z, top).liquid() == BlockShape.Liquid.LAVA) continue;
            if (!clear(x, z, surface, surface + height)) continue;
            found[count++] = surface;
        }
        float[] surfaces = count == 0 ? NONE : Arrays.copyOf(found, count);
        floors[column * depth + row] = surfaces;
        return surfaces;
    }

    private boolean inside(int column, int row) {
        return column >= 0 && row >= 0 && column < width && row < depth;
    }

    private int index(int column, int row, int layer) {
        return (column * depth + row) * layers + layer;
    }
}
