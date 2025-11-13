package com.moud.client.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.client.audio.ClientAudioService;
import com.moud.client.audio.ClientMicrophoneManager;
import com.moud.client.audio.VoiceChatController;
import com.moud.client.settings.VoiceSettingsManager;
import net.minecraft.client.MinecraftClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class AudioService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AudioService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Context context;
    private final ClientMicrophoneManager microphoneManager = new ClientMicrophoneManager();
    private final MicrophoneAPI microphoneAPI = new MicrophoneAPI();

    public AudioService() {
        String preferred = VoiceSettingsManager.get().inputDeviceName;
        if (preferred != null && !preferred.isEmpty()) {
            microphoneManager.setPreferredInputDevice(preferred);
        }
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void handleNetworkEvent(String eventName, String payload) {
        if (eventName.startsWith("audio:microphone:")) {
            handleMicrophoneEvent(eventName, payload);
            return;
        }
        ClientAudioService.getInstance().handleNetworkEvent(eventName, payload);
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

    public void tick() {
        ClientAudioService.getInstance().tick();
    }

    public void cleanUp() {
        context = null;
        microphoneManager.stop();
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
                microphoneManager.start(data);
            } else if ("audio:microphone:stop".equals(eventName)) {
                microphoneManager.stop();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to handle microphone event {}", eventName, e);
        }
    }

    public final class MicrophoneAPI {

        @HostAccess.Export
        public void start(Object options) {
            Map<String, Object> data = convert(options);
            microphoneManager.start(data);
        }

        @HostAccess.Export
        public void stop() {
            microphoneManager.stop();
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
        public String getPreferredInputDevice() {
            return microphoneManager.getPreferredInputDevice();
        }

        @HostAccess.Export
        public void setPreferredInputDevice(String deviceName) {
            microphoneManager.setPreferredInputDevice(deviceName);
        }

        private Map<String, Object> convert(Object value) {
            if (value == null) {
                return Map.of();
            }
            try {
                return MAPPER.convertValue(value, Map.class);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Failed to convert microphone options, using defaults", e);
                return Map.of();
            }
        }
    }
}
