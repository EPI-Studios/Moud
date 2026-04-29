package com.moud.net.protocol;

import java.util.List;

public record SceneSnapshotDelta(
        long revision,
        List<SceneSnapshot.NodeSnapshot> upserts,
        List<Long> removed
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_SNAPSHOT_DELTA;
    }
}
