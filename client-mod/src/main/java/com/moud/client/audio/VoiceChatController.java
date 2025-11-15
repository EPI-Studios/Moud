package com.moud.client.audio;

import com.moud.client.api.service.AudioService;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.settings.VoiceSettingsManager;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


public final class VoiceChatController {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceChatController.class);
    private static boolean pushToTalkActive = false;

    private VoiceChatController() {
    }

    public static boolean handleKeyEvent(int key, int action) {
        VoiceSettingsManager.VoiceSettings settings = VoiceSettingsManager.get();
        if (settings.activationMode != VoiceSettingsManager.ActivationMode.PUSH_TO_TALK) {
            return false;
        }

        if (key != settings.pushToTalkKey) {
            return false;
        }

        if (action == GLFW.GLFW_PRESS) {
            enableLocalCapture();
        } else if (action == GLFW.GLFW_RELEASE) {
            disableLocalCapture();
        }
        return true;
    }

    public static void enableLocalCapture() {
        AudioService audio = audioService();
        if (audio == null || audio.getMicrophone().isActive()) {
            return;
        }

        try {
            audio.getMicrophone().start(Map.of("sessionId", "moud:ptt"));
            pushToTalkActive = true;
        } catch (Exception e) {
            LOGGER.warn("Failed to start push-to-talk capture", e);
        }
    }

    public static void disableLocalCapture() {
        AudioService audio = audioService();
        if (!pushToTalkActive || audio == null) {
            return;
        }

        audio.getMicrophone().stop();
        pushToTalkActive = false;
    }

    public static void refreshDevicePreference(String deviceName) {
        AudioService audio = audioService();
        if (audio != null) {
            audio.getMicrophone().setPreferredInputDevice(deviceName);
        }
    }

    private static AudioService audioService() {
        return ClientAPIService.INSTANCE != null ? ClientAPIService.INSTANCE.audio : null;
    }
}
