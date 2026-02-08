package com.moud.client.audio.voice;

public final class VoiceAudioUtil {

    private VoiceAudioUtil() {
    }

    public static float computeRmsLevel(short[] samples) {
        if (samples.length == 0) {
            return 0.0f;
        }
        double sumSq = 0.0;
        for (short sample : samples) {
            double normalized = sample / 32768.0;
            sumSq += normalized * normalized;
        }
        double rms = Math.sqrt(sumSq / samples.length);
        return (float) Math.max(0.0, Math.min(1.0, rms));
    }

    public static short[] decodePcmS16Le(byte[] pcm) {
        int samples = pcm.length / 2;
        short[] out = new short[samples];
        int idx = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int lo = pcm[i] & 0xFF;
            int hi = (pcm[i + 1] & 0xFF) << 8;
            out[idx++] = (short) (hi | lo);
        }
        return out;
    }

    public static byte[] encodePcmS16Le(short[] samples) {
        byte[] out = new byte[samples.length * 2];
        int idx = 0;
        for (short sample : samples) {
            out[idx++] = (byte) (sample & 0xFF);
            out[idx++] = (byte) ((sample >> 8) & 0xFF);
        }
        return out;
    }

    public static void applyGainWithLimiter(short[] samples, float gain, float maxPeak) {
        if (samples.length == 0) {
            return;
        }

        float clampedGain = Math.max(0.0f, Math.min(gain, 4.0f));
        if (clampedGain <= 0.0f) {
            for (int i = 0; i < samples.length; i++) {
                samples[i] = 0;
            }
            return;
        }

        float clampedPeak = Math.max(0.1f, Math.min(maxPeak, 1.0f));
        float peak = 0.0f;
        for (short sample : samples) {
            float abs = Math.abs(sample / 32768.0f);
            if (abs > peak) {
                peak = abs;
            }
        }
        if (peak <= 0.0f) {
            return;
        }

        float effectiveGain = clampedGain;
        if (peak * clampedGain > clampedPeak) {
            effectiveGain = clampedPeak / peak;
        }
        if (effectiveGain == 1.0f) {
            return;
        }

        for (int i = 0; i < samples.length; i++) {
            int mixed = Math.round(samples[i] * effectiveGain);
            samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
        }
    }
}

