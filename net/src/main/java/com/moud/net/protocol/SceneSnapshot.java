package com.moud.net.protocol;

import java.util.List;

public record SceneSnapshot(long requestId, long revision, List<NodeSnapshot> nodes) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_SNAPSHOT;
    }

    public record NodeSnapshot(long nodeId, long parentId, String name, String type, List<Property> properties) {
    }

    public record Property(String key, String value) {
    }
}
