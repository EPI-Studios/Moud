package com.moud.client.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.client.network.ClientNetworkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientMicrophoneManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientMicrophoneManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_CHUNK_SIZE = 960;
    private static final AudioFormat DEFAULT_FORMAT = new AudioFormat(48000f, 16, 1, true, false);

    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private volatile String preferredInputDevice = "";

    private TargetDataLine dataLine;
    private Thread captureThread;
    private String sessionId;

    public synchronized void start(Map<String, Object> options) {
        if (capturing.get()) {
            LOGGER.warn("Microphone capture already running");
            return;
        }

        try {
            AudioFormat format = DEFAULT_FORMAT;
            if (options != null && options.get("sampleRate") instanceof Number number) {
                format = new AudioFormat(number.floatValue(), 16, 1, true, false);
            }

            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.error("Requested microphone format {} not supported", format);
                sendError("unsupported_format");
                return;
            }

            dataLine = selectInputLine(info);
            dataLine.open(format);
            dataLine.start();

            sessionId = options != null && options.get("sessionId") instanceof String id && !id.isEmpty()
                    ? id
                    : UUID.randomUUID().toString();

            capturing.set(true);
            final AudioFormat captureFormat = format;
            captureThread = new Thread(() -> captureLoop(captureFormat), "Moud-Microphone");
            captureThread.setDaemon(true);
            captureThread.start();

            LOGGER.info("Microphone capture started (session={})", sessionId);
            sendState("started");
        } catch (LineUnavailableException e) {
            LOGGER.error("Failed to open microphone line", e);
            sendError("line_unavailable");
        }
    }

    public synchronized void stop() {
        if (!capturing.getAndSet(false)) {
            return;
        }

        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }

        if (dataLine != null) {
            dataLine.stop();
            dataLine.close();
            dataLine = null;
        }

        LOGGER.info("Microphone capture stopped (session={})", sessionId);
        sendState("stopped");
        sessionId = null;
    }

    public boolean isCapturing() {
        return capturing.get();
    }

    private void captureLoop(AudioFormat format) {
        byte[] buffer = new byte[DEFAULT_CHUNK_SIZE];

        while (capturing.get() && !Thread.currentThread().isInterrupted()) {
            int read = dataLine.read(buffer, 0, buffer.length);
            if (read <= 0) {
                continue;
            }

            byte[] chunk = buffer;
            if (read != buffer.length) {
                chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
            }

            sendChunk(chunk, format);
        }
    }

    private void sendChunk(byte[] data, AudioFormat format) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("sampleRate", format.getSampleRate());
            payload.put("channels", format.getChannels());
            payload.put("data", Base64.getEncoder().encodeToString(data));

            String json = MAPPER.writeValueAsString(payload);
            ClientNetworkManager.sendToServer("audio:microphone:chunk", json);
        } catch (Exception e) {
            LOGGER.error("Failed to serialise microphone chunk", e);
        }
    }

    private void sendError(String code) {
        try {
            Map<String, Object> payload = Map.of(
                    "sessionId", sessionId,
                    "error", code
            );
            String json = MAPPER.writeValueAsString(payload);
            ClientNetworkManager.sendToServer("audio:microphone:error", json);
        } catch (Exception e) {
            LOGGER.error("Failed to send microphone error", e);
        }
    }

    private void sendState(String state) {
        try {
            Map<String, Object> payload = Map.of(
                    "sessionId", sessionId,
                    "state", state
            );
            String json = MAPPER.writeValueAsString(payload);
            ClientNetworkManager.sendToServer("audio:microphone:state", json);
        } catch (Exception e) {
            LOGGER.error("Failed to send microphone state", e);
        }
    }

    public synchronized void setPreferredInputDevice(String deviceName) {
        this.preferredInputDevice = deviceName == null ? "" : deviceName;
    }

    public synchronized String getPreferredInputDevice() {
        return preferredInputDevice;
    }

    public java.util.List<String> getAvailableInputDevices() {
        java.util.List<String> devices = new java.util.ArrayList<>();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, DEFAULT_FORMAT);
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            if (mixer.isLineSupported(info)) {
                devices.add(mixerInfo.getName());
            }
        }
        return devices;
    }

    private TargetDataLine selectInputLine(DataLine.Info info) throws LineUnavailableException {
        if (preferredInputDevice != null && !preferredInputDevice.isEmpty()) {
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                if (preferredInputDevice.equals(mixerInfo.getName())) {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(info)) {
                        LOGGER.info("Opening preferred microphone device: {}", mixerInfo.getName());
                        return (TargetDataLine) mixer.getLine(info);
                    }
                }
            }
            LOGGER.warn("Preferred microphone '{}' unavailable. Falling back to system default.", preferredInputDevice);
        }

        return (TargetDataLine) AudioSystem.getLine(info);
    }
}
