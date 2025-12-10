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
import java.util.List;
import java.util.Optional;
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
        this.position = position;
        this.entity.teleport(new Pos(position.x, position.y, position.z));
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.handleModelManualTransform(this, position, null);
        }
        broadcastUpdate();
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return position;
    }

    @HostAccess.Export
    public void setTransform(Vector3 position, Quaternion rotation, Vector3 scale) {
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
        this.rotation = rotation;
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.handleModelManualTransform(this, null, rotation);
        }
        broadcastUpdate();
    }

    @HostAccess.Export
    public void setRotationFromEuler(double pitch, double yaw, double roll) {
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
        this.scale = scale;
        broadcastUpdate();
    }

    @HostAccess.Export
    public Vector3 getScale() {
        return scale;
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

    public double getCollisionWidth() {
        return collisionBox != null ? collisionBox.width() : 0;
    }

    public double getCollisionHeight() {
        return collisionBox != null ? collisionBox.height() : 0;
    }

    public double getCollisionDepth() {
        return collisionBox != null ? collisionBox.depth() : 0;
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
