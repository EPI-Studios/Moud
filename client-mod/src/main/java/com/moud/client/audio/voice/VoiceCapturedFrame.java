package com.moud.client.audio.voice;

public record VoiceCapturedFrame(String sessionId,
                                 long timestampMs,
                                 int sampleRate,
                                 int channels,
                                 int frameSizeMs,
                                 String codec,
                                 byte[] data) {

    public VoiceCapturedFrame(String sessionId,
                              long timestampMs,
                              int sampleRate,
                              int channels,
                              int frameSizeMs,
                              byte[] data) {
        this(sessionId, timestampMs, sampleRate, channels, frameSizeMs, VoiceCodecs.PCM_S16LE, data);
    }
}

