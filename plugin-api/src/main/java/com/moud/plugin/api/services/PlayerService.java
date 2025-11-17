package com.moud.plugin.api.services;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.player.PlayerContext;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PlayerService {
    Collection<PlayerContext> onlinePlayers();
    Optional<PlayerContext> byName(String username);
    Optional<PlayerContext> byId(UUID uuid);
    void broadcast(String message);
    void sendMessage(PlayerContext player, String message);
    void sendActionBar(PlayerContext player, String message);
    void broadcastActionBar(String message);
    void teleport(PlayerContext player, Vector3 position);
}
