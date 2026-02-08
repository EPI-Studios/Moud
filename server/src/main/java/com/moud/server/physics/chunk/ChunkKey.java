package com.moud.server.physics.chunk;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;

import java.util.UUID;

record ChunkKey(UUID instanceId, int chunkX, int chunkZ) {
    static final UUID FALLBACK_INSTANCE_ID = new UUID(0L, 0L);

    static ChunkKey from(Chunk chunk) {
        UUID id = chunk.getInstance() != null ? chunk.getInstance().getUniqueId() : null;
        if (id == null) {
            id = FALLBACK_INSTANCE_ID;
        }
        return new ChunkKey(id, chunk.getChunkX(), chunk.getChunkZ());
    }

    static ChunkKey from(Instance instance, int chunkX, int chunkZ) {
        UUID id = instance != null ? instance.getUniqueId() : null;
        if (id == null) {
            id = FALLBACK_INSTANCE_ID;
        }
        return new ChunkKey(id, chunkX, chunkZ);
    }
}
