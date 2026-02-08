package com.moud.server.plugin.impl;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.MicrophoneService;
import com.moud.server.audio.ServerMicrophoneManager;
import com.moud.server.network.ServerNetworkManager;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;

public final class MicrophoneServiceImpl implements MicrophoneService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Logger logger;

    public MicrophoneServiceImpl(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void start(PlayerContext player, Map<String, Object> options) {
        send(player, "audio:microphone:start", options);
    }

    @Override
    public void stop(PlayerContext player) {
        send(player, "audio:microphone:stop", Map.of());
    }

    @Override
    public boolean isActive(PlayerContext player) {
        if (player == null) {
            return false;
        }
        return ServerMicrophoneManager.getInstance().isActive(player.player());
    }

    @Override
    public Map<String, Object> session(PlayerContext player) {
        if (player == null) {
            return null;
        }
        ServerMicrophoneManager.MicrophoneSession session = ServerMicrophoneManager.getInstance().snapshot(player.player());
        return session != null ? session.toMap() : null;
    }

    private void send(PlayerContext player, String event, Map<String, Object> payload) {
        if (player == null) {
            return;
        }
        try {
            String json = JSON.writeValueAsString(payload == null ? Map.of() : payload);
            ServerNetworkManager manager = ServerNetworkManager.getInstance();
            if (manager != null) {
                manager.sendScriptEvent(player.player(), event, json);
            }
        } catch (Exception e) {
            logger.warn("Failed to send microphone command {} to {}", event, player.username(), e);
        }
    }
}

