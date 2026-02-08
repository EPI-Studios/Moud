package com.moud.client.audio.voice;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record VoiceProcessorRef(String id, @Nullable Map<String, Object> options) {
}

