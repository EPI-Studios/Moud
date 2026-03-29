package com.moud.net.protocol;


import java.util.List;

public record SceneSnapshot(long requestId, long revision, List<NodeSnapshot> nodes) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_SNAPSHOT;
    }

    public record NodeSnapshot(long nodeId, long parentId, String name, String type, List<Property> properties, List<Uniform> uniforms) {
        public NodeSnapshot(long nodeId, long parentId, String name, String type, List<Property> properties) {
            this(nodeId, parentId, name, type, properties, List.of());
        }
    }

    public record Property(String key, String value) {
    }

    public record Uniform(String key, List<Float> values) {
    }
}
