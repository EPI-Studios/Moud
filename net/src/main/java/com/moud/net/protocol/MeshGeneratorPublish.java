package com.moud.net.protocol;

public record MeshGeneratorPublish(
        long nodeId,
        String scriptPath,
        String paramsJson,
        long seed
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.MESH_GENERATOR_PUBLISH;
    }
}
