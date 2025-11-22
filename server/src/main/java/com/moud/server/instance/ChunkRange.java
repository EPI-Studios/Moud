package com.moud.server.instance;

import java.util.function.BiConsumer;


//utility class for iterating chunk coordinates around a center
public final class ChunkRange {
    private ChunkRange() {}

    public static void chunksInRange(int centerX, int centerZ, int radius, BiConsumer<Integer, Integer> consumer) {
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                consumer.accept(x, z);
            }
        }
    }
}
