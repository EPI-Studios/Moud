package com.moud.client.audio.voice;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public record VoiceIncomingFrame(UUID speakerId,
                                 String sessionId,
                                 int sequence,
                                 long timestampMs,
                                 String codec,
                                 int sampleRate,
                                 int channels,
                                 int frameSizeMs,
                                 float level,
                                 boolean speaking,
                                 byte[] data,
                                 Map<String, Object> outputProcessing,
                                 @Nullable Vector3 position) {

    public static VoiceIncomingFrame fromPacket(MoudPackets.VoiceStreamChunkPacket packet) {
        Map<String, Object> processing = packet.outputProcessing() != null ? packet.outputProcessing() : Map.of();
        return new VoiceIncomingFrame(
                packet.speakerId(),
                packet.sessionId(),
                packet.sequence(),
                packet.timestampMs(),
                packet.codec(),
                packet.sampleRate(),
                packet.channels(),
                packet.frameSizeMs(),
                packet.level(),
                packet.speaking(),
                packet.data(),
                processing,
                packet.position()
        );
    }
}

