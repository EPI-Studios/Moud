package com.moud.server.proxy;

import com.moud.server.assets.AssetManager;
import com.moud.server.editor.SceneManager;

public final class ModelProxyBootstrap {
    private ModelProxyBootstrap() {}

    public static void ensureAssetsReady() {
        try {
            AssetManager manager = SceneManager.getInstance().getAssetManager();
            if (manager != null) {
                manager.refresh();
            }
        } catch (Exception ignored) {
        }
    }
}
