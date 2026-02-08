package com.moud.plugin.api.services.client;

import com.moud.plugin.api.player.PlayerContext;

public interface ClientBridge {
    PlayerContext player();
    void send(String eventName, Object payload);
    void sendRaw(String eventName, String jsonPayload);
}
