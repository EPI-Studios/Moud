package com.moud.server.proxy;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.bridge.AxiomBridgeService;
import com.moud.server.entity.DisplayManager;
import com.moud.server.entity.ModelManager;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MediaDisplayProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaDisplayProxy.class);
    private static final float POSITION_EPSILON = 0.0001f;

    private final long id;
    private final Instance instance;

    private Vector3 position;
    private Quaternion rotation;
    private Vector3 scale;

    private MoudPackets.DisplayAnchorType anchorType = MoudPackets.DisplayAnchorType.FREE;
    private int anchorBlockX;
    private int anchorBlockY;
    private int anchorBlockZ;
    private WeakReference<Entity> anchorEntityRef;
    private UUID anchorEntityUuid;
    private Vector3 anchorOffset = Vector3.zero();

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
        this.anchorType = MoudPackets.DisplayAnchorType.FREE;
        this.anchorEntityRef = null;
        this.anchorEntityUuid = null;
        broadcastTransform(true);
        broadcastAnchorUpdate();
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
        broadcastTransform(true);
    }

    @HostAccess.Export
    public void setRotationFromEuler(double pitch, double yaw, double roll) {
        this.rotation = Quaternion.fromEuler((float) pitch, (float) yaw, (float) roll);
        broadcastTransform(true);
    }

    @HostAccess.Export
    public Vector3 getScale() {
        return new Vector3(scale);
    }

    @HostAccess.Export
    public void setScale(Vector3 newScale) {
        if (newScale == null) {
            return;
        }
        this.scale = new Vector3(newScale);
        broadcastTransform(true);
    }

    public Instance getInstance() {
        return instance;
    }

    @HostAccess.Export
    public void setAnchorToBlock(int x, int y, int z, Vector3 offset) {
        this.anchorType = MoudPackets.DisplayAnchorType.BLOCK;
        this.anchorBlockX = x;
        this.anchorBlockY = y;
        this.anchorBlockZ = z;
        this.anchorOffset = offset != null ? new Vector3(offset) : Vector3.zero();
        this.anchorEntityRef = null;
        this.anchorEntityUuid = null;
        updateAnchorTracking();
        broadcastAnchorUpdate();
    }

    @HostAccess.Export
    public void setAnchorToEntity(UUID entityUuid, Vector3 offset) {
        Entity entity = resolveAnchorEntity(entityUuid);
        if (entity == null) {
            LOGGER.warn("Attempted to anchor display {} to missing entity {}", id, entityUuid);
            return;
        }
        attachToEntity(entity, offset);
    }

    public void attachToEntity(Entity entity, Vector3 offset) {
        if (entity == null) {
            return;
        }
        this.anchorType = MoudPackets.DisplayAnchorType.ENTITY;
        this.anchorEntityUuid = entity.getUuid();
        this.anchorEntityRef = new WeakReference<>(entity);
        this.anchorOffset = offset != null ? new Vector3(offset) : Vector3.zero();
        updateAnchorTracking();
        broadcastAnchorUpdate();
    }

    @HostAccess.Export
    public void clearAnchor() {
        this.anchorType = MoudPackets.DisplayAnchorType.FREE;
        this.anchorEntityRef = null;
        this.anchorEntityUuid = null;
        broadcastAnchorUpdate();
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

    public void applyBridgeTransform(Vector3 newPosition, Quaternion newRotation, Vector3 newScale) {
        if (newPosition != null) {
            this.position = new Vector3(newPosition);
        }
        if (newRotation != null) {
            this.rotation = new Quaternion(newRotation);
        }
        if (newScale != null) {
            this.scale = new Vector3(newScale);
        }
        if (anchorType != MoudPackets.DisplayAnchorType.FREE) {
            anchorType = MoudPackets.DisplayAnchorType.FREE;
            anchorEntityRef = null;
            anchorEntityUuid = null;
            broadcastAnchorUpdate();
        }
        broadcastTransform(false);
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
        AxiomBridgeService bridge = AxiomBridgeService.getInstance();
        if (bridge != null) {
            bridge.onDisplayRemoved(this);
        }
        DisplayManager.getInstance().unregister(this);
    }

    public void removeSilently() {
        if (removed) {
            return;
        }
        removed = true;
        AxiomBridgeService bridge = AxiomBridgeService.getInstance();
        if (bridge != null) {
            bridge.onDisplayRemoved(this);
        }
    }

    public void updateAnchorTracking() {
        if (removed) {
            return;
        }
        switch (anchorType) {
            case BLOCK -> updateBlockAnchor();
            case ENTITY -> updateEntityAnchor();
            case FREE -> {
            }
        }
    }

    private void updateBlockAnchor() {
        Vector3 target = new Vector3(
                anchorBlockX + 0.5f,
                anchorBlockY + 0.5f,
                anchorBlockZ + 0.5f
        ).add(anchorOffset);
        updatePositionIfChanged(target);
    }

    private void updateEntityAnchor() {
        Entity entity = anchorEntityRef != null ? anchorEntityRef.get() : null;
        if (entity == null && anchorEntityUuid != null) {
            entity = resolveAnchorEntity(anchorEntityUuid);
            if (entity != null) {
                anchorEntityRef = new WeakReference<>(entity);
            }
        }
        if (entity == null) {
            LOGGER.debug("Display {} anchor entity missing, reverting to free mode", id);
            clearAnchor();
            return;
        }

        Pos position = entity.getPosition();
        Vector3 target = new Vector3(position.x(), position.y(), position.z()).add(anchorOffset);
        updatePositionIfChanged(target);
    }

    private void updatePositionIfChanged(Vector3 target) {
        if (target == null) {
            return;
        }
        double dx = target.x - position.x;
        double dy = target.y - position.y;
        double dz = target.z - position.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq < POSITION_EPSILON) {
            return;
        }
        this.position = new Vector3(target);
        broadcastTransform(true);
    }

    private Entity resolveAnchorEntity(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        var connectionManager = MinecraftServer.getConnectionManager();
        Player entity = connectionManager.getOnlinePlayers().stream()
                .filter(player -> player.getUuid().equals(uuid))
                .findFirst()
                .orElse(null);
        if (entity != null) {
            return entity;
        }
        ModelProxy modelProxy = ModelManager.getInstance().getByEntityUuid(uuid);
        if (modelProxy != null) {
            return modelProxy.getEntity();
        }
        return null;
    }

    private void broadcastCreate() {
        broadcast(buildCreatePacket());
        AxiomBridgeService bridge = AxiomBridgeService.getInstance();
        if (bridge != null) {
            bridge.onDisplayCreated(this);
        }
    }

    private void broadcastTransform(boolean notifyBridge) {
        broadcast(new MoudPackets.S2C_UpdateDisplayTransformPacket(
                id,
                new Vector3(position),
                new Quaternion(rotation),
                new Vector3(scale)
        ));
        if (notifyBridge) {
            AxiomBridgeService bridge = AxiomBridgeService.getInstance();
            if (bridge != null) {
                bridge.onDisplayMoved(this);
            }
        }
    }

    private void broadcastAnchorUpdate() {
        broadcast(new MoudPackets.S2C_UpdateDisplayAnchorPacket(
                id,
                anchorType,
                anchorType == MoudPackets.DisplayAnchorType.BLOCK ? new Vector3(anchorBlockX, anchorBlockY, anchorBlockZ) : null,
                anchorType == MoudPackets.DisplayAnchorType.ENTITY ? anchorEntityUuid : null,
                anchorOffset != null ? new Vector3(anchorOffset) : null
        ));
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
        return new MoudPackets.S2C_CreateDisplayPacket(
                id,
                new Vector3(position),
                new Quaternion(rotation),
                new Vector3(scale),
                anchorType,
                anchorType == MoudPackets.DisplayAnchorType.BLOCK ? new Vector3(anchorBlockX, anchorBlockY, anchorBlockZ) : null,
                anchorType == MoudPackets.DisplayAnchorType.ENTITY ? anchorEntityUuid : null,
                anchorOffset != null ? new Vector3(anchorOffset) : null,
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

    private float getPlaybackOffsetSeconds() {
        if (!playing) {
            return playbackOffsetSeconds;
        }
        long elapsedMillis = System.currentTimeMillis() - playbackStartMillis;
        return playbackOffsetSeconds + (elapsedMillis / 1000.0f) * playbackSpeed;
    }
}