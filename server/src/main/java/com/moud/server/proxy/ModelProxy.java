package com.moud.server.proxy;

import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.anchor.AnchorBehavior;
import com.moud.server.anchor.Transformable;
import com.moud.server.assets.ModelTextureResolver;
import com.moud.server.collision.MinestomCollisionAdapter;
import com.moud.server.entity.ModelManager;
import com.moud.server.network.NetworkCompression;
import com.moud.server.network.sync.SyncableObject;
import com.moud.server.physics.PhysicsService;
import com.moud.server.physics.mesh.ModelCollisionLibrary;
import com.moud.server.physics.mesh.ModelCollisionLibrary.MeshData;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.logging.MoudLogger;
import com.moud.server.ts.TsExpose;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.other.InteractionMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;
import org.graalvm.polyglot.HostAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@TsExpose
public class ModelProxy implements Transformable, SyncableObject {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(ModelProxy.class);
    private final long id;
    private final Entity entity;
    private final String modelPath;

    private Vector3 position;
    private Quaternion rotation;
    private Vector3 scale;
    private BoundingBox collisionBox;
    private String texturePath;
    private List<OBB> collisionBoxes = new ArrayList<>();
    private CollisionMode collisionMode = CollisionMode.AUTO;
    private byte[] cachedCompressedVertices;
    private byte[] cachedCompressedIndices;
    private boolean manualCollisionOverride;
    private Vector3 lastBroadcastPosition;
    private Quaternion lastBroadcastRotation;
    private Vector3 lastBroadcastScale;
    private long lastBroadcastNanos;

    private final AnchorBehavior anchor = new AnchorBehavior(this::onAnchorChanged);

    private static final double MIN_COLLISION_SIZE = 0.11;

    private static final double MIN_POS_DELTA_SQ = 1.0e-4;
    private static final double MIN_SCALE_DELTA = 1.0e-4;
    private static final float MIN_ROTATION_DELTA_DEG = 0.1f;
    private static final long MIN_BROADCAST_INTERVAL_NANOS = 20_000_000L;


    public ModelProxy(Instance instance, String modelPath, Vector3 position, Quaternion rotation, Vector3 scale, String texturePath) {
        this(instance, modelPath, position, rotation, scale, texturePath, false);
    }

    public ModelProxy(Instance instance, String modelPath, Vector3 position, Quaternion rotation, Vector3 scale, String texturePath, boolean manualCollisionOverride) {
        this.id = ModelManager.getInstance().nextId();
        this.modelPath = modelPath;
        this.position = position;
        this.rotation = rotation;
        this.scale = scale;
        this.manualCollisionOverride = manualCollisionOverride;
        this.texturePath = texturePath != null ? texturePath : "";
        if (this.texturePath.isBlank()) {
            this.texturePath = ModelTextureResolver.inferTextureId(modelPath).orElse("");
        }

        this.entity = new Entity(EntityType.INTERACTION);
        InteractionMeta meta = (InteractionMeta) this.entity.getEntityMeta();
        meta.setResponse(true);
        this.entity.setInstance(instance, new Pos(position.x, position.y, position.z));

        generateAccurateCollision();

        ModelManager.getInstance().register(this);
        broadcastCreate();

    }

    private void generateAccurateCollision() {
        generateAccurateCollision(5);
    }

    private void generateAccurateCollision(int retries) {
        com.moud.server.physics.mesh.ModelCollisionLibrary.getCollisionBoxesAsync(modelPath)
                .thenAccept(boxes -> {
                    if (boxes != null && !boxes.isEmpty()) {
                        net.minestom.server.MinecraftServer.getSchedulerManager()
                                .buildTask(() -> setCollisionBoxes(boxes))
                                .schedule();
                    } else if (retries > 0) {
                        net.minestom.server.MinecraftServer.getSchedulerManager()
                                .buildTask(() -> generateAccurateCollision(retries - 1))
                                .delay(TaskSchedule.tick(20))
                                .schedule();
                    }
                });
    }

    private void broadcast(Object packet) {
        ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
        if (networkManager != null) {
            networkManager.broadcast(packet);
        }
    }

    private boolean shouldBroadcastUpdate() {
        boolean hasBaseline = lastBroadcastPosition != null && lastBroadcastRotation != null && lastBroadcastScale != null;
        if (!hasBaseline) {
            return true;
        }

        double posDeltaSq = position != null ? position.distanceSquared(lastBroadcastPosition) : Double.MAX_VALUE;
        double scaleDelta = scale != null && lastBroadcastScale != null
                ? Math.max(Math.abs(scale.x - lastBroadcastScale.x),
                Math.max(Math.abs(scale.y - lastBroadcastScale.y), Math.abs(scale.z - lastBroadcastScale.z)))
                : Double.MAX_VALUE;
        float rotDelta = rotation != null && lastBroadcastRotation != null ? rotation.angleTo(lastBroadcastRotation) : Float.MAX_VALUE;

        boolean changed = posDeltaSq > MIN_POS_DELTA_SQ
                || scaleDelta > MIN_SCALE_DELTA
                || rotDelta > MIN_ROTATION_DELTA_DEG;

        if (!changed) {
            return false;
        }

        long now = System.nanoTime();
        return now - lastBroadcastNanos >= MIN_BROADCAST_INTERVAL_NANOS;
    }

    private void snapshotBroadcastState() {
        this.lastBroadcastPosition = position != null ? new Vector3(position) : null;
        this.lastBroadcastRotation = rotation != null ? new Quaternion(rotation) : null;
        this.lastBroadcastScale = scale != null ? new Vector3(scale) : null;
        this.lastBroadcastNanos = System.nanoTime();
    }



    @Override
    public void applyAnchoredTransform(Vector3 worldPos, Quaternion worldRot, Vector3 worldScale) {
        if (worldPos == null || worldRot == null || worldScale == null) {
            return;
        }
        this.position = worldPos;
        this.rotation = worldRot;
        this.scale = worldScale;
        this.entity.teleport(new Pos(worldPos.x, worldPos.y, worldPos.z));
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.handleModelManualTransform(this, worldPos, worldRot);
        }
    }

    private void onAnchorChanged(AnchorBehavior anchor) {
        broadcastAnchorUpdate();
    }

    public enum CollisionMode {
        AUTO,
        CONVEX,
        STATIC_MESH,
        CAPSULE
    }

    private void prepareCollisionPayload() {
        try {
            MeshData mesh = ModelCollisionLibrary.getMesh(modelPath);
            if (mesh == null) {
                LOGGER.warn("Mesh collision payload unavailable for {} (mesh data missing)", modelPath);
                return;
            }
            this.cachedCompressedVertices = NetworkCompression.gzipFloatArray(mesh.vertices());
            this.cachedCompressedIndices = NetworkCompression.gzipIntArray(mesh.indices());
            LOGGER.info("Prepared mesh collision payload for model {} (vertices={}, indices={})",
                    modelPath,
                    mesh.vertices() != null ? mesh.vertices().length : 0,
                    mesh.indices() != null ? mesh.indices().length : 0);
        } catch (Exception ignored) {
        }
    }

    public void ensureCollisionPayload() {
        if (cachedCompressedVertices == null || cachedCompressedIndices == null) {
            prepareCollisionPayload();
        }
    }

    private MoudPackets.CollisionMode toWireCollisionMode() {
        return switch (collisionMode) {
            case STATIC_MESH -> MoudPackets.CollisionMode.MESH;
            case CONVEX -> MoudPackets.CollisionMode.CONVEX_HULLS;
            case CAPSULE -> MoudPackets.CollisionMode.BOX;
            case AUTO -> {
                if (cachedCompressedVertices != null && cachedCompressedIndices != null) {
                    yield MoudPackets.CollisionMode.MESH;
                }
                yield MoudPackets.CollisionMode.BOX;
            }
        };
    }

    public MoudPackets.CollisionMode getWireCollisionMode() {
        return toWireCollisionMode();
    }

    public byte[] getCompressedVertices() {
        return cachedCompressedVertices;
    }

    public byte[] getCompressedIndices() {
        return cachedCompressedIndices;
    }

    private void broadcastCreate() {
        ensureCollisionPayload();
        broadcast(buildCreatePacket());
        snapshotBroadcastState();
    }

    private MoudPackets.S2C_CreateModelPacket buildCreatePacket() {
        return new MoudPackets.S2C_CreateModelPacket(
                id,
                modelPath,
                position,
                rotation,
                scale,
                getCollisionWidth(),
                getCollisionHeight(),
                getCollisionDepth(),
                texturePath,
                toCollisionData(collisionBoxes),
                toWireCollisionMode(),
                cachedCompressedVertices,
                cachedCompressedIndices,
                List.of()
        );
    }

    private void broadcastUpdate() {
        if (!shouldBroadcastUpdate()) {
            return;
        }
        MoudPackets.S2C_UpdateModelTransformPacket packet = new MoudPackets.S2C_UpdateModelTransformPacket(
                id, position, rotation, scale
        );
        broadcast(packet);
        snapshotBroadcastState();
    }

    private void broadcastAnchorUpdate() {
        broadcast(snapshotAnchor());
    }

    public MoudPackets.S2C_UpdateModelAnchorPacket snapshotAnchor() {
        MoudPackets.DisplayAnchorType anchorType = anchor.getAnchorType();
        return new MoudPackets.S2C_UpdateModelAnchorPacket(
                id,
                anchorType,
                anchorType == MoudPackets.DisplayAnchorType.ENTITY ? anchor.getAnchorEntityUuid() : null,
                anchorType == MoudPackets.DisplayAnchorType.MODEL ? anchor.getAnchorModelId() : null,
                anchorType == MoudPackets.DisplayAnchorType.BLOCK && anchor.getAnchorBlockX() != null
                        ? new Vector3(anchor.getAnchorBlockX(), anchor.getAnchorBlockY(), anchor.getAnchorBlockZ())
                        : null,
                anchor.getLocalPosition(),
                anchor.getLocalRotation(),
                anchor.getLocalScale(),
                anchor.isLocalSpace(),
                anchor.isInheritRotation(),
                anchor.isInheritScale(),
                anchor.isIncludePitch()
        );
    }

    @Override
    public List<Object> snapshotPackets() {
        ensureCollisionPayload();

        List<Object> packets = new ArrayList<>(3);
        packets.add(buildCreatePacket());
        packets.add(snapshotAnchor());

        String texture = getTexture();
        if (texture != null && !texture.isBlank()) {
            packets.add(new MoudPackets.S2C_UpdateModelTexturePacket(id, texture));
        }

        return packets;
    }

    public void updateAnchorTracking() {
        anchor.updateTracking(this);
    }

    @HostAccess.Export
    public long getId() {
        return id;
    }

    public Entity getEntity() {
        return entity;
    }

    @HostAccess.Export
    public String getModelPath() {
        return modelPath;
    }

    @HostAccess.Export
    public void setPosition(Vector3 position) {
        if (position == null) {
            return;
        }
        if (anchor.isAnchored()) {
            clearAnchor();
        }
        Vector3 pos = new Vector3(position);
        this.position = pos;
        this.entity.teleport(new Pos(pos.x, pos.y, pos.z));
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.handleModelManualTransform(this, pos, null);
        }
        broadcastUpdate();
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return position;
    }

    @HostAccess.Export
    public void setTransform(Vector3 position, Quaternion rotation, Vector3 scale) {
        if (anchor.isAnchored()) {
            clearAnchor();
        }
        if (position != null) {
            this.position = position;
            this.entity.teleport(new Pos(position.x, position.y, position.z));
        }
        if (rotation != null) {
            this.rotation = rotation;
        }
        if (scale != null) {
            this.scale = scale;
        }
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.handleModelManualTransform(this, position, rotation);
        }
        broadcastUpdate();
    }

    @HostAccess.Export
    public void setRotation(Quaternion rotation) {
        if (anchor.isAnchored()) {
            clearAnchor();
        }
        this.rotation = rotation;
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.handleModelManualTransform(this, null, rotation);
        }
        broadcastUpdate();
    }

    @HostAccess.Export
    public void setRotationFromEuler(double pitch, double yaw, double roll) {
        if (anchor.isAnchored()) {
            clearAnchor();
        }
        this.rotation = Quaternion.fromEuler((float)pitch, (float)yaw, (float)roll);
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.handleModelManualTransform(this, null, this.rotation);
        }
        broadcastUpdate();
    }

    @HostAccess.Export
    public Quaternion getRotation() {
        return rotation;
    }

    @HostAccess.Export
    public void setScale(Vector3 scale) {
        if (anchor.isAnchored()) {
            clearAnchor();
        }
        this.scale = scale;
        broadcastUpdate();
    }

    @HostAccess.Export
    public Vector3 getScale() {
        return scale;
    }

    @HostAccess.Export
    public void setAnchorToBlock(int x, int y, int z, Vector3 localPosition) {
        setAnchorToBlock(x, y, z, localPosition, Quaternion.identity(), Vector3.one(), true, true, true);
    }

    @HostAccess.Export
    public void setAnchorToBlock(int x, int y, int z, Vector3 localPosition, Quaternion localRotation, Vector3 localScale,
                                 boolean inheritRotation, boolean inheritScale, boolean localSpace) {
        anchor.anchorToBlock(x, y, z, localPosition, localRotation, localScale, inheritRotation, inheritScale, localSpace);
        updateAnchorTracking();
    }

    @HostAccess.Export
    public void setAnchorToPlayer(PlayerProxy player, Vector3 localPosition) {
        setAnchorToPlayer(player, localPosition, Quaternion.identity(), Vector3.one(), true, true, false, true);
    }

    @HostAccess.Export
    public void setAnchorToPlayer(PlayerProxy player, Vector3 localPosition, Quaternion localRotation, Vector3 localScale,
                                  boolean inheritRotation, boolean inheritScale, boolean includePitch, boolean localSpace) {
        if (player == null) {
            return;
        }
        setAnchorToEntity(player.getUuid(), localPosition, localRotation, localScale,
                inheritRotation, inheritScale, includePitch, localSpace);
    }

    @HostAccess.Export
    public void setAnchorToEntity(String uuid, Vector3 localPosition, Quaternion localRotation, Vector3 localScale,
                                  boolean inheritRotation, boolean inheritScale, boolean includePitch, boolean localSpace) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }
        try {
            UUID parsed = UUID.fromString(uuid);
            anchor.anchorToEntity(parsed, localPosition, localRotation, localScale,
                    inheritRotation, inheritScale, includePitch, localSpace);
            updateAnchorTracking();
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid UUID '{}' for model anchor", uuid, e);
        }
    }

    @HostAccess.Export
    public void setAnchorToModel(ModelProxy model, Vector3 localPosition) {
        setAnchorToModel(model, localPosition, Quaternion.identity(), Vector3.one(), true, true, true);
    }

    @HostAccess.Export
    public void setAnchorToModel(ModelProxy model, Vector3 localPosition, Quaternion localRotation, Vector3 localScale,
                                 boolean inheritRotation, boolean inheritScale, boolean localSpace) {
        if (model == null) {
            return;
        }
        anchor.anchorToModel(model.getId(), localPosition, localRotation, localScale,
                inheritRotation, inheritScale, localSpace);
        updateAnchorTracking();
    }

    @HostAccess.Export
    public void clearAnchor() {
        anchor.clear();
    }

    @HostAccess.Export
    public void setTexture(String texturePath) {
        String previous = this.texturePath;
        this.texturePath = texturePath != null ? texturePath : "";
        LOGGER.info("Model {} texture change: '{}' -> '{}'", id, previous, this.texturePath);
        broadcast(new MoudPackets.S2C_UpdateModelTexturePacket(id, this.texturePath));
    }

    @HostAccess.Export
    public String getTexture() {
        return texturePath;
    }

    @HostAccess.Export
    public void setCollisionBox(double width, double height, double depth) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            this.collisionBox = null;
            this.entity.setBoundingBox(0, 0, 0);
            ((InteractionMeta)this.entity.getEntityMeta()).setWidth(0);
            ((InteractionMeta)this.entity.getEntityMeta()).setHeight(0);
        } else {
            this.manualCollisionOverride = true;
            this.collisionBox = new BoundingBox(width, height, depth);
            this.entity.setBoundingBox(this.collisionBox);
            ((InteractionMeta)this.entity.getEntityMeta()).setWidth((float)width);
            ((InteractionMeta)this.entity.getEntityMeta()).setHeight((float)height);
        }
        broadcast(new MoudPackets.S2C_UpdateModelCollisionPacket(
                id, getCollisionWidth(), getCollisionHeight(), getCollisionDepth()
        ));
    }

    @HostAccess.Export
    public CollisionMode getCollisionMode() {
        return collisionMode;
    }

    @HostAccess.Export
    public void setCollisionMode(CollisionMode mode) {
        this.collisionMode = mode != null ? mode : CollisionMode.AUTO;
    }

    @HostAccess.Export
    public void setCollisionMode(String mode) {
        if (mode == null) {
            this.collisionMode = CollisionMode.AUTO;
            return;
        }

        String normalized = mode.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        switch (normalized) {
            case "AUTO" -> this.collisionMode = CollisionMode.AUTO;
            case "CONVEX" -> this.collisionMode = CollisionMode.CONVEX;
            case "STATIC_MESH", "STATICMESH", "MESH", "STATIC" -> this.collisionMode = CollisionMode.STATIC_MESH;
            case "CAPSULE" -> this.collisionMode = CollisionMode.CAPSULE;
            default -> {
                try {
                    this.collisionMode = CollisionMode.valueOf(normalized);
                } catch (IllegalArgumentException ignored) {
                    this.collisionMode = CollisionMode.AUTO;
                }
            }
        }
    }

    public void setCollisionBoxes(List<OBB> boxes) {
        this.collisionBoxes = new ArrayList<>(boxes);
        List<BoundingBox> minestomBoxes = MinestomCollisionAdapter.convertToBoundingBoxes(
                boxes, rotation, scale
        );
        if (!manualCollisionOverride && !minestomBoxes.isEmpty()) {
            BoundingBox mainBox = MinestomCollisionAdapter.getLargestBox(minestomBoxes);
            this.collisionBox = mainBox;
            this.entity.setBoundingBox(mainBox);
            ((InteractionMeta)this.entity.getEntityMeta()).setWidth((float)mainBox.width());
            ((InteractionMeta)this.entity.getEntityMeta()).setHeight((float)mainBox.height());
        }
        broadcastCollisionBoxes();
    }

    private void broadcastCollisionBoxes() {
        if (collisionBoxes.isEmpty()) {
            return;
        }
        broadcast(new MoudPackets.S2C_SyncModelCollisionBoxesPacket(id, toCollisionData(collisionBoxes)));
    }

    private List<MoudPackets.CollisionBoxData> toCollisionData(List<OBB> boxes) {
        List<MoudPackets.CollisionBoxData> boxData = new ArrayList<>();
        if (boxes == null) {
            return boxData;
        }
        for (OBB obb : boxes) {
            if (obb == null) {
                continue;
            }
            boxData.add(new MoudPackets.CollisionBoxData(obb.center, obb.halfExtents, obb.rotation));
        }
        return boxData;
    }

    public List<OBB> getCollisionBoxes() {
        return new ArrayList<>(collisionBoxes);
    }

    private static double clampCollisionSize(double value) {
        double size = Math.abs(value);
        return size < MIN_COLLISION_SIZE ? MIN_COLLISION_SIZE : size;
    }

    public double getCollisionWidth() {
        return collisionBox != null ? Math.max(collisionBox.width(), MIN_COLLISION_SIZE) : 0;
    }

    public double getCollisionHeight() {
        return collisionBox != null ? Math.max(collisionBox.height(), MIN_COLLISION_SIZE) : 0;
    }

    public double getCollisionDepth() {
        return collisionBox != null ? Math.max(collisionBox.depth(), MIN_COLLISION_SIZE) : 0;
    }

    public void syncPhysicsTransform(Vector3 position, Quaternion rotation) {
        if (position != null) {
            this.position = position;
            this.entity.teleport(new Pos(position.x, position.y, position.z));
        }
        if (rotation != null) {
            this.rotation = rotation;
        }
        broadcastUpdate();
    }

    @HostAccess.Export
    public void attachToEntity(String uuid, Vector3 offset, boolean kinematic) {
        PhysicsService physics = PhysicsService.getInstance();
        if (physics == null || uuid == null || uuid.isBlank()) {
            return;
        }
        try {
            physics.attachFollow(this, java.util.UUID.fromString(uuid), offset, kinematic);
        } catch (Exception e) {
            LOGGER.warn("Failed to attach model {} to {}", id, uuid, e);
        }
    }

    @HostAccess.Export
    public void attachSpring(Vector3 anchor, double stiffness, double damping, double restLength) {
        PhysicsService physics = PhysicsService.getInstance();
        if (physics == null || anchor == null) {
            return;
        }
        physics.attachSpring(this, anchor, stiffness, damping, restLength);
    }

    @HostAccess.Export
    public void clearPhysicsConstraints() {
        PhysicsService physics = PhysicsService.getInstance();
        if (physics == null) return;
        physics.clearConstraints(this);
    }

    @HostAccess.Export
    public org.graalvm.polyglot.proxy.ProxyObject getPhysicsState() {
        PhysicsService physics = PhysicsService.getInstance();
        if (physics == null) return org.graalvm.polyglot.proxy.ProxyObject.fromMap(java.util.Map.of());
        var state = physics.getState(this);
        if (state == null) {
            return org.graalvm.polyglot.proxy.ProxyObject.fromMap(java.util.Map.of());
        }
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("linearVelocity", state.linearVelocity());
        map.put("angularVelocity", state.angularVelocity());
        map.put("active", state.active());
        map.put("onGround", state.onGround());
        map.put("lastImpulse", state.lastImpulse());
        map.put("hasFollowConstraint", state.hasFollowConstraint());
        map.put("hasSpringConstraint", state.hasSpringConstraint());
        return org.graalvm.polyglot.proxy.ProxyObject.fromMap(map);
    }

    @HostAccess.Export
    public void remove() {
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.detachModel(this);
        }
        ModelManager.getInstance().unregister(this);
        entity.remove();
        broadcast(new MoudPackets.S2C_RemoveModelPacket(id));

    }



}
