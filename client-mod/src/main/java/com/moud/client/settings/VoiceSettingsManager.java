package com.moud.client.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VoiceSettingsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceSettingsManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("moud-voice-settings.json");

    private static VoiceSettings settings;

    private VoiceSettingsManager() {
    }

    public static synchronized VoiceSettings get() {
        if (settings == null) {
            settings = load();
        }
        return settings;
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            MAPPER.writeValue(CONFIG_PATH.toFile(), get());
        } catch (IOException e) {
            LOGGER.warn("Failed to persist voice settings", e);
        }
    }

    private static VoiceSettings load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                return MAPPER.readValue(CONFIG_PATH.toFile(), VoiceSettings.class);
            } catch (IOException e) {
                LOGGER.warn("Failed to read voice settings, falling back to defaults", e);
            }
        }
        return new VoiceSettings();
    }

    public enum ActivationMode {
        PUSH_TO_TALK,
        VOICE_ACTIVITY
    }

    public static final class VoiceSettings {
        public String inputDeviceName = "";
        public ActivationMode activationMode = ActivationMode.PUSH_TO_TALK;
        public int pushToTalkKey = GLFW.GLFW_KEY_V;
        public boolean microphoneMuted = false;
        public boolean deafened = false;
        public boolean autoMuteWhenIdle = true;
        public boolean automaticVoiceDetection = false;
        public int activityThreshold = 35;
        public int inputGain = 100;
        public int outputVolume = 100;

        public VoiceSettings copy() {

            VoiceSettings copy = new VoiceSettings();
            copy.inputDeviceName = this.inputDeviceName;
            copy.activationMode = this.activationMode;
            copy.pushToTalkKey = this.pushToTalkKey;
            copy.microphoneMuted = this.microphoneMuted;
            copy.deafened = this.deafened;
            copy.autoMuteWhenIdle = this.autoMuteWhenIdle;
            copy.automaticVoiceDetection = this.automaticVoiceDetection;
            copy.activityThreshold = this.activityThreshold;
            copy.inputGain = this.inputGain;
            copy.outputVolume = this.outputVolume;
            return copy;
        }

        public float getInputGainMultiplier() {
            return inputGain / 100.0f;
        }

        public float getOutputVolumeMultiplier() {
            return outputVolume / 100.0f;
        }
    }
}
