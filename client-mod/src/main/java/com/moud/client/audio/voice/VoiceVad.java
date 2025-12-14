package com.moud.client.audio.voice;

import com.moud.client.settings.VoiceSettingsManager;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class VoiceVad {

    private volatile Config config = Config.fromSettings();
    private volatile long lastActivityAtMs;

    public void configure(@Nullable Map<String, Object> options) {
        config = options != null ? Config.fromMap(options) : Config.fromSettings();
    }

    public void reset() {
        lastActivityAtMs = 0L;
    }

    public boolean dropSilence() {
        return config.dropSilence;
    }

    public boolean isSpeaking(float level, long timestampMs) {
        VoiceSettingsManager.VoiceSettings settings = VoiceSettingsManager.get();
        if (settings.activationMode == VoiceSettingsManager.ActivationMode.PUSH_TO_TALK) {
            lastActivityAtMs = timestampMs;
            return true;
        }

        Config cfg = config;
        double levelDb = 20.0 * Math.log10(Math.max(1e-9, level));
        double thresholdDb = -Math.abs(cfg.thresholdDb);
        if (levelDb > thresholdDb) {
            lastActivityAtMs = timestampMs;
            return true;
        }
        return (timestampMs - lastActivityAtMs) <= cfg.hangoverMs;
    }

    public record Config(double thresholdDb, long hangoverMs, boolean dropSilence) {

        public static Config fromSettings() {
            VoiceSettingsManager.VoiceSettings settings = VoiceSettingsManager.get();
            return new Config(settings.activityThreshold, 200L, settings.autoMuteWhenIdle);
        }

        public static Config fromMap(Map<String, Object> options) {
            double thresholdDb = options.get("thresholdDb") instanceof Number number
                    ? number.doubleValue()
                    : VoiceSettingsManager.get().activityThreshold;
            long hangoverMs = options.get("hangoverMs") instanceof Number number ? number.longValue() : 200L;
            boolean dropSilence = options.get("dropSilence") instanceof Boolean bool
                    ? bool
                    : VoiceSettingsManager.get().autoMuteWhenIdle;
            return new Config(thresholdDb, hangoverMs, dropSilence);
        }
    }
}

