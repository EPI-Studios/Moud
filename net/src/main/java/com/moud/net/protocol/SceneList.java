package com.moud.net.protocol;

import java.util.List;

public record SceneList(List<SceneInfo> scenes, String activeSceneId) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_LIST;
    }
}

