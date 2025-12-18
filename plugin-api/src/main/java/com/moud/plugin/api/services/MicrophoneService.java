package com.moud.plugin.api.services;

import com.moud.plugin.api.player.PlayerContext;

import java.util.Map;

/**
 * Controls client microphone capture and provides last-known capture session state.
 * Payload schema matches the client-side `audio:microphone:*` events.
 */
public interface MicrophoneService {
    void start(PlayerContext player, Map<String, Object> options);
    void stop(PlayerContext player);
    boolean isActive(PlayerContext player);
    Map<String, Object> session(PlayerContext player);
}

