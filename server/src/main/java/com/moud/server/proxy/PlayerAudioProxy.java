package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.moud.server.audio.ServerAudioManager;
import com.moud.server.audio.ServerMicrophoneManager;
import com.moud.server.audio.ServerVoiceChatManager;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@TsExpose
public final class PlayerAudioProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAudioProxy.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Player player;
    private final ServerAudioManager audioManager;
    private final ServerMicrophoneManager microphoneManager;
    private final ServerVoiceChatManager voiceChatManager;

    public PlayerAudioProxy(Player player) {
        this.player = player;
        this.audioManager = ServerAudioManager.getInstance();
        this.microphoneManager = ServerMicrophoneManager.getInstance();
        this.voiceChatManager = ServerVoiceChatManager.getInstance();
    }

    @HostAccess.Export
    public void play(Value options) {
        Map<String, Object> payload = toMap(options);
        audioManager.play(player, payload);
    }

    @HostAccess.Export
    public void update(Value options) {
        Map<String, Object> payload = toMap(options);
        audioManager.update(player, payload);
    }

    @HostAccess.Export
    public void stop(Value options) {
        Map<String, Object> payload = toMap(options);
        audioManager.stop(player, payload);
    }

    @HostAccess.Export
    public void startMicrophone(Value options) {
        Map<String, Object> payload = toMap(options);
        sendCommand("audio:microphone:start", payload);
    }

    @HostAccess.Export
    public void stopMicrophone() {
        sendCommand("audio:microphone:stop", Map.of());
    }

    @HostAccess.Export
    public boolean isMicrophoneActive() {
        return microphoneManager.isActive(player);
    }

    @HostAccess.Export
    public Map<String, Object> getMicrophoneSession() {
        ServerMicrophoneManager.MicrophoneSession session = microphoneManager.snapshot(player);
        return session != null ? session.toMap() : null;
    }

    @HostAccess.Export
    public Map<String, Object> getVoiceState() {
        return voiceChatManager.snapshotState(player);
    }

    @HostAccess.Export
    public Map<String, Object> getVoiceRouting() {
        return voiceChatManager.getRouting(player.getUuid());
    }

    @HostAccess.Export
    public void setVoiceRouting(Value options) {
        Map<String, Object> payload = toMap(options);
        voiceChatManager.setRouting(player.getUuid(), payload);
    }

    @HostAccess.Export
    public String startVoiceRecording(Value options) {
        Map<String, Object> payload = toMap(options);
        String recordingId = payload.get("id") instanceof String raw && !raw.isBlank() ? raw : null;
        long maxDurationMs = payload.get("maxDurationMs") instanceof Number number ? number.longValue() : 60_000L;
        return voiceChatManager.startRecording(player.getUuid(), recordingId, maxDurationMs);
    }

    @HostAccess.Export
    public void stopVoiceRecording() {
        voiceChatManager.stopRecording(player.getUuid());
    }

    @HostAccess.Export
    public void deleteVoiceRecording(String recordingId) {
        voiceChatManager.deleteRecording(recordingId);
    }

    @HostAccess.Export
    public void replayVoiceRecording(String recordingId, Value options) {
        Map<String, Object> payload = toMap(options);
        voiceChatManager.replayRecording(recordingId, payload);
    }

    private Map<String, Object> toMap(Value options) {
        if (options == null || options.isNull()) {
            return Map.of();
        }
        if (!options.hasMembers()) {
            LOGGER.warn("Audio payload for player {} is missing object members", player.getUsername());
            return Map.of();
        }

        Map<String, Object> payload = new java.util.HashMap<>();
        for (String key : options.getMemberKeys()) {
            Value value = options.getMember(key);
            payload.put(key, convertValue(value));
        }
        return payload;
    }

    private Object convertValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.fitsInDouble()) {
            return value.asDouble();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.hasArrayElements()) {
            int size = (int) value.getArraySize();
            List<Object> array = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                array.add(convertValue(value.getArrayElement(i)));
            }
            return array;
        }
        if (value.hasMembers()) {
            Map<String, Object> nested = new java.util.HashMap<>();
            for (String key : value.getMemberKeys()) {
                nested.put(key, convertValue(value.getMember(key)));
            }
            return nested;
        }
        return value.toString();
    }

    private void sendCommand(String event, Map<String, Object> payload) {
        try {
            String json = JSON.writeValueAsString(payload);
            ServerNetworkManager manager = ServerNetworkManager.getInstance();
            if (manager != null) {
                manager.sendScriptEvent(player, event, json);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to send microphone command {} to player {}", event, player.getUsername(), e);
        }
    }
}
