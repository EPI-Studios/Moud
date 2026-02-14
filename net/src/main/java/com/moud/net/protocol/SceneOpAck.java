package com.moud.net.protocol;

import java.util.List;

public record SceneOpAck(long batchId, long sceneRevision, List<SceneOpResult> results) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_OP_ACK;
    }
}
