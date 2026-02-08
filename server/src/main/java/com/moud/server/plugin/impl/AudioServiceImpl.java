package com.moud.server.plugin.impl;

import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.AudioService;
import com.moud.server.audio.ServerAudioManager;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;

public final class AudioServiceImpl implements AudioService {

    private final Logger logger;

    public AudioServiceImpl(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void play(PlayerContext player, Map<String, Object> options) {
        if (player == null) {
            return;
        }
        ServerAudioManager.getInstance().play(player.player(), options);
    }

    @Override
    public void update(PlayerContext player, Map<String, Object> options) {
        if (player == null) {
            return;
        }
        ServerAudioManager.getInstance().update(player.player(), options);
    }

    @Override
    public void stop(PlayerContext player, Map<String, Object> options) {
        if (player == null) {
            return;
        }
        ServerAudioManager.getInstance().stop(player.player(), options);
    }
}

