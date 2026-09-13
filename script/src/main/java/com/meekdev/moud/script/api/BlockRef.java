package com.meekdev.moud.script.api;

import java.util.function.Consumer;
import com.meekdev.moud.core.math.Vector3;

public interface BlockRef {

    record Hit(Vector3 at, Vector3 normal, double distance, String block) {}

    Hit raycast(Vector3 from, Vector3 direction, double range);

    String get(int x, int y, int z);

    void set(int x, int y, int z, String block);

    long fill(int x0, int y0, int z0, int x1, int y1, int z1, String block);

    boolean writable();

    Hit raycast(Vector3 from, Vector3 direction, double range, boolean fluids);

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

    int light(int x, int y, int z);

    default int top(int x, int z) {
        for (int y = 319; y >= -64; y--) {
            if (solid(x, y, z)) return y;
        }
        return -64;
    }

    String rotate(String block, int quarterTurns);

    record Change(int x, int y, int z, String block) {}

    void drainChanges(Consumer<Change> out);
}
