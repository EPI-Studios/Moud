package com.moud.plugin.api.services;

import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.client.ClientBridge;

import java.util.Optional;
import java.util.UUID;

public interface ClientService {
    Optional<ClientBridge> client(PlayerContext player);
    Optional<ClientBridge> client(UUID playerId);
    void send(PlayerContext player, String eventName, Object payload);
    void sendRaw(PlayerContext player, String eventName, String jsonPayload);
}
