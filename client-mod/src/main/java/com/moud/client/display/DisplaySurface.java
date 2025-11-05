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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DisplaySurface {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisplaySurface.class);
    private static final int STREAM_BUFFER_SIZE = 5;

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
    private CompletableFuture<Identifier> pendingRemoteTexture;
    private String remoteSource;
    private List<String> remoteSequenceSources = List.of();

    private boolean loop = false;
    private float frameRate = 0.0f;

    private boolean playing = false;
    private float playbackSpeed = 1.0f;
    private float playbackBaseSeconds = 0.0f;
    private long playbackBaseTimeMillis = Util.getMeasuringTimeMs();

    private final Deque<StreamFrame> streamFrames = new ArrayDeque<>();
    private StreamFrame currentStreamFrame;

    private BlockPos cachedBlockPos = BlockPos.ORIGIN;

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
        releaseRemoteIfNeeded();
        releaseRemoteSequenceSources();
        contentType = type != null ? type : MoudPackets.DisplayContentType.IMAGE;
        primarySource = source;
        frameRate = fps;
        this.loop = loop;

        if (contentType == MoudPackets.DisplayContentType.FRAME_SEQUENCE) {
            frameSources = frames != null ? List.copyOf(frames) : List.of();
            sequenceDirty = true;
            staticTexture = null;
        } else {
            frameSources = List.of();
            sequenceTextures = List.of();
            if (requiresRemote(source)) {
                remoteSource = source;
                pendingRemoteTexture = textureResolver.acquireRemote(source);
                pendingRemoteTexture.thenAccept(identifier -> staticTexture = identifier);
            } else {
                remoteSource = null;
                staticTexture = textureResolver.normalize(source);
                pendingRemoteTexture = null;
            }
        }
        clearStreamFrames();
    }

    void updatePlayback(boolean playing, float playbackSpeed, float baseSeconds) {
        this.playing = playing;
        this.playbackSpeed = playbackSpeed <= 0 ? 1.0f : playbackSpeed;
        this.playbackBaseSeconds = baseSeconds;
        this.playbackBaseTimeMillis = Util.getMeasuringTimeMs();
    }

    void pushStreamFrame(MoudPackets.S2C_DisplayStreamFramePacket packet) {
        CompletableFuture.runAsync(() -> {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(packet.imageData()));
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> registerStreamFrame(image, packet.frameIndex(), packet.presentationTimestampMillis()));
            } catch (IOException e) {
                LOGGER.error("Failed to parse stream frame for display {}", id, e);
            }
        });
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
            case IMAGE, URL -> resolveStaticTexture();
            case FRAME_SEQUENCE -> resolveSequenceTexture();
            case STREAM -> resolveStreamTexture();
        };
    }

    void dispose() {
        releaseRemoteIfNeeded();
        releaseRemoteSequenceSources();
        clearStreamFrames();
    }

    private Identifier resolveStaticTexture() {
        if (staticTexture != null) {
            return staticTexture;
        }
        if (pendingRemoteTexture != null && pendingRemoteTexture.isDone()) {
            try {
                staticTexture = pendingRemoteTexture.get();
            } catch (Exception e) {
                LOGGER.error("Failed to obtain remote texture for display {}", id, e);
                pendingRemoteTexture = null;
            }
        }
        return staticTexture;
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

    private Identifier resolveStreamTexture() {
        StreamFrame current = currentStreamFrame;
        return current != null ? current.textureId : null;
    }

    private List<Identifier> loadSequenceTextures() {
        if (frameSources.isEmpty()) {
            return List.of();
        }
        releaseRemoteSequenceSources();
        List<Identifier> textures = new ArrayList<>(frameSources.size());
        List<String> remoteSources = new ArrayList<>();
        for (String source : frameSources) {
            Identifier identifier;
            if (requiresRemote(source)) {
                CompletableFuture<Identifier> future = textureResolver.acquireRemote(source);
                try {
                    identifier = future.get();
                    remoteSources.add(source);
                } catch (Exception e) {
                    LOGGER.error("Failed to load remote frame {} for display {}", source, id, e);
                    continue;
                }
            } else {
                identifier = textureResolver.normalize(source);
            }
            if (identifier != null) {
                textures.add(identifier);
            }
        }
        remoteSequenceSources = remoteSources.isEmpty() ? List.of() : List.copyOf(remoteSources);
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

    private boolean requiresRemote(String source) {
        if (source == null) {
            return false;
        }
        return source.startsWith("http://") || source.startsWith("https://") || source.startsWith("data:");
    }

    private void releaseRemoteIfNeeded() {
        if (remoteSource != null) {
            textureResolver.releaseRemote(remoteSource);
            remoteSource = null;
            pendingRemoteTexture = null;
        }
    }

    private void releaseRemoteSequenceSources() {
        if (remoteSequenceSources.isEmpty()) {
            return;
        }
        for (String source : remoteSequenceSources) {
            textureResolver.releaseRemote(source);
        }
        remoteSequenceSources = List.of();
    }

    private void registerStreamFrame(NativeImage image, long frameIndex, long timestamp) {
        MinecraftClient client = MinecraftClient.getInstance();
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        Identifier identifier = Identifier.of("moud", "display/stream_" + id + "_" + frameIndex);
        client.getTextureManager().registerTexture(identifier, texture);

        StreamFrame frame = new StreamFrame(identifier, texture, timestamp);
        streamFrames.addLast(frame);
        currentStreamFrame = frame;

        while (streamFrames.size() > STREAM_BUFFER_SIZE) {
            StreamFrame oldest = streamFrames.removeFirst();
            oldest.dispose();
        }
    }

    private void clearStreamFrames() {
        StreamFrame frame;
        while ((frame = streamFrames.poll()) != null) {
            frame.dispose();
        }
        currentStreamFrame = null;
    }

    private static final class StreamFrame {
        private final Identifier textureId;
        private final NativeImageBackedTexture texture;
        private final long timestampMillis;

        private StreamFrame(Identifier textureId, NativeImageBackedTexture texture, long timestampMillis) {
            this.textureId = textureId;
            this.texture = texture;
            this.timestampMillis = timestampMillis;
        }

        private void dispose() {
            MinecraftClient client = MinecraftClient.getInstance();
            client.getTextureManager().destroyTexture(textureId);
            texture.close();
        }
    }
}
