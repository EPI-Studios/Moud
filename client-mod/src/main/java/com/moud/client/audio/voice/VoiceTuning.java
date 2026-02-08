package com.moud.client.audio.voice;

import com.moud.client.settings.VoiceSettingsManager;

public final class VoiceTuning {

    public static final float INPUT_GAIN = readFloatProperty("moud.voice.inputGain", 1.0f, 0.0f, 4.0f);
    public static final float OUTPUT_GAIN = readFloatProperty("moud.voice.outputGain", 0.8f, 0.0f, 4.0f);
    public static final float LIMITER_PEAK = readFloatProperty("moud.voice.limiterPeak", 0.98f, 0.1f, 1.0f);

    private VoiceTuning() {
    }

    public static float getInputGain() {
        return INPUT_GAIN * VoiceSettingsManager.get().getInputGainMultiplier();
    }

    public static float getOutputGain() {
        return OUTPUT_GAIN * VoiceSettingsManager.get().getOutputVolumeMultiplier();
    }

    private static float readFloatProperty(String key, float fallback, float min, float max) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            float value = Float.parseFloat(raw);
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
