package com.moud.server.proxy;

import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.collision.MinestomCollisionAdapter;
import com.moud.server.entity.ModelManager;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

@TsExpose
public class ModelProxy {
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

    private MoudPackets.DisplayAnchorType anchorType = MoudPackets.DisplayAnchorType.FREE;
    private UUID anchorEntityUuid;
    private Long anchorModelId;
    private Integer anchorBlockX;
    private Integer anchorBlockY;
    private Integer anchorBlockZ;
    private Vector3 anchorLocalPosition = Vector3.zero();
    private Quaternion anchorLocalRotation = Quaternion.identity();
    private Vector3 anchorLocalScale = Vector3.one();
    private boolean anchorLocalSpace = true;
    private boolean anchorInheritRotation = true;
    private boolean anchorInheritScale = true;
    private boolean anchorIncludePitch = false;

    // Increased to ensure collision box half-extents > 0.05
    private static final double MIN_COLLISION_SIZE = 0.11;

    private static final double MIN_POS_DELTA_SQ = 1.0e-4; // 1 cm movement
    private static final double MIN_SCALE_DELTA = 1.0e-4;
    private static final float MIN_ROTATION_DELTA_DEG = 0.1f;
    private static final long MIN_BROADCAST_INTERVAL_NANOS = 20_000_000L; // 20 ms (~50/s)


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
            inferTexturePath();
        }
        if (this.texturePath == null) {
            this.texturePath = "";
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

    private void inferTexturePath() {
        try {
            Path projectRoot = com.moud.server.project.ProjectLoader.findProjectRoot();
            if (projectRoot == null || modelPath == null || !modelPath.contains(":")) {
                return;
            }
            String[] parts = modelPath.split(":", 2);
            String namespace = parts[0];
            String path = parts[1];
            Path assetsRoot = projectRoot.resolve("assets").resolve(namespace);

            Optional<String> mapFile = resolveMapKd(assetsRoot, path);
            String candidateName = mapFile.orElseGet(() -> {
                String base = Path.of(path).getFileName().toString();
                if (base.endsWith(".obj")) {
                    base = base.substring(0, base.length() - 4) + ".png";
                }
                return base;
            });

            if (candidateName == null || candidateName.isBlank()) {
                return;
            }

            try (Stream<Path> matches = Files.walk(assetsRoot.resolve("textures"))) {
                Optional<Path> found = matches
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equalsIgnoreCase(candidateName))
                        .findFirst();
                if (found.isPresent()) {
                    Path rel = assetsRoot.resolve("textures").relativize(found.get());
                    String textureId = namespace + ":textures/" + rel.toString().replace('\\', '/');
                    this.texturePath = textureId;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Optional<String> resolveMapKd(Path assetsRoot, String modelRelativePath) {
        try {
            Path modelPathFs = assetsRoot.resolve(modelRelativePath);
            Path mtl = modelPathFs.getParent().resolve(replaceExt(modelPathFs.getFileName().toString(), ".mtl"));
            if (!Files.isRegularFile(mtl)) {
                return Optional.empty();
            }
            return Files.lines(mtl)
                    .map(String::trim)
                    .filter(l -> l.startsWith("map_Kd"))
                    .map(l -> l.substring("map_Kd".length()).trim())
                    .filter(s -> !s.isBlank())
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String replaceExt(String name, String ext) {
        int idx = name.lastIndexOf('.');
        if (idx >= 0) {
            return name.substring(0, idx) + ext;
        }
        return name + ext;
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
            this.cachedCompressedVertices = compressFloatArray(mesh.vertices());
            this.cachedCompressedIndices = compressIntArray(mesh.indices());
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

    private byte[] compressFloatArray(float[] data) throws IOException {
        if (data == null || data.length == 0) return null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            byte[] bytes = new byte[data.length * 4];
            int idx = 0;
            for (float v : data) {
                int bits = Float.floatToIntBits(v);
                bytes[idx++] = (byte) (bits);
                bytes[idx++] = (byte) (bits >>> 8);
                bytes[idx++] = (byte) (bits >>> 16);
                bytes[idx++] = (byte) (bits >>> 24);
            }
            gzip.write(bytes);
        }
        return baos.toByteArray();
    }

    private byte[] compressIntArray(int[] data) throws IOException {
        if (data == null || data.length == 0) return null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            byte[] bytes = new byte[data.length * 4];
            int idx = 0;
            for (int v : data) {
                bytes[idx++] = (byte) (v);
                bytes[idx++] = (byte) (v >>> 8);
                bytes[idx++] = (byte) (v >>> 16);
                bytes[idx++] = (byte) (v >>> 24);
            }
            gzip.write(bytes);
        }
        return baos.toByteArray();
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
        if (cachedCompressedVertices == null || cachedCompressedIndices == null) {
            prepareCollisionPayload();
        }
        MoudPackets.S2C_CreateModelPacket packet = new MoudPackets.S2C_CreateModelPacket(
                id, modelPath, position, rotation, scale,
                getCollisionWidth(), getCollisionHeight(), getCollisionDepth(),
                texturePath,
                toCollisionData(collisionBoxes),
                toWireCollisionMode(),
                cachedCompressedVertices,
                cachedCompressedIndices,
                List.of()
        );
        broadcast(packet);
        snapshotBroadcastState();
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
        return new MoudPackets.S2C_UpdateModelAnchorPacket(
                id,
                anchorType,
                anchorType == MoudPackets.DisplayAnchorType.ENTITY ? anchorEntityUuid : null,
                anchorType == MoudPackets.DisplayAnchorType.MODEL ? anchorModelId : null,
                anchorType == MoudPackets.DisplayAnchorType.BLOCK && anchorBlockX != null && anchorBlockY != null && anchorBlockZ != null
                        ? new Vector3(anchorBlockX, anchorBlockY, anchorBlockZ)
                        : null,
                anchorLocalPosition != null ? new Vector3(anchorLocalPosition) : Vector3.zero(),
                anchorLocalRotation != null ? new Quaternion(anchorLocalRotation) : Quaternion.identity(),
                anchorLocalScale != null ? new Vector3(anchorLocalScale) : Vector3.one(),
                anchorLocalSpace,
                anchorInheritRotation,
                anchorInheritScale,
                anchorIncludePitch
        );
    }

    public void updateAnchorTracking() {
        if (anchorType == null || anchorType == MoudPackets.DisplayAnchorType.FREE) {
            return;
        }

        ParentTransform parent = resolveAnchorParent();
        if (parent == null) {
            clearAnchor();
            return;
        }

        Vector3 localPos = anchorLocalPosition != null ? new Vector3(anchorLocalPosition) : Vector3.zero();
        Quaternion localRot = anchorLocalRotation != null ? new Quaternion(anchorLocalRotation) : Quaternion.identity();
        Vector3 localScale = anchorLocalScale != null ? new Vector3(anchorLocalScale) : Vector3.one();

        Vector3 translated = localPos;
        if (anchorLocalSpace) {
            translated = localPos.multiply(parent.scale());
            translated = parent.rotation().rotate(translated);
        }

        Vector3 worldPos = parent.position().add(translated);
        Quaternion worldRot = anchorInheritRotation ? parent.rotation().multiply(localRot).normalize() : localRot;
        Vector3 worldScale = anchorInheritScale ? parent.scale().multiply(localScale) : localScale;

        applyAnchoredWorldTransform(worldPos, worldRot, worldScale);
    }

    private void applyAnchoredWorldTransform(Vector3 worldPos, Quaternion worldRot, Vector3 worldScale) {
        if (worldPos == null || worldRot == null || worldScale == null) {
            return;
        }
        this.position = worldPos;
        this.rotation = worldRot;
        this.scale = worldScale;
        this.entity.teleport(new Pos(worldPos.x, worldPos.y, worldPos.z));
    }

    private ParentTransform resolveAnchorParent() {
        return switch (anchorType) {
            case BLOCK -> {
                if (anchorBlockX == null || anchorBlockY == null || anchorBlockZ == null) {
                    yield null;
                }
                Vector3 pos = new Vector3(anchorBlockX + 0.5f, anchorBlockY + 0.5f, anchorBlockZ + 0.5f);
                yield new ParentTransform(pos, Quaternion.identity(), Vector3.one());
            }
            case ENTITY -> {
                if (anchorEntityUuid == null) {
                    yield null;
                }
                net.minestom.server.entity.Player player = net.minestom.server.MinecraftServer.getConnectionManager()
                        .getOnlinePlayerByUuid(anchorEntityUuid);
                if (player == null) {
                    yield null;
                }
                Pos pos = player.getPosition();
                float pitch = anchorIncludePitch ? pos.pitch() : 0.0f;
                Quaternion rot = Quaternion.fromEuler(pitch, pos.yaw(), 0.0f);
                yield new ParentTransform(new Vector3(pos.x(), pos.y(), pos.z()), rot, Vector3.one());
            }
            case MODEL -> {
                if (anchorModelId == null || anchorModelId == id) {
                    yield null;
                }
                ModelProxy parent = ModelManager.getInstance().getById(anchorModelId);
                if (parent == null) {
                    yield null;
                }
                Vector3 parentPos = parent.getPosition() != null ? new Vector3(parent.getPosition()) : Vector3.zero();
                Quaternion parentRot = parent.getRotation() != null ? new Quaternion(parent.getRotation()) : Quaternion.identity();
                Vector3 parentScale = parent.getScale() != null ? new Vector3(parent.getScale()) : Vector3.one();
                yield new ParentTransform(parentPos, parentRot, parentScale);
            }
            case FREE -> null;
        };
    }

    private record ParentTransform(Vector3 position, Quaternion rotation, Vector3 scale) {
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
        if (anchorType != MoudPackets.DisplayAnchorType.FREE) {
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
        if (anchorType != MoudPackets.DisplayAnchorType.FREE) {
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
        if (anchorType != MoudPackets.DisplayAnchorType.FREE) {
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
        if (anchorType != MoudPackets.DisplayAnchorType.FREE) {
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
        if (anchorType != MoudPackets.DisplayAnchorType.FREE) {
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
        this.anchorType = MoudPackets.DisplayAnchorType.BLOCK;
        this.anchorEntityUuid = null;
        this.anchorModelId = null;
        this.anchorBlockX = x;
        this.anchorBlockY = y;
        this.anchorBlockZ = z;
        this.anchorLocalPosition = localPosition != null ? new Vector3(localPosition) : Vector3.zero();
        this.anchorLocalRotation = localRotation != null ? new Quaternion(localRotation) : Quaternion.identity();
        this.anchorLocalScale = localScale != null ? new Vector3(localScale) : Vector3.one();
        this.anchorLocalSpace = localSpace;
        this.anchorInheritRotation = inheritRotation;
        this.anchorInheritScale = inheritScale;
        this.anchorIncludePitch = false;
        broadcastAnchorUpdate();
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
            this.anchorType = MoudPackets.DisplayAnchorType.ENTITY;
            this.anchorEntityUuid = parsed;
            this.anchorModelId = null;
            this.anchorBlockX = null;
            this.anchorBlockY = null;
            this.anchorBlockZ = null;
            this.anchorLocalPosition = localPosition != null ? new Vector3(localPosition) : Vector3.zero();
            this.anchorLocalRotation = localRotation != null ? new Quaternion(localRotation) : Quaternion.identity();
            this.anchorLocalScale = localScale != null ? new Vector3(localScale) : Vector3.one();
            this.anchorLocalSpace = localSpace;
            this.anchorInheritRotation = inheritRotation;
            this.anchorInheritScale = inheritScale;
            this.anchorIncludePitch = includePitch;
            broadcastAnchorUpdate();
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
        this.anchorType = MoudPackets.DisplayAnchorType.MODEL;
        this.anchorEntityUuid = null;
        this.anchorModelId = model.getId();
        this.anchorBlockX = null;
        this.anchorBlockY = null;
        this.anchorBlockZ = null;
        this.anchorLocalPosition = localPosition != null ? new Vector3(localPosition) : Vector3.zero();
        this.anchorLocalRotation = localRotation != null ? new Quaternion(localRotation) : Quaternion.identity();
        this.anchorLocalScale = localScale != null ? new Vector3(localScale) : Vector3.one();
        this.anchorLocalSpace = localSpace;
        this.anchorInheritRotation = inheritRotation;
        this.anchorInheritScale = inheritScale;
        this.anchorIncludePitch = false;
        broadcastAnchorUpdate();
        updateAnchorTracking();
    }

    @HostAccess.Export
    public void clearAnchor() {
        this.anchorType = MoudPackets.DisplayAnchorType.FREE;
        this.anchorEntityUuid = null;
        this.anchorModelId = null;
        this.anchorBlockX = null;
        this.anchorBlockY = null;
        this.anchorBlockZ = null;
        this.anchorLocalPosition = Vector3.zero();
        this.anchorLocalRotation = Quaternion.identity();
        this.anchorLocalScale = Vector3.one();
        this.anchorLocalSpace = true;
        this.anchorInheritRotation = true;
        this.anchorInheritScale = true;
        this.anchorIncludePitch = false;
        broadcastAnchorUpdate();
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
                boxes, position, rotation, scale
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
