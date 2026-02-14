package com.moud.net.protocol;

import java.util.Objects;

public record SceneSelect(String sceneId) implements Message {
    public SceneSelect {
        Objects.requireNonNull(sceneId, "sceneId");
    }

    @Override
    public MessageType type() {
        return MessageType.SCENE_SELECT;
    }
}

