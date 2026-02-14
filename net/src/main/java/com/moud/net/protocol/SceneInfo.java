package com.moud.net.protocol;

import java.util.Objects;

public record SceneInfo(String sceneId, String displayName) {
    public SceneInfo {
        Objects.requireNonNull(sceneId, "sceneId");
        if (displayName == null) {
            displayName = "";
        }
    }

    public String uiLabel() {
        if (displayName.isBlank()) {
            return sceneId;
        }
        return displayName;
    }
}

