package com.moud.client.display;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.model.ClientModelManager;
import com.moud.client.model.RenderableModel;
import com.moud.client.util.ClientEntityResolver;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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
    private Vector3 renderPosition = Vector3.zero();
    private Vector3 smoothingStartPosition = Vector3.zero();
    private Quaternion rotation = Quaternion.identity();
    private Quaternion renderRotation = Quaternion.identity();
    private Quaternion smoothingStartRotation = Quaternion.identity();
    private Vector3 scale = Vector3.one();
    private Vector3 renderScale = Vector3.one();
    private Vector3 smoothingStartScale = Vector3.one();
    private float opacity = 1.0f;
    private boolean initialized = false;
    private boolean smoothingEnabled = true;
    private float smoothingDurationTicks = 3.0f;
    private float smoothingProgress = 1.0f;

    private MoudPackets.DisplayAnchorType anchorType = MoudPackets.DisplayAnchorType.FREE;
    private BlockPos anchorBlockPos;
    private UUID anchorEntityUuid;
    private Long anchorModelId;
    private Vector3 anchorOffset = Vector3.zero();
    private boolean anchorOffsetLocal = false;
    private boolean inheritRotation = false;
    private boolean inheritScale = false;
    private boolean includePitch = false;

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
    private MoudPackets.DisplayBillboardMode billboardMode = MoudPackets.DisplayBillboardMode.NONE;
    private boolean renderThroughBlocks = false;

    // Video playback fields
    private VideoDecoder videoDecoder;
    private Thread videoDecoderThread;
    private final ConcurrentLinkedQueue<NativeImage> frameQueue = new ConcurrentLinkedQueue<>();
    private NativeImageBackedTexture videoTexture;
    private Identifier videoTextureId;

    DisplaySurface(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public Vector3 getPosition() {
        return getInterpolatedPosition(getCurrentTickDelta());
    }

    public Quaternion getRotation() {
        return getInterpolatedRotation(getCurrentTickDelta());
    }

    public Vector3 getScale() {
        return getInterpolatedScale(getCurrentTickDelta());
    }

    public MoudPackets.DisplayContentType getContentType() {
        return contentType;
    }

    public String getPrimarySource() {
        return primarySource;
    }

    public boolean isLooping() {
        return loop;
    }

    public float getFrameRate() {
        return frameRate;
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = opacity;
    }

    public MoudPackets.DisplayBillboardMode getBillboardMode() {
        return billboardMode;
    }

    public boolean isRenderThroughBlocks() {
        return renderThroughBlocks;
    }

    void applyCreatePacket(MoudPackets.S2C_CreateDisplayPacket packet) {
        updateTransform(packet.position(), packet.rotation(), packet.scale(), packet.billboardMode(), packet.renderThroughBlocks());
        updateAnchor(packet.anchorType(),
                packet.anchorBlockPosition(),
                packet.anchorEntityUuid(),
                packet.anchorOffset(),
                null,
                false,
                false,
                false,
                false);
        updateContent(packet.contentType(), packet.primarySource(), packet.frameSources(), packet.frameRate(), packet.loop());
        updatePlayback(packet.playing(), packet.playbackSpeed(), packet.startOffsetSeconds());
    }

    public void updateTransform(Vector3 pos, Quaternion rot, Vector3 scl, MoudPackets.DisplayBillboardMode mode, boolean renderOnTop) {
        if (pos != null) {
            if (!initialized) {
                position = new Vector3(pos);
                previousPosition = new Vector3(pos);
                renderPosition = new Vector3(pos);
                smoothingStartPosition = new Vector3(pos);
            } else {
                previousPosition = new Vector3(position);
                position = new Vector3(pos);
                smoothingStartPosition = new Vector3(renderPosition);
                smoothingProgress = 0.0f;
            }
            cachedBlockPos = BlockPos.ofFloored(position.x, position.y, position.z);
        }
        if (rot != null) {
            Quaternion newRot = new Quaternion(rot);
            if (!initialized) {
                rotation = newRot;
                renderRotation = new Quaternion(newRot);
                smoothingStartRotation = new Quaternion(newRot);
            } else {
                smoothingStartRotation = new Quaternion(renderRotation);
                rotation = newRot;
                smoothingProgress = 0.0f;
            }
        }
        if (scl != null) {
            Vector3 safeScale = new Vector3(Math.max(scl.x, 0.001f), Math.max(scl.y, 0.001f), Math.max(scl.z, 0.001f));
            if (!initialized) {
                scale = safeScale;
                renderScale = new Vector3(safeScale);
                smoothingStartScale = new Vector3(safeScale);
            } else {
                smoothingStartScale = new Vector3(renderScale);
                scale = safeScale;
                smoothingProgress = 0.0f;
            }
        }
        if (mode != null) {
            billboardMode = mode;
        }
        renderThroughBlocks = renderOnTop;
        if (!initialized && pos != null) {
            initialized = true;
        }
    }

    void updateAnchor(MoudPackets.DisplayAnchorType type,
                      Vector3 blockPosition,
                      UUID entityUuid,
                      Vector3 offset,
                      Long modelId,
                      boolean anchorOffsetLocal,
                      boolean inheritRotation,
                      boolean inheritScale,
                      boolean includePitch) {
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
        anchorModelId = anchorType == MoudPackets.DisplayAnchorType.MODEL ? modelId : null;
        this.anchorOffsetLocal = anchorOffsetLocal;
        this.inheritRotation = inheritRotation;
        this.inheritScale = inheritScale;
        this.includePitch = includePitch;
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
        tickSmoothing(1.0f);
        if (contentType == MoudPackets.DisplayContentType.URL && !frameQueue.isEmpty()) {
            NativeImage frame = frameQueue.poll();
            if (frame != null) {
                uploadFrameToGpu(frame);
            }
        }
    }

    public void tickSmoothing(float deltaTicks) {
        if (!smoothingEnabled || !initialized) {
            return;
        }
        if (deltaTicks <= 0.0f) {
            deltaTicks = 1.0f;
        }

        this.previousPosition = new Vector3(renderPosition);

        float duration = Math.max(1.0f, smoothingDurationTicks);
        smoothingProgress = Math.min(1.0f, smoothingProgress + (deltaTicks / duration));
        float t = Math.max(0.0f, Math.min(1.0f, smoothingProgress));

        renderPosition = Vector3.lerp(smoothingStartPosition, position, t);
        renderRotation = smoothingStartRotation.slerp(rotation, t);
        renderScale = Vector3.lerp(smoothingStartScale, scale, t);

        if (smoothingProgress >= 1.0f) {
            smoothingStartPosition = new Vector3(renderPosition);
            smoothingStartRotation = new Quaternion(renderRotation);
            smoothingStartScale = new Vector3(renderScale);
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
        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE) {
            return computeAnchoredWorldTransform(tickDelta, 0).position();
        }
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        float x = previousPosition.x + (renderPosition.x - previousPosition.x) * t;
        float y = previousPosition.y + (renderPosition.y - previousPosition.y) * t;
        float z = previousPosition.z + (renderPosition.z - previousPosition.z) * t;
        return new Vector3(x, y, z);
    }

    public Quaternion getInterpolatedRotation(float tickDelta) {
        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE) {
            return computeAnchoredWorldTransform(tickDelta, 0).rotation();
        }
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        return smoothingStartRotation.slerp(renderRotation, t);
    }

    public Vector3 getInterpolatedScale(float tickDelta) {
        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE) {
            return computeAnchoredWorldTransform(tickDelta, 0).scale();
        }
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        return Vector3.lerp(smoothingStartScale, renderScale, t);
    }


    public BlockPos getBlockPos() {
        Vector3 pos = getPosition();
        return BlockPos.ofFloored(pos.x, pos.y, pos.z);
    }

    private float getCurrentTickDelta() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return 1.0f;
        }
        return client.getRenderTickCounter().getTickDelta(true);
    }

    private WorldTransform computeAnchoredWorldTransform(float tickDelta, int depth) {
        if (depth > 8) {
            return fallbackWorldTransform();
        }

        WorldTransform parent = resolveAnchorParentTransform(tickDelta, depth);
        if (parent == null) {
            return fallbackWorldTransform();
        }

        Vector3 offset = anchorOffset != null ? new Vector3(anchorOffset) : Vector3.zero();
        if (anchorOffsetLocal) {
            Vector3 scaledLocal = offset.multiply(parent.scale());
            offset = parent.rotation().rotate(scaledLocal);
        }
        Vector3 worldPos = parent.position().add(offset);

        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        Quaternion localRotation = smoothingStartRotation.slerp(renderRotation, t);
        Vector3 localScale = Vector3.lerp(smoothingStartScale, renderScale, t);

        Quaternion worldRot = inheritRotation
                ? parent.rotation().multiply(localRotation).normalize()
                : localRotation;
        Vector3 worldScale = inheritScale
                ? parent.scale().multiply(localScale)
                : localScale;

        return new WorldTransform(worldPos, worldRot, worldScale);
    }

    private WorldTransform resolveAnchorParentTransform(float tickDelta, int depth) {
        return switch (anchorType) {
            case BLOCK -> {
                if (anchorBlockPos == null) {
                    yield null;
                }
                Vector3 pos = new Vector3(
                        anchorBlockPos.getX() + 0.5f,
                        anchorBlockPos.getY() + 0.5f,
                        anchorBlockPos.getZ() + 0.5f
                );
                yield new WorldTransform(pos, Quaternion.identity(), Vector3.one());
            }
            case ENTITY -> {
                Entity entity = ClientEntityResolver.resolve(anchorEntityUuid);
                if (entity == null) {
                    yield null;
                }
                Vec3d pos = entity.getLerpedPos(tickDelta);
                float yaw = entity.getYaw(tickDelta);
                float pitch = includePitch ? entity.getPitch(tickDelta) : 0.0f;
                yield new WorldTransform(new Vector3(pos.x, pos.y, pos.z), Quaternion.fromEuler(pitch, yaw, 0.0f), Vector3.one());
            }
            case MODEL -> {
                if (anchorModelId == null) {
                    yield null;
                }
                RenderableModel model = ClientModelManager.getInstance().getModel(anchorModelId);
                if (model == null) {
                    yield null;
                }
                yield new WorldTransform(
                        model.getInterpolatedPosition(tickDelta),
                        model.getInterpolatedRotation(tickDelta),
                        model.getInterpolatedScale(tickDelta)
                );
            }
            case FREE -> null;
        };
    }

    private WorldTransform fallbackWorldTransform() {
        return new WorldTransform(
                renderPosition != null ? new Vector3(renderPosition) : Vector3.zero(),
                renderRotation != null ? new Quaternion(renderRotation) : Quaternion.identity(),
                renderScale != null ? new Vector3(renderScale) : Vector3.one()
        );
    }

    private record WorldTransform(Vector3 position, Quaternion rotation, Vector3 scale) {
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
