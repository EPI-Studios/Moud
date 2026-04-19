package com.moud.client.fabric.editor.dialogs.sceneimport;

import com.moud.net.protocol.SceneInfo;
import java.util.Locale;

record SceneImportCard(SceneInfo scene, String title, String subtitle, String searchText) {
    static SceneImportCard of(SceneInfo scene) {
        String title = scene == null ? "" : scene.uiLabel();
        String subtitle = scene == null || scene.sceneId() == null ? "" : scene.sceneId();
        String search = (title + "\n" + subtitle).toLowerCase(Locale.ROOT);
        return new SceneImportCard(scene, title, subtitle, search);
    }

    boolean matches(String query) {
        return query == null || query.isBlank() || searchText.contains(query);
    }
}
