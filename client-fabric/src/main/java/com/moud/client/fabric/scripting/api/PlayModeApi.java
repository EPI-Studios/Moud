package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.render.loading.PlayLoading;

public final class PlayModeApi {

    public PlayModeApi() { }

    public boolean isReady() {
        return PlayLoading.isReady();
    }

    public void onReady(Runnable callback) {
        PlayLoading.addReadyListener(callback);
    }

    public String currentStatus() {
        PlayLoading.Entry e = PlayLoading.currentStatus();
        return e == null ? "" : e.label();
    }
}
