package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;

public final class PlayerStateApi {

    public PlayerStateApi() {
    }

    public void set(String key, String value) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null) {
            runtime.setPlayerState(key, value);
        }
    }

    public String get(String key) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        return runtime == null ? "" : runtime.getPlayerState(key);
    }

    public void clear(String key) {
        set(key, "");
    }
}
