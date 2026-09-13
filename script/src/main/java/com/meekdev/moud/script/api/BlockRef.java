package com.meekdev.moud.script.api;

import java.util.function.Consumer;
import com.meekdev.moud.core.math.Vec3;

public interface BlockRef {

    record Hit(Vec3 at, Vec3 normal, double distance, String block) {}

    Hit raycast(Vec3 from, Vec3 direction, double range);

    String get(int x, int y, int z);

    void set(int x, int y, int z, String block);

    long fill(int x0, int y0, int z0, int x1, int y1, int z1, String block);

    boolean writable();

    default Hit raycast(Vec3 from, Vec3 direction, double range, boolean fluids) {
        return raycast(from, direction, range);
    }

    default String id(int x, int y, int z) {
        String full = get(x, y, z);
        int bracket = full.indexOf('[');
        return bracket < 0 ? full : full.substring(0, bracket);
    }

    default boolean solid(int x, int y, int z) {
        return !id(x, y, z).equals("minecraft:air");
    }

    default boolean air(int x, int y, int z) {
        String id = id(x, y, z);
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    default boolean fluid(int x, int y, int z) {
        String id = id(x, y, z);
        return id.equals("minecraft:water") || id.equals("minecraft:lava");
    }

    default int light(int x, int y, int z) {
        return 15;
    }

    default int top(int x, int z) {
        for (int y = 319; y >= -64; y--) {
            if (solid(x, y, z)) return y;
        }
        return -64;
    }

    default String rotate(String block, int quarterTurns) {
        return block;
    }

    record Change(int x, int y, int z, String block) {}

    default void drainChanges(Consumer<Change> out) {}
}
