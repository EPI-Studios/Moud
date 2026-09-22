package com.meekdev.moud.mod.adapter.video;

import com.meekdev.moud.mod.MoudMod;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

final class VideoDecoder {

    record Picture(double at, double duration, int width, int height, ByteBuffer rgba) {}

    record Info(int width, int height, double length, int channels, int sampleRate) {}

    private static final int PICTURES_AHEAD = 8;
    private static final int SOUND_AHEAD = 64;
    private static final long WAIT_MILLIS = 50;

    private final BlockingQueue<Picture> pictures = new ArrayBlockingQueue<>(PICTURES_AHEAD);
    private final BlockingQueue<ByteBuffer> spare = new ArrayBlockingQueue<>(PICTURES_AHEAD + 2);
    private final BlockingQueue<short[]> sound = new ArrayBlockingQueue<>(SOUND_AHEAD);
    private final Thread thread;
    private volatile boolean stopped;
    private volatile boolean finished;
    private volatile @Nullable Info info;

    VideoDecoder(String name, byte[] bytes, double from) {
        thread = Thread.ofPlatform().daemon().name("moud video " + name).start(() -> decode(name, bytes, from));
    }

    @Nullable Info info() {
        return info;
    }

    @Nullable Picture peek() {
        return pictures.peek();
    }

    @Nullable Picture poll() {
        return pictures.poll();
    }

    short @Nullable [] sound() {
        return sound.poll();
    }

    void recycle(Picture picture) {
        if (stopped || !spare.offer(picture.rgba())) MemoryUtil.memFree(picture.rgba());
    }

    boolean drained() {
        return finished && pictures.isEmpty();
    }

    boolean finished() {
        return finished;
    }

    void stop() {
        stopped = true;
        thread.interrupt();
    }

    private void decode(String name, byte[] bytes, double from) {
        Path file = null;
        try {
            file = Files.createTempFile("moud-video-", extension(name));
            Files.write(file, bytes);
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file.toFile())) {
                grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
                grabber.setSampleFormat(avutil.AV_SAMPLE_FMT_S16);
                grabber.setVideoOption("threads", "0");
                grabber.start();
                double rate = grabber.getFrameRate() > 0 ? grabber.getFrameRate() : 30;
                info = new Info(grabber.getImageWidth(), grabber.getImageHeight(), grabber.getLengthInTime() / 1.0e6,
                        grabber.getAudioChannels(), grabber.getSampleRate());
                if (from > 0) grabber.setTimestamp(Math.round(from * 1.0e6));
                Frame frame;
                while (!stopped && (frame = grabber.grab()) != null) {
                    if (frame.image != null && frame.imageWidth > 0) {
                        Picture picture = copy(frame, frame.timestamp / 1.0e6, 1.0 / rate);
                        while (!stopped && !pictures.offer(picture, WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                            // the sound keeps playing ahead of the pictures, so it is never left waiting behind a full picture queue
                        }
                    } else if (frame.samples != null && frame.samples[0] instanceof ShortBuffer samples) {
                        short[] chunk = new short[samples.remaining()];
                        samples.get(chunk);
                        while (!stopped && !sound.offer(chunk, WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                        }
                    }
                }
                grabber.stop();
            }
        } catch (InterruptedException ignored) {
        } catch (IOException | RuntimeException | LinkageError problem) {
            MoudMod.LOG.warn("could not decode the video {}: {}", name, problem.getMessage());
        } finally {
            finished = true;
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                }
            }
            if (stopped) release();
        }
    }

    private Picture copy(Frame frame, double at, double duration) {
        int width = frame.imageWidth;
        int height = frame.imageHeight;
        int row = width * 4;
        ByteBuffer source = (ByteBuffer) frame.image[0];
        int stride = frame.imageStride;
        ByteBuffer out = spare.poll();
        if (out == null || out.capacity() < row * height) {
            if (out != null) MemoryUtil.memFree(out);
            out = MemoryUtil.memAlloc(row * height);
        }
        out.clear();
        for (int y = 0; y < height; y++) {
            ByteBuffer line = source.duplicate();
            line.position(y * stride).limit(y * stride + row);
            out.put(line);
        }
        out.flip();
        return new Picture(at, duration, width, height, out);
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? ".video" : name.substring(dot);
    }

    private void release() {
        Picture picture;
        while ((picture = pictures.poll()) != null) MemoryUtil.memFree(picture.rgba());
        ByteBuffer buffer;
        while ((buffer = spare.poll()) != null) MemoryUtil.memFree(buffer);
    }
}
