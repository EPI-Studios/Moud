package com.meekdev.moud.script.api;

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
}
