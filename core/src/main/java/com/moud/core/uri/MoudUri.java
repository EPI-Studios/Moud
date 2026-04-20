package com.moud.core.uri;

import java.util.Objects;

public record MoudUri(
        String rawUri,
        String action,
        String serverAddress,
        String sceneId
) {
    public MoudUri {
        Objects.requireNonNull(rawUri, "rawUri");
        Objects.requireNonNull(action, "action");
        if (serverAddress != null) {
            serverAddress = serverAddress.trim();
            if (serverAddress.isEmpty()) {
                serverAddress = null;
            }
        }
        if (sceneId != null) {
            sceneId = sceneId.trim();
            if (sceneId.isEmpty()) {
                sceneId = null;
            }
        }
    }

    public boolean hasServerAddress() {
        return serverAddress != null;
    }

    public boolean hasSceneId() {
        return sceneId != null;
    }
}
