package com.moud.net.protocol;

public record CollisionGeometryChunk(long nodeId, int chunkIndex, int chunkCount, byte[] payload) implements Message {
    @Override
    public MessageType type() {
        return MessageType.COLLISION_GEOMETRY_CHUNK;
    }
}
