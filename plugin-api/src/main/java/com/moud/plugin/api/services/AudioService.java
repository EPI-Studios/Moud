package com.moud.plugin.api.services;

import com.moud.plugin.api.player.PlayerContext;

import java.util.Map;

/**
 * Server-driven client audio controls
 * Payload schema matches the client-side `audio:*` events.
 */
public interface AudioService {
    void play(PlayerContext player, Map<String, Object> options);
    void update(PlayerContext player, Map<String, Object> options);
    void stop(PlayerContext player, Map<String, Object> options);
}

