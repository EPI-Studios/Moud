package com.moud.server.plugin.impl;

import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.VoiceService;
import com.moud.server.audio.ServerVoiceChatManager;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;

public final class VoiceServiceImpl implements VoiceService {

    private final Logger logger;

    public VoiceServiceImpl(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Map<String, Object> state(PlayerContext player) {
        if (player == null) {
            return null;
        }
        return ServerVoiceChatManager.getInstance().snapshotState(player.player());
    }

    @Override
    public Map<String, Object> routing(PlayerContext player) {
        if (player == null) {
            return null;
        }
        return ServerVoiceChatManager.getInstance().getRouting(player.uuid());
    }

    @Override
    public void setRouting(PlayerContext player, Map<String, Object> options) {
        if (player == null) {
            return;
        }
        ServerVoiceChatManager.getInstance().setRouting(player.uuid(), options);
    }

    @Override
    public String startRecording(PlayerContext player, String recordingId, long maxDurationMs) {
        if (player == null) {
            return null;
        }
        String id = recordingId != null && !recordingId.isBlank() ? recordingId : null;
        return ServerVoiceChatManager.getInstance().startRecording(player.uuid(), id, maxDurationMs);
    }

    @Override
    public void stopRecording(PlayerContext player) {
        if (player == null) {
            return;
        }
        ServerVoiceChatManager.getInstance().stopRecording(player.uuid());
    }

    @Override
    public void deleteRecording(String recordingId) {
        ServerVoiceChatManager.getInstance().deleteRecording(recordingId);
    }

    @Override
    public void replayRecording(String recordingId, Map<String, Object> options) {
        ServerVoiceChatManager.getInstance().replayRecording(recordingId, options);
    }
}

