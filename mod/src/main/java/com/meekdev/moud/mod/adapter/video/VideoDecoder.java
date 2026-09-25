package com.meekdev.moud.mod.adapter.video;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.place.SyncedFiles.Stamp;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
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

    VideoDecoder(String name, Stamp stamp, double from, boolean sound) {
        String key = VideoFiles.key(name);
        thread = Thread.ofPlatform().daemon().name("moud video " + name).start(() -> decode(name, key, stamp, from, sound));
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
        if (finished) release();
    }

    private void decode(String name, String key, Stamp stamp, double from, boolean withSound) {
        try {
            Path file = VideoFiles.file(key, name, stamp);
            if (file == null) {
                MoudMod.LOG.warn("the video {} is not in the place", name);
                return;
            }
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
                while (!stopped && (frame = withSound ? grabber.grab() : grabber.grabImage()) != null) {
                    if (frame.image != null && frame.imageWidth > 0) {
                        hand(copy(frame, frame.timestamp / 1.0e6, 1.0 / rate));
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
            if (stopped) release();
        }
    }

    private void hand(Picture picture) throws InterruptedException {
        boolean taken = false;
        try {
            while (!stopped && !taken) taken = pictures.offer(picture, WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } finally {
            if (!taken) MemoryUtil.memFree(picture.rgba());
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

    private void release() {
        Picture picture;
        while ((picture = pictures.poll()) != null) MemoryUtil.memFree(picture.rgba());
        ByteBuffer buffer;
        while ((buffer = spare.poll()) != null) MemoryUtil.memFree(buffer);
    }
}
