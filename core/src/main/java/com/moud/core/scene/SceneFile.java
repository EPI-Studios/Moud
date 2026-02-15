package com.moud.core.scene;

import java.util.List;
import java.util.Map;

public record SceneFile(
        int format,
        String sceneId,
        String displayName,
        List<NodeEntry> nodes
) {
    public static final int FORMAT_V1 = 1;

    public SceneFile {
        if (sceneId == null) {
            sceneId = "";
        }
        if (displayName == null) {
            displayName = "";
        }
        if (nodes == null) {
            nodes = List.of();
        } else {
            nodes = List.copyOf(nodes);
        }
    }

    public record NodeEntry(
            long id,
            long parent,
            String name,
            String type,
            Map<String, String> properties
    ) {
        public NodeEntry {
            if (name == null) {
                name = "";
            }
            if (type == null || type.isBlank()) {
                type = "Node";
            }
            if (properties == null) {
                properties = Map.of();
            } else {
                properties = Map.copyOf(properties);
            }
        }
    }
}

