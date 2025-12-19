package com.moud.server.proxy;

import com.moud.server.anchor.AnchorBehavior;
import com.moud.server.anchor.Transformable;
import com.moud.server.ts.TsExpose;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.entity.DisplayManager;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.instance.Instance;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@TsExpose
public class MediaDisplayProxy implements Transformable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaDisplayProxy.class);
    private static final float POSITION_EPSILON = 0.0001f;

    private final long id;
    private final Instance instance;

    private Vector3 position;
    private Quaternion rotation;
    private Vector3 scale;
    private MoudPackets.DisplayBillboardMode billboardMode = MoudPackets.DisplayBillboardMode.NONE;
    private boolean renderThroughBlocks = false;

    private final AnchorBehavior anchor = new AnchorBehavior(this::onAnchorChanged);

    private MoudPackets.DisplayContentType contentType = MoudPackets.DisplayContentType.IMAGE;
    private String primarySource;
    private List<String> frameSources = new ArrayList<>();
    private float frameRate = 0.0f;
    private boolean loop = false;

    private boolean playing = false;
    private float playbackSpeed = 1.0f;
    private float playbackOffsetSeconds = 0.0f;
    private long playbackStartMillis = System.currentTimeMillis();

    private boolean removed = false;

    public MediaDisplayProxy(Instance instance, Vector3 position, Quaternion rotation, Vector3 scale) {
        this.id = DisplayManager.getInstance().nextId();
        this.instance = instance;
        this.position = position != null ? new Vector3(position) : Vector3.zero();
        this.rotation = rotation != null ? new Quaternion(rotation) : Quaternion.identity();
        this.scale = scale != null ? new Vector3(scale) : Vector3.one();
        if (this.scale.x == 0) this.scale.x = 0.001f;
        if (this.scale.y == 0) this.scale.y = 0.001f;
        if (this.scale.z == 0) this.scale.z = 0.001f;

        DisplayManager.getInstance().register(this);
        broadcastCreate();
    }

    @HostAccess.Export
    public long getId() {
        return id;
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return new Vector3(position);
    }

    @HostAccess.Export
    public void setPosition(Vector3 newPosition) {
        if (newPosition == null) {
            return;
        }
        this.position = new Vector3(newPosition);
        if (anchor.isAnchored()) {
            anchor.clear();
        }
        broadcastTransform();
    }

    @HostAccess.Export
    public Quaternion getRotation() {
        return new Quaternion(rotation);
    }

    @HostAccess.Export
    public void setRotation(Quaternion newRotation) {
        if (newRotation == null) {
            return;
        }
        this.rotation = new Quaternion(newRotation);
        broadcastTransform();
    }

    @HostAccess.Export
    public void setRotationFromEuler(double pitch, double yaw, double roll) {
        this.rotation = Quaternion.fromEuler((float) pitch, (float) yaw, (float) roll);
        broadcastTransform();
    }

    @HostAccess.Export
    public void setBillboard(String mode) {
        if (mode == null) {
            return;
        }
        try {
            this.billboardMode = switch (mode.toLowerCase(Locale.ROOT)) {
                case "camera", "camera_facing", "face_camera" -> MoudPackets.DisplayBillboardMode.CAMERA_FACING;
                case "vertical", "y" -> MoudPackets.DisplayBillboardMode.VERTICAL;
                default -> MoudPackets.DisplayBillboardMode.NONE;
            };
            broadcastTransform();
        } catch (Exception e) {
            LOGGER.warn("Invalid billboard mode '{}'", mode, e);
        }
    }

    public MoudPackets.DisplayBillboardMode getBillboardMode() {
        return billboardMode;
    }

    @HostAccess.Export
    public void setRenderThroughBlocks(boolean enabled) {
        this.renderThroughBlocks = enabled;
        broadcastTransform();
    }

    public boolean isRenderThroughBlocks() {
        return renderThroughBlocks;
    }

    @HostAccess.Export
    public Vector3 getScale() {
        return new Vector3(scale);
    }

    @HostAccess.Export
    public void setTransform(Vector3 newPosition, Quaternion newRotation, Vector3 newScale) {
        boolean positionChanged = false;
        if (newPosition != null) {
            this.position = new Vector3(newPosition);
            if (anchor.isAnchored()) {
                anchor.clear();
            }
            positionChanged = true;
        }
        if (newRotation != null) {
            this.rotation = new Quaternion(newRotation);
        }
        if (newScale != null) {
            this.scale = ensureScaleSafe(newScale);
        }
        broadcastTransform();
    }

    @HostAccess.Export
    public void setScale(Vector3 newScale) {
        if (newScale == null) {
            return;
        }
        this.scale = ensureScaleSafe(newScale);
        broadcastTransform();
    }

    public Instance getInstance() {
        return instance;
    }

    @HostAccess.Export
    public void setAnchorToBlock(int x, int y, int z, Vector3 offset) {
        anchor.anchorToBlock(x, y, z, offset);
        updateAnchorTracking();
    }

    @HostAccess.Export
    public void setAnchorToEntity(UUID entityUuid, Vector3 offset) {
        setAnchorToEntity(entityUuid, offset, false, false, false, false);
    }

    @HostAccess.Export
    public void setAnchorToEntity(UUID entityUuid, Vector3 offset, boolean anchorOffsetLocal, boolean inheritRotation,
                                  boolean inheritScale, boolean includePitch) {
        if (entityUuid == null) {
            LOGGER.warn("Attempted to anchor display {} to null entity UUID", id);
            return;
        }
        anchor.anchorToEntity(entityUuid, offset, Quaternion.identity(), Vector3.one(),
                inheritRotation, inheritScale, includePitch, anchorOffsetLocal);
        updateAnchorTracking();
    }

    public void attachToEntity(net.minestom.server.entity.Entity entity, Vector3 offset) {
        attachToEntity(entity, offset, false, false, false, false);
    }

    public void attachToEntity(net.minestom.server.entity.Entity entity, Vector3 offset, boolean anchorOffsetLocal, boolean inheritRotation,
                               boolean inheritScale, boolean includePitch) {
        if (entity == null) {
            return;
        }
        anchor.anchorToEntity(entity.getUuid(), offset, Quaternion.identity(), Vector3.one(),
                inheritRotation, inheritScale, includePitch, anchorOffsetLocal);
        updateAnchorTracking();
    }

    @HostAccess.Export
    public void setAnchorToModel(ModelProxy model, Vector3 offset) {
        setAnchorToModel(model, offset, true, true, true, false);
    }

    @HostAccess.Export
    public void setAnchorToModel(ModelProxy model, Vector3 offset, boolean anchorOffsetLocal, boolean inheritRotation,
                                 boolean inheritScale, boolean includePitch) {
        if (model == null) {
            return;
        }
        anchor.anchorToModel(model.getId(), offset, Quaternion.identity(), Vector3.one(),
                inheritRotation, inheritScale, anchorOffsetLocal);
        updateAnchorTracking();
    }

    @HostAccess.Export
    public void clearAnchor() {
        anchor.clear();
    }

    @HostAccess.Export
    public void setImage(String source) {
        if (source == null || source.isEmpty()) {
            LOGGER.warn("Display {} received empty image source", id);
            return;
        }
        this.primarySource = source;
        this.contentType = source.startsWith("http")
                ? MoudPackets.DisplayContentType.URL
                : MoudPackets.DisplayContentType.IMAGE;
        this.frameSources = Collections.emptyList();
        this.frameRate = 0.0f;
        this.loop = false;
        broadcastContent();
    }

    @HostAccess.Export
    public void setFrameSequence(String[] frames, double fps, boolean loop) {
        if (frames == null || frames.length == 0) {
            LOGGER.warn("Display {} frame sequence requested without frames", id);
            return;
        }
        this.contentType = MoudPackets.DisplayContentType.FRAME_SEQUENCE;
        this.primarySource = null;
        this.frameSources = List.of(frames.clone());
        this.frameRate = (float) Math.max(0.01, fps);
        this.loop = loop;
        broadcastContent();
    }

    @HostAccess.Export
    public void setVideo(String url, double targetFps, boolean loop) {
        if (url == null || url.isEmpty()) {
            LOGGER.warn("Display {} video requested without url", id);
            return;
        }
        this.contentType = MoudPackets.DisplayContentType.URL;
        this.primarySource = url;
        this.frameSources = Collections.emptyList();
        this.frameRate = (float) Math.max(0.01, targetFps);
        this.loop = loop;
        broadcastContent();
    }

    @HostAccess.Export
    public void play() {
        if (playing) {
            return;
        }
        playbackOffsetSeconds = getPlaybackOffsetSeconds();
        playbackStartMillis = System.currentTimeMillis();
        playing = true;
        broadcastPlayback();
    }

    @HostAccess.Export
    public void pause() {
        if (!playing) {
            return;
        }
        playbackOffsetSeconds = getPlaybackOffsetSeconds();
        playing = false;
        broadcastPlayback();
    }

    @HostAccess.Export
    public void setPlaybackSpeed(double speed) {
        float clamped = (float) Math.max(0.05, Math.min(speed, 64.0));
        playbackOffsetSeconds = getPlaybackOffsetSeconds();
        playbackStartMillis = System.currentTimeMillis();
        playbackSpeed = clamped;
        broadcastPlayback();
    }

    @HostAccess.Export
    public void seek(double seconds) {
        playbackOffsetSeconds = (float) Math.max(0.0, seconds);
        playbackStartMillis = System.currentTimeMillis();
        broadcastPlayback();
    }

    @HostAccess.Export
    public void setLoop(boolean loop) {
        this.loop = loop;
        broadcastContent();
    }

    @HostAccess.Export
    public void setFrameRate(double fps) {
        this.frameRate = (float) Math.max(0.01, fps);
        broadcastContent();
    }

    public MoudPackets.DisplayContentType getContentType() {
        return contentType;
    }

    public String getPrimarySource() {
        return primarySource;
    }

    public List<String> getFrameSources() {
        return List.copyOf(frameSources);
    }

    @HostAccess.Export
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        broadcast(new MoudPackets.S2C_RemoveDisplayPacket(id));
        DisplayManager.getInstance().unregister(this);
    }

    public void removeSilently() {
        if (removed) {
            return;
        }
        removed = true;
    }

    public void updateAnchorTracking() {
        if (removed) {
            return;
        }
        anchor.updateTracking(this);
    }



    @Override
    public void applyAnchoredTransform(Vector3 worldPos, Quaternion worldRot, Vector3 worldScale) {
        if (worldPos == null) {
            return;
        }

        double dx = worldPos.x - position.x;
        double dy = worldPos.y - position.y;
        double dz = worldPos.z - position.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq < POSITION_EPSILON) {
            return;
        }
        this.position = new Vector3(worldPos);
        if (worldRot != null) {
            this.rotation = new Quaternion(worldRot);
        }
        if (worldScale != null) {
            this.scale = new Vector3(worldScale);
        }
        broadcastTransform();
    }

    private void onAnchorChanged(AnchorBehavior anchor) {
        broadcastAnchorUpdate();
    }

    private void broadcastCreate() {
        broadcast(buildCreatePacket());
    }

    private void broadcastTransform() {
        broadcast(new MoudPackets.S2C_UpdateDisplayTransformPacket(
                id,
                new Vector3(position),
                new Quaternion(rotation),
                new Vector3(scale),
                billboardMode,
                renderThroughBlocks
        ));
    }

    private void broadcastAnchorUpdate() {
        broadcast(snapshotAnchor());
    }

    public MoudPackets.S2C_UpdateDisplayAnchorPacket snapshotAnchor() {
        MoudPackets.DisplayAnchorType anchorType = anchor.getAnchorType();
        return new MoudPackets.S2C_UpdateDisplayAnchorPacket(
                id,
                anchorType,
                anchorType == MoudPackets.DisplayAnchorType.BLOCK && anchor.getAnchorBlockX() != null
                        ? new Vector3(anchor.getAnchorBlockX(), anchor.getAnchorBlockY(), anchor.getAnchorBlockZ())
                        : null,
                anchorType == MoudPackets.DisplayAnchorType.ENTITY ? anchor.getAnchorEntityUuid() : null,
                anchor.getLocalPosition(),
                anchorType == MoudPackets.DisplayAnchorType.MODEL ? anchor.getAnchorModelId() : null,
                anchor.isLocalSpace(),
                anchor.isInheritRotation(),
                anchor.isInheritScale(),
                anchor.isIncludePitch()
        );
    }

    private void broadcastContent() {
        broadcast(new MoudPackets.S2C_UpdateDisplayContentPacket(
                id,
                contentType,
                primarySource,
                frameSources.isEmpty() ? null : List.copyOf(frameSources),
                frameRate,
                loop
        ));
    }

    private void broadcastPlayback() {
        broadcast(new MoudPackets.S2C_UpdateDisplayPlaybackPacket(
                id,
                playing,
                playbackSpeed,
                getPlaybackOffsetSeconds()
        ));
    }

    public MoudPackets.S2C_CreateDisplayPacket snapshot() {
        return buildCreatePacket();
    }

    private MoudPackets.S2C_CreateDisplayPacket buildCreatePacket() {
        MoudPackets.DisplayAnchorType anchorType = anchor.getAnchorType();
        return new MoudPackets.S2C_CreateDisplayPacket(
                id,
                new Vector3(position),
                new Quaternion(rotation),
                new Vector3(scale),
                billboardMode,
                renderThroughBlocks,
                anchorType,
                anchorType == MoudPackets.DisplayAnchorType.BLOCK && anchor.getAnchorBlockX() != null
                        ? new Vector3(anchor.getAnchorBlockX(), anchor.getAnchorBlockY(), anchor.getAnchorBlockZ())
                        : null,
                anchorType == MoudPackets.DisplayAnchorType.ENTITY ? anchor.getAnchorEntityUuid() : null,
                anchor.getLocalPosition(),
                contentType,
                primarySource,
                frameSources.isEmpty() ? null : List.copyOf(frameSources),
                frameRate,
                loop,
                playing,
                playbackSpeed,
                getPlaybackOffsetSeconds()
        );
    }

    private void broadcast(Object packet) {
        ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
        if (networkManager != null) {
            networkManager.broadcast(packet);
        }
    }

    private Vector3 ensureScaleSafe(Vector3 newScale) {
        Vector3 safe = new Vector3(newScale);
        if (safe.x == 0f) safe.x = 0.001f;
        if (safe.y == 0f) safe.y = 0.001f;
        if (safe.z == 0f) safe.z = 0.001f;
        return safe;
    }

    private float getPlaybackOffsetSeconds() {
        if (!playing) {
            return playbackOffsetSeconds;
        }
        long elapsedMillis = System.currentTimeMillis() - playbackStartMillis;
        return playbackOffsetSeconds + (elapsedMillis / 1000.0f) * playbackSpeed;
    }
}
