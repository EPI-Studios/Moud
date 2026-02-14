package com.moud.net.protocol;

public record SceneSnapshotRequest(long requestId) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_SNAPSHOT_REQUEST;
    }
}
