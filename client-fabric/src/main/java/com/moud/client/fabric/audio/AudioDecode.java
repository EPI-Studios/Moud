package com.moud.client.fabric.audio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.StaticSound;

final class AudioDecode {
    private AudioDecode() {
    }

    static StaticSound decodeStaticSound(String path, byte[] bytes) throws IOException {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ogg")) {
            try (OggAudioStream ogg = new OggAudioStream(new ByteArrayInputStream(bytes))) {
                return new StaticSound(ogg.readAll(), ogg.getFormat());
            }
        }

        try (AudioInputStream input = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
            AudioFormat source = input.getFormat();
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(),
                    16,
                    source.getChannels(),
                    source.getChannels() * 2,
                    source.getSampleRate(),
                    false
            );
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, input)) {
                byte[] pcmBytes = pcm.readAllBytes();
                ByteBuffer buffer = ByteBuffer.allocateDirect(pcmBytes.length);
                buffer.put(pcmBytes);
                buffer.flip();
                return new StaticSound(buffer, target);
            }
        } catch (Exception e) {
            throw new IOException("unsupported audio format", e);
        }
    }
}
