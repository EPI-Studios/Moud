package com.moud.server.audio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerMicrophoneManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMicrophoneManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ServerMicrophoneManager INSTANCE = new ServerMicrophoneManager();

    private final Map<UUID, MicrophoneSession> sessions = new ConcurrentHashMap<>();

    private ServerMicrophoneManager() {
    }

    public static ServerMicrophoneManager getInstance() {
        return INSTANCE;
    }

    public void handleEvent(Player player, String eventName, String payload) {
        try {
            Map<String, Object> data = payload == null || payload.isEmpty() ? Map.of() : MAPPER.readValue(payload, Map.class);
            switch (eventName) {
                case "audio:microphone:chunk" -> handleChunk(player, data);
                case "audio:microphone:state" -> handleState(player, data);
                case "audio:microphone:error" -> handleError(player, data);
                default -> LOGGER.debug("Unhandled microphone event {}", eventName);
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse microphone payload from {}", player.getUsername(), e);
        }
    }

    public boolean isActive(Player player) {
        MicrophoneSession session = sessions.get(player.getUuid());
        return session != null && session.active;
    }

    public MicrophoneSession snapshot(Player player) {
        MicrophoneSession session = sessions.get(player.getUuid());
        return session != null ? session.copy() : null;
    }

    private void handleState(Player player, Map<String, Object> data) {
        String sessionId = string(data.get("sessionId"));
        String state = string(data.get("state"));

        if (sessionId == null || state == null) {
            return;
        }

        MicrophoneSession session = sessions.computeIfAbsent(player.getUuid(), uuid -> new MicrophoneSession(sessionId));
        session.sessionId = sessionId;
        session.active = "started".equalsIgnoreCase(state);
        session.lastState = state;
        if (!session.active && "stopped".equalsIgnoreCase(state)) {
            session.lastChunkBase64 = null;
            session.lastChunkBytes = null;
        }
        LOGGER.debug("Microphone state for {} -> {} (session {})", player.getUsername(), state, sessionId);
    }

    private void handleChunk(Player player, Map<String, Object> data) {
        String sessionId = string(data.get("sessionId"));
        if (sessionId == null) {
            return;
        }

        MicrophoneSession session = sessions.computeIfAbsent(player.getUuid(), uuid -> new MicrophoneSession(sessionId));
        session.sessionId = sessionId;
        session.active = true;

        session.sampleRate = number(data.get("sampleRate"), session.sampleRate);
        session.channels = (int) number(data.get("channels"), session.channels);
        session.lastTimestamp = number(data.get("timestamp"), System.currentTimeMillis());

        Object encoded = data.get("data");
        if (encoded instanceof String base64 && !base64.isEmpty()) {
            session.lastChunkBase64 = base64;
            try {
                session.lastChunkBytes = Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid base64 microphone chunk from {}", player.getUsername(), e);
            }
        }
    }

    private void handleError(Player player, Map<String, Object> data) {
        String sessionId = string(data.get("sessionId"));
        String error = string(data.get("error"));
        LOGGER.warn("Microphone error from {} (session {}): {}", player.getUsername(), sessionId, error);
        sessions.remove(player.getUuid());
    }

    private static String string(Object value) {
        return value instanceof String string && !string.isEmpty() ? string : null;
    }

    private static long number(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    public static final class MicrophoneSession {
        private String sessionId;
        private boolean active;
        private String lastState;
        private long lastTimestamp;
        private long sampleRate = 48000L;
        private int channels = 1;
        private String lastChunkBase64;
        private byte[] lastChunkBytes;

        private MicrophoneSession(String sessionId) {
            this.sessionId = sessionId;
        }

        public MicrophoneSession copy() {
            MicrophoneSession clone = new MicrophoneSession(this.sessionId);
            clone.active = this.active;
            clone.lastState = this.lastState;
            clone.lastTimestamp = this.lastTimestamp;
            clone.sampleRate = this.sampleRate;
            clone.channels = this.channels;
            clone.lastChunkBase64 = this.lastChunkBase64;
            clone.lastChunkBytes = this.lastChunkBytes != null ? this.lastChunkBytes.clone() : null;
            return clone;
        }

        public Map<String, Object> toMap() {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("sessionId", sessionId);
            map.put("active", active);
            map.put("state", lastState);
            map.put("timestamp", lastTimestamp);
            map.put("sampleRate", sampleRate);
            map.put("channels", channels);
            if (lastChunkBase64 != null) {
                map.put("chunkBase64", lastChunkBase64);
            }
            return map;
        }

        public byte[] getLastChunkBytes() {
            return lastChunkBytes;
        }
    }
}
