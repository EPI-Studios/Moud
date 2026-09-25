package com.meekdev.moud.mod.adapter.video;

import com.meekdev.resona.engine.StreamDecoder;
import java.nio.ShortBuffer;
import org.lwjgl.system.MemoryUtil;

final class VideoSound implements StreamDecoder {

    private static final int SILENCE_FRAMES = 512;

    private final VideoDecoder decoder;
    private final int channels;
    private final int sampleRate;
    private ShortBuffer buffer;

    VideoSound(VideoDecoder decoder, int channels, int sampleRate) {
        this.decoder = decoder;
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.buffer = MemoryUtil.memAllocShort(SILENCE_FRAMES * channels);
    }

    @Override
    public int channels() {
        return channels;
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public ShortBuffer readChunk() {
        short[] chunk = decoder.sound();
        if (chunk == null) {
            if (decoder.finished()) return null;
            chunk = new short[SILENCE_FRAMES * channels];
        }
        if (buffer.capacity() < chunk.length) {
            MemoryUtil.memFree(buffer);
            buffer = MemoryUtil.memAllocShort(chunk.length);
        }
        buffer.clear();
        buffer.put(chunk).flip();
        return buffer;
    }

    @Override
    public void rewind() {
    }

    @Override
    public void close() {
        MemoryUtil.memFree(buffer);
    }
}
