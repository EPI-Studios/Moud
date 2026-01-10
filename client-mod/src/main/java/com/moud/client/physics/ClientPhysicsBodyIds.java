package com.moud.client.physics;

public final class ClientPhysicsBodyIds {
    private static final long KIND_PRIMITIVE = 0x8000_0000_0000_0000L;
    private static final long KIND_CHUNK = 0xC000_0000_0000_0000L;
    private static final long PAYLOAD_MASK = 0x3FFF_FFFF_FFFF_FFFFL;
    private static final int CHUNK_COORD_OFFSET = 1 << 30;

    private ClientPhysicsBodyIds() {
    }

    public static long primitive(long primitiveId) {
        return KIND_PRIMITIVE | (primitiveId & PAYLOAD_MASK);
    }

    public static long chunk(int chunkX, int chunkZ) {
        long x = (long) chunkX + CHUNK_COORD_OFFSET;
        long z = (long) chunkZ + CHUNK_COORD_OFFSET;
        if ((x & ~0x7FFF_FFFFL) != 0L || (z & ~0x7FFF_FFFFL) != 0L) {
            long fallback = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFF_FFFFL);
            return KIND_CHUNK | (fallback & PAYLOAD_MASK);
        }
        long payload = (x & 0x7FFF_FFFFL) | ((z & 0x7FFF_FFFFL) << 31);
        return KIND_CHUNK | payload;
    }
}

