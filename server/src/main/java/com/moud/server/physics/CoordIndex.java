package com.moud.server.physics;

final class CoordIndex {
    private CoordIndex() {}

    static long chunkIndex(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xffffffffL);
    }

    static int chunkIndexGetX(long index) {
        return (int) (index >> 32);
    }

    static int chunkIndexGetZ(long index) {
        return (int) index;
    }
}
