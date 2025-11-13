package com.moud.client.display;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class DisplaySurface {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisplaySurface.class);

    private final long id;
    private final DisplayTextureResolver textureResolver = DisplayTextureResolver.getInstance();

    private Vector3 previousPosition = Vector3.zero();
    private Vector3 position = Vector3.zero();
    private Quaternion rotation = Quaternion.identity();
    private Vector3 scale = Vector3.one();
    private boolean initialized = false;

    private MoudPackets.DisplayAnchorType anchorType = MoudPackets.DisplayAnchorType.FREE;
    private BlockPos anchorBlockPos;
    private UUID anchorEntityUuid;
    private Vector3 anchorOffset = Vector3.zero();

    private MoudPackets.DisplayContentType contentType = MoudPackets.DisplayContentType.IMAGE;
    private String primarySource;
    private List<String> frameSources = List.of();
    private List<Identifier> sequenceTextures = List.of();
    private boolean sequenceDirty = false;
    private Identifier staticTexture;

    private boolean loop = false;
    private float frameRate = 0.0f;
    private boolean playing = false;
    private float playbackSpeed = 1.0f;
    private float playbackBaseSeconds = 0.0f;
    private long playbackBaseTimeMillis = Util.getMeasuringTimeMs();

    private BlockPos cachedBlockPos = BlockPos.ORIGIN;

    // Video playback fields
    private VideoDecoder videoDecoder;
    private Thread videoDecoderThread;
    private final ConcurrentLinkedQueue<NativeImage> frameQueue = new ConcurrentLinkedQueue<>();
    private NativeImageBackedTexture videoTexture;
    private Identifier videoTextureId;

    DisplaySurface(long id) {
        this.id = id;
    }

    long getId() {
        return id;
    }

    void applyCreatePacket(MoudPackets.S2C_CreateDisplayPacket packet) {
        updateTransform(packet.position(), packet.rotation(), packet.scale());
        updateAnchor(packet.anchorType(), packet.anchorBlockPosition(), packet.anchorEntityUuid(), packet.anchorOffset());
        updateContent(packet.contentType(), packet.primarySource(), packet.frameSources(), packet.frameRate(), packet.loop());
        updatePlayback(packet.playing(), packet.playbackSpeed(), packet.startOffsetSeconds());
    }

    void updateTransform(Vector3 pos, Quaternion rot, Vector3 scl) {
        if (pos != null) {
            if (!initialized) {
                position = new Vector3(pos);
                previousPosition = new Vector3(pos);
            } else {
                previousPosition = new Vector3(position);
                position = new Vector3(pos);
            }
            cachedBlockPos = BlockPos.ofFloored(position.x, position.y, position.z);
        }
        if (rot != null) {
            rotation = new Quaternion(rot);
        }
        if (scl != null) {
            scale = new Vector3(Math.max(scl.x, 0.001f), Math.max(scl.y, 0.001f), Math.max(scl.z, 0.001f));
        }
        if (!initialized && pos != null) {
            initialized = true;
        }
    }

    void updateAnchor(MoudPackets.DisplayAnchorType type, Vector3 blockPosition, UUID entityUuid, Vector3 offset) {
        anchorType = type != null ? type : MoudPackets.DisplayAnchorType.FREE;
        anchorOffset = offset != null ? new Vector3(offset) : Vector3.zero();
        if (anchorType == MoudPackets.DisplayAnchorType.BLOCK && blockPosition != null) {
            anchorBlockPos = BlockPos.ofFloored(blockPosition.x, blockPosition.y, blockPosition.z);
        } else {
            anchorBlockPos = null;
        }
        if (anchorType == MoudPackets.DisplayAnchorType.ENTITY) {
            anchorEntityUuid = entityUuid;
        } else {
            anchorEntityUuid = null;
        }
    }

    void updateContent(MoudPackets.DisplayContentType type, String source, List<String> frames, float fps, boolean loop) {
        stopVideoDecoder();
        contentType = type != null ? type : MoudPackets.DisplayContentType.IMAGE;
        primarySource = source;
        frameRate = fps;
        this.loop = loop;

        switch (contentType) {
            case IMAGE:
                staticTexture = textureResolver.normalize(source);
                break;
            case URL:
                if (source != null && (source.endsWith(".mp4") || source.endsWith(".mov"))) {
                    startVideoDecoder(source);
                } else {
                    staticTexture = textureResolver.normalize(source);
                }
                break;
            case FRAME_SEQUENCE:
                frameSources = frames != null ? List.copyOf(frames) : List.of();
                sequenceDirty = true;
                staticTexture = null;
                break;
            default:
                frameSources = List.of();
                sequenceTextures = List.of();
                staticTexture = null;
        }
    }

    void updatePlayback(boolean playing, float playbackSpeed, float baseSeconds) {
        this.playing = playing;
        this.playbackSpeed = playbackSpeed <= 0 ? 1.0f : playbackSpeed;
        this.playbackBaseSeconds = baseSeconds;
        this.playbackBaseTimeMillis = Util.getMeasuringTimeMs();

        if (videoDecoder != null) {
            if (this.playing) {
                videoDecoder.play();
                videoDecoder.seek(getPlaybackSeconds());
            } else {
                videoDecoder.pause();
            }
        }
    }

    public void tick() {
        if (contentType == MoudPackets.DisplayContentType.URL && !frameQueue.isEmpty()) {
            NativeImage frame = frameQueue.poll();
            if (frame != null) {
                uploadFrameToGpu(frame);
            }
        }
    }

    private void uploadFrameToGpu(NativeImage nativeImage) {
        MinecraftClient.getInstance().execute(() -> {
            if (videoTexture == null) {
                videoTexture = new NativeImageBackedTexture(nativeImage);
                videoTextureId = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("moud_video_" + id, videoTexture);
            } else {
                if (videoTexture.getImage() != null) {
                    if (videoTexture.getImage().getWidth() != nativeImage.getWidth() || videoTexture.getImage().getHeight() != nativeImage.getHeight()) {
                        videoTexture.getImage().close();
                        videoTexture.setImage(nativeImage);
                    } else {
                        videoTexture.getImage().copyFrom(nativeImage);
                        videoTexture.upload();
                        nativeImage.close();
                    }
                }
            }
        });
    }

    private void startVideoDecoder(String url) {
        stopVideoDecoder();
        videoDecoderThread = new Thread(videoDecoder, "Moud-VideoDecoder-" + id);
        videoDecoderThread.setDaemon(true);
        videoDecoderThread.start();
    }

    private void stopVideoDecoder() {
        if (videoDecoder != null) {
            videoDecoder.stop();
            if (videoDecoderThread != null) {
                videoDecoderThread.interrupt();
            }
        }
        videoDecoder = null;
        videoDecoderThread = null;

        while (!frameQueue.isEmpty()) {
            NativeImage img = frameQueue.poll();
            if (img != null) {
                img.close();
            }
        }

        if (videoTextureId != null) {
            MinecraftClient.getInstance().execute(() -> {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(videoTextureId);
                if (videoTexture != null) {
                    videoTexture.close();
                }
                videoTexture = null;
                videoTextureId = null;
            });
        }
    }

    public void enqueueFrame(BufferedImage frame) {
        if (frameQueue.size() > 5) {
            return;
        }
        try {
            NativeImage nativeImage = convertToNativeImage(frame);
            frameQueue.offer(nativeImage);
        } catch (Exception e) {
            LOGGER.error("Failed to convert BufferedImage to NativeImage for display {}", id, e);
        }
    }

    private NativeImage convertToNativeImage(BufferedImage image) throws IOException {
        NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                // Convert ARGB (Java) to ABGR (Minecraft)
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                nativeImage.setColor(x, y, (alpha << 24) | (blue << 16) | (green << 8) | red);
            }
        }
        return nativeImage;
    }

    public Vector3 getInterpolatedPosition(float tickDelta) {
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        float x = previousPosition.x + (position.x - previousPosition.x) * t;
        float y = previousPosition.y + (position.y - previousPosition.y) * t;
        float z = previousPosition.z + (position.z - previousPosition.z) * t;
        return new Vector3(x, y, z);
    }

    Quaternion getRotation() {
        return rotation;
    }

    Vector3 getScale() {
        return scale;
    }

    public BlockPos getBlockPos() {
        return cachedBlockPos;
    }

    Identifier resolveTexture(float tickDelta) {
        return switch (contentType) {
            case IMAGE -> staticTexture;
            case URL -> videoTextureId != null ? videoTextureId : staticTexture;
            case FRAME_SEQUENCE -> resolveSequenceTexture();
        };
    }

    void dispose() {
        stopVideoDecoder();
    }

    private Identifier resolveSequenceTexture() {
        if (sequenceDirty) {
            sequenceTextures = loadSequenceTextures();
            sequenceDirty = false;
        }
        if (sequenceTextures.isEmpty()) {
            return null;
        }

        double timeSeconds = getPlaybackSeconds();
        double framePosition = timeSeconds * Math.max(frameRate, 0.01f);
        int frameIndex;
        if (loop) {
            frameIndex = Math.floorMod((int) framePosition, sequenceTextures.size());
        } else {
            frameIndex = (int) Math.min(sequenceTextures.size() - 1, Math.max(0, Math.floor(framePosition)));
        }
        return sequenceTextures.get(frameIndex);
    }

    private List<Identifier> loadSequenceTextures() {
        if (frameSources.isEmpty()) {
            return List.of();
        }
        List<Identifier> textures = new ArrayList<>(frameSources.size());
        for (String source : frameSources) {
            Identifier identifier = textureResolver.normalize(source);
            if (identifier != null) {
                textures.add(identifier);
            }
        }
        return textures;
    }

    private double getPlaybackSeconds() {
        double base = playbackBaseSeconds;
        if (playing) {
            long elapsedMillis = Util.getMeasuringTimeMs() - playbackBaseTimeMillis;
            base += (elapsedMillis / 1000.0) * playbackSpeed;
        }
        return base;
    }

    public boolean shouldLoop() {
        return this.loop;
    }
}