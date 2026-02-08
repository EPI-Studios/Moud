package com.moud.client.display;

import org.jcodec.api.JCodecException;
import org.jcodec.api.awt.AWTFrameGrab;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Rational;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class VideoDecoder implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoDecoder.class);
    private final String videoUrl;
    private final DisplaySurface targetSurface;
    private final HttpClient httpClient;
    private volatile boolean running = true;
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicReference<Double> seekRequest = new AtomicReference<>(null);

    public VideoDecoder(String videoUrl, DisplaySurface targetSurface) {
        this.videoUrl = videoUrl;
        this.targetSurface = targetSurface;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void run() {
        Path tempFile = null;
        try {
            tempFile = downloadVideo();
            if (tempFile == null) {
                LOGGER.error("Failed to download video from {}", videoUrl);
                return;
            }
            decodeFrames(tempFile);
        } catch (Exception e) {
            if (running) {
                LOGGER.error("Error during video decoding for {}", videoUrl, e);
            }
        } finally {
            if (tempFile != null) {
                try {
                    Files.delete(tempFile);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete temporary video file: {}", tempFile, e);
                }
            }
            LOGGER.info("VideoDecoder thread for {} has finished.", videoUrl);
        }
    }

    public void play() {
        playing.set(true);
    }

    public void pause() {
        playing.set(false);
    }

    public void seek(double timeSeconds) {
        seekRequest.set(timeSeconds);
    }

    public void stop() {
        running = false;
    }

    private Path downloadVideo() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(videoUrl))
                .GET()
                .build();

        Path tempFile = Files.createTempFile("moud-video-", ".mp4");
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return tempFile;
        } else {
            Files.delete(tempFile);
            throw new IOException("Failed to download video, status code: " + response.statusCode());
        }
    }

    private void decodeFrames(Path videoFile) throws IOException, JCodecException {
        File file = videoFile.toFile();
        AWTFrameGrab grab = AWTFrameGrab.createAWTFrameGrab(NIOUtils.readableChannel(file));
        DemuxerTrackMeta meta = grab.getVideoTrack().getMeta();

        double frameRate;
        if (meta.getTotalDuration() > 0 && meta.getTotalFrames() > 0) {
            frameRate = meta.getTotalFrames() / meta.getTotalDuration();
        } else {
            LOGGER.warn("Could not determine video framerate from metadata for {}. Defaulting to 30 fps.", videoUrl);
            frameRate = 30.0;
        }

        long frameIntervalMillis = (long) (1000.0 / frameRate);

        while (running) {
            try {
                Double seekTime = seekRequest.getAndSet(null);
                if (seekTime != null) {
                    grab.seekToSecondPrecise(seekTime);
                }

                if (!playing.get()) {
                    Thread.sleep(50);
                    continue;
                }

                long frameStartTime = System.currentTimeMillis();

                BufferedImage frame = grab.getFrame();
                if (frame == null) {
                    if (targetSurface.shouldLoop()) {
                        LOGGER.info("Video reached end, looping...");
                        grab.seekToSecondPrecise(0);
                        continue;
                    } else {
                        LOGGER.info("Video reached end, stopping.");
                        playing.set(false);
                        continue;
                    }
                }

                targetSurface.enqueueFrame(frame);

                long processingTime = System.currentTimeMillis() - frameStartTime;
                long sleepTime = frameIntervalMillis - processingTime;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }

            } catch (InterruptedException e) {
                running = false;
            } catch (Exception e) {
                if (running) {
                    LOGGER.error("An error occurred during frame decoding loop", e);
                    playing.set(false);
                }
            }
        }
    }
}