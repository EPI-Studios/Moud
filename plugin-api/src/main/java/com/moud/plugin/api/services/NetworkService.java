package com.moud.plugin.api.services;

import com.moud.plugin.api.player.PlayerContext;
import net.minestom.server.entity.Player;

public interface NetworkService {
    void broadcast(String eventName, String jsonPayload);
    void broadcast(String eventName, Object payload);
    void send(PlayerContext player, String eventName, String jsonPayload);
    void send(PlayerContext player, String eventName, Object payload);
    void send(Player player, String eventName, String jsonPayload);
    void send(Player player, String eventName, Object payload);
}
