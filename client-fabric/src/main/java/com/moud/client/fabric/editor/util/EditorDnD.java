package com.moud.client.fabric.editor.util;

import com.miry.ui.dnd.DragPayload;

public final class EditorDnD {
    private EditorDnD() {
    }

    public static final String TYPE_SCENE_ID = "moud.scene_id";
    public static final String TYPE_ASSET_PATH = "moud.asset_path";
    public static final String TYPE_IMAGE_PATH = "moud.image_path";

    public static DragPayload<String> sceneId(String sceneId) {
        return new DragPayload<>(TYPE_SCENE_ID, sceneId);
    }

    public static DragPayload<String> assetPath(String path) {
        return new DragPayload<>(TYPE_ASSET_PATH, path);
    }

    public static DragPayload<String> imagePath(String path) {
        return new DragPayload<>(TYPE_IMAGE_PATH, path);
    }

    public static String filename(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : path;
    }
}

