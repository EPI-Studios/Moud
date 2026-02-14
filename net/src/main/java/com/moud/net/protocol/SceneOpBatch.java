package com.moud.net.protocol;

import java.util.List;

public record SceneOpBatch(long batchId, boolean atomic, List<SceneOp> ops) implements Message {
    public SceneOpBatch(long batchId, List<SceneOp> ops) {
        this(batchId, false, ops);
    }

    @Override
    public MessageType type() {
        return MessageType.SCENE_OP_BATCH;
    }
}
