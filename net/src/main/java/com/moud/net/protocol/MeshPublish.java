package com.moud.net.protocol;

public record MeshPublish(
        long nodeId,
        byte[] hash,
        int chunkIndex,
        int chunkCount,
        byte[] payload
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.MESH_PUBLISH;
    }
}
