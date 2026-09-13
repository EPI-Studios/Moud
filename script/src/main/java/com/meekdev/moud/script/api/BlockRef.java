package com.meekdev.moud.script.api;

import java.util.function.Consumer;
import com.meekdev.moud.core.math.Vec3;

// the blocks of the level a side runs in, as far as a query needs them
public interface BlockRef {

    // block is the block's id, like minecraft:stone
    record Hit(Vec3 at, Vec3 normal, double distance, String block) {}

    // null when nothing solid is in the way
    Hit raycast(Vec3 from, Vec3 direction, double range);

    // the block at a position as the game writes it, like minecraft:oak_stairs[facing=north]
    String get(int x, int y, int z);

    // throws when the text names no block, saying why
    void set(int x, int y, int z, String block);

    // every block in the box between two corners, both included, and how many that was
    long fill(int x0, int y0, int z0, int x1, int y1, int z1, String block);

    // whether this side may change blocks at all. a client's copy of the level is the server's to change
    boolean writable();

    // a ray that stops at water and lava too when fluids is set
    default Hit raycast(Vec3 from, Vec3 direction, double range, boolean fluids) {
        return raycast(from, direction, range);
    }

    // the block's id without its state, like minecraft:oak_stairs
    default String id(int x, int y, int z) {
        String full = get(x, y, z);
        int bracket = full.indexOf('[');
        return bracket < 0 ? full : full.substring(0, bracket);
    }

    // something a body stands on and cannot walk through
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

    // how lit a block is, from 0 to 15, the brighter of the sun and the lamps
    default int light(int x, int y, int z) {
        return 15;
    }

    // the y of the highest block something could stand on at a column, or the bottom of the world
    default int top(int x, int z) {
        for (int y = 319; y >= -64; y--) {
            if (solid(x, y, z)) return y;
        }
        return -64;
    }

    // a block state turned by quarter turns about up, so stairs still face the way a pasted build does
    default String rotate(String block, int quarterTurns) {
        return block;
    }

    // one block that changed, and what it is now
    record Change(int x, int y, int z, String block) {}

    // every change to the level since the last drain, from anything: a place, a player, the game itself
    default void drainChanges(Consumer<Change> out) {}
}
