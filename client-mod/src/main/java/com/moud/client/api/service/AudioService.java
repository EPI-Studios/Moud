package com.moud.client.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.client.audio.ClientAudioService;
import com.moud.client.audio.ClientMicrophoneManager;
import com.moud.client.audio.ClientVoiceChatManager;
import com.moud.client.runtime.ClientScriptingRuntime;
import com.moud.client.settings.VoiceSettingsManager;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public final class AudioService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AudioService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Context context;
    private final ClientMicrophoneManager microphoneManager = new ClientMicrophoneManager();
    private final MicrophoneAPI microphoneAPI = new MicrophoneAPI();
    private final ClientVoiceChatManager voiceChatManager = new ClientVoiceChatManager();
    private final VoiceAPI voiceAPI = new VoiceAPI();

    public AudioService() {
        String preferred = VoiceSettingsManager.get().inputDeviceName;
        if (preferred != null && !preferred.isEmpty()) {
            microphoneManager.setPreferredInputDevice(preferred);
        }
        microphoneManager.setFrameConsumer(voiceChatManager::onMicrophoneFrame);
    }

    public void setRuntime(ClientScriptingRuntime runtime) {
        voiceChatManager.setRuntime(runtime);
    }

    public void setContext(Context context) {
        this.context = context;
        voiceChatManager.setContext(context);
    }

    public void handleNetworkEvent(String eventName, String payload) {
        if (eventName.startsWith("audio:microphone:")) {
            handleMicrophoneEvent(eventName, payload);
            return;
        }
        ClientAudioService.getInstance().handleNetworkEvent(eventName, payload);
    }

    public void handleVoiceStreamChunk(MoudPackets.VoiceStreamChunkPacket packet) {
        voiceChatManager.handleVoiceStreamChunk(packet);
    }

    @HostAccess.Export
    public void play(Object options) {
        sendToService("audio:play", options);
    }

    @HostAccess.Export
    public void update(Object options) {
        sendToService("audio:update", options);
    }

    @HostAccess.Export
    public void stop(Object options) {
        sendToService("audio:stop", options);
    }

    @HostAccess.Export
    public MicrophoneAPI getMicrophone() {
        return microphoneAPI;
    }

    @HostAccess.Export
    public VoiceAPI getVoice() {
        return voiceAPI;
    }

    public void tick() {
        ClientAudioService.getInstance().tick();
        voiceChatManager.tick();
    }

    public void cleanUp() {
        context = null;
        microphoneManager.stop();
        voiceChatManager.setContext(null);
        voiceChatManager.setRuntime(null);
        voiceChatManager.setEnabled(false);
    }

    private Map<String, Object> convertToMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        try {
            return MAPPER.convertValue(value, Map.class);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Failed to convert audio payload, using defaults", e);
            return Map.of();
        }
    }

    private void sendToService(String eventName, Object payload) {
        try {
            String json = payload == null ? "" : MAPPER.writeValueAsString(payload);
            MinecraftClient.getInstance().execute(() ->
                    ClientAudioService.getInstance().handleNetworkEvent(eventName, json));
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialise audio payload for {}", eventName, e);
        }
    }

    private void handleMicrophoneEvent(String eventName, String payload) {
        try {
            Map<String, Object> data = payload == null || payload.isEmpty()
                    ? Map.of()
                    : MAPPER.readValue(payload, Map.class);

            if ("audio:microphone:start".equals(eventName)) {
                voiceChatManager.setMicrophoneConfig(data);
                microphoneManager.start(data);
            } else if ("audio:microphone:stop".equals(eventName)) {
                microphoneManager.stop();
                voiceChatManager.onMicrophoneStopped();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to handle microphone event {}", eventName, e);
        }
    }

    public final class MicrophoneAPI {

        @HostAccess.Export
        public void start(Object options) {
            Map<String, Object> data = convertToMap(options);
            voiceChatManager.setMicrophoneConfig(data);
            microphoneManager.start(data);
        }

        @HostAccess.Export
        public void stop() {
            microphoneManager.stop();
            voiceChatManager.onMicrophoneStopped();
        }

        @HostAccess.Export
        public boolean isActive() {
            return microphoneManager.isCapturing();
        }

        @HostAccess.Export
        public java.util.List<String> getInputDevices() {
            return microphoneManager.getAvailableInputDevices();
        }

        @HostAccess.Export
        public float getCurrentLevel() {
            return microphoneManager.getCurrentLevel();
        }

        @HostAccess.Export
        public String getPreferredInputDevice() {
            return microphoneManager.getPreferredInputDevice();
        }

        @HostAccess.Export
        public void setPreferredInputDevice(String deviceName) {
            microphoneManager.setPreferredInputDevice(deviceName);
        }
    }

    public final class VoiceAPI {

        @HostAccess.Export
        public void registerProcessor(String id, Value factory) {
            voiceChatManager.registerProcessor(id, factory);
        }

        @HostAccess.Export
        public void setEnabled(boolean enabled) {
            voiceChatManager.setEnabled(enabled);
        }

        @HostAccess.Export
        public boolean isEnabled() {
            return voiceChatManager.isEnabled();
        }

        @HostAccess.Export
        public void setOutputProcessing(String speakerUuid, Object processing) {
            UUID speakerId = parseUuid(speakerUuid);
            if (speakerId == null) {
                return;
            }
            Map<String, Object> data = convertToMap(processing);
            voiceChatManager.setLocalOutputProcessing(speakerId, data);
        }

        @HostAccess.Export
        public void clearOutputProcessing(String speakerUuid) {
            UUID speakerId = parseUuid(speakerUuid);
            if (speakerId == null) {
                return;
            }
            voiceChatManager.clearLocalOutputProcessing(speakerId);
        }

        private UUID parseUuid(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
