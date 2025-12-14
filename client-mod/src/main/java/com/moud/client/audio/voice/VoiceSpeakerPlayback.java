package com.moud.client.audio.voice;

import com.moud.api.math.Vector3;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

public final class VoiceSpeakerPlayback {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceSpeakerPlayback.class);
    private static final byte[] STOP = new byte[0];

    private final UUID speakerId;
    private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(32);
    private final Thread thread;
    private final SourceDataLine line;
    private final FloatControl panControl;
    private final FloatControl gainControl;
    private volatile boolean running = true;
    private volatile long lastReceivedAtMs;
    private volatile Vector3 lastPosition;

    private VoiceSpeakerPlayback(UUID speakerId,
                                 SourceDataLine line,
                                 @Nullable FloatControl panControl,
                                 @Nullable FloatControl gainControl) {
        this.speakerId = speakerId;
        this.line = line;
        this.panControl = panControl;
        this.gainControl = gainControl;
        this.thread = new Thread(this::loop, "Moud-VoicePlayback-" + speakerId);
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public static @Nullable VoiceSpeakerPlayback open(UUID speakerId, int sampleRate, int channels) {
        AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            LOGGER.warn("Voice playback format not supported: {}", format);
            return null;
        }

        try {
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            FloatControl pan = null;
            FloatControl gain = null;
            if (line.isControlSupported(FloatControl.Type.PAN)) {
                pan = (FloatControl) line.getControl(FloatControl.Type.PAN);
            } else if (line.isControlSupported(FloatControl.Type.BALANCE)) {
                pan = (FloatControl) line.getControl(FloatControl.Type.BALANCE);
            }
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            }

            return new VoiceSpeakerPlayback(speakerId, line, pan, gain);
        } catch (LineUnavailableException e) {
            LOGGER.warn("Failed to open voice playback line", e);
            return null;
        }
    }

    public void markReceived(@Nullable Vector3 position) {
        lastReceivedAtMs = System.currentTimeMillis();
        lastPosition = position != null ? new Vector3(position) : null;
    }

    public long getLastReceivedAtMs() {
        return lastReceivedAtMs;
    }

    public void enqueue(byte[] pcm) {
        if (!running) {
            return;
        }
        if (!queue.offer(pcm)) {
            queue.poll();
            queue.offer(pcm);
        }
    }

    public void applySpatialization() {
        Vector3 position = lastPosition;
        if (position == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        double dx = position.x - client.player.getX();
        double dz = position.z - client.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        double pan = distance < 0.0001 ? 0.0 : Math.max(-1.0, Math.min(1.0, dx / Math.max(1.0, distance)));
        if (panControl != null) {
            panControl.setValue((float) pan);
        }

        if (gainControl != null) {
            float clamped = (float) Math.max(0.0, Math.min(1.0, 1.0 / (1.0 + (distance / 12.0))));
            float db = (float) (20.0 * Math.log10(Math.max(1e-3, clamped)));
            gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), db)));
        }
    }

    public void close() {
        if (!running) {
            return;
        }
        running = false;
        queue.clear();
        queue.offer(STOP);
    }

    private void loop() {
        try {
            while (running) {
                byte[] next = queue.take();
                if (next == STOP || next.length == 0) {
                    break;
                }
                line.write(next, 0, next.length);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.warn("Voice playback stream errored for {}", speakerId, e);
        } finally {
            try {
                line.stop();
            } catch (Exception ignored) {
            }
            try {
                line.close();
            } catch (Exception ignored) {
            }
        }
    }
}

