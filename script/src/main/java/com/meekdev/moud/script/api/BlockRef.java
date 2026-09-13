package com.meekdev.moud.script.api;

import com.meekdev.moud.core.math.Vec3;

// the blocks of the level a side runs in, as far as a query needs them
public interface BlockRef {

    // block is the block's id, like minecraft:stone
    record Hit(Vec3 at, Vec3 normal, double distance, String block) {}

    // null when nothing solid is in the way
    Hit raycast(Vec3 from, Vec3 direction, double range);
}
