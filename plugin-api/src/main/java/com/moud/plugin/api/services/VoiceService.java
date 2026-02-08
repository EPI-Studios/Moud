package com.moud.plugin.api.services;

import com.moud.plugin.api.player.PlayerContext;

import java.util.Map;

/**
 * Server-side voice chat routing, state inspection, and recording/replay controls.
 */
public interface VoiceService {
    Map<String, Object> state(PlayerContext player);

    Map<String, Object> routing(PlayerContext player);

    void setRouting(PlayerContext player, Map<String, Object> options);

    /**
     * Start recording voice frames for a player. Returns the recording id.
     * Pass {@code null} or blank to auto-generate.
     */
    String startRecording(PlayerContext player, String recordingId, long maxDurationMs);

    void stopRecording(PlayerContext player);

    void deleteRecording(String recordingId);

    void replayRecording(String recordingId, Map<String, Object> options);
}
