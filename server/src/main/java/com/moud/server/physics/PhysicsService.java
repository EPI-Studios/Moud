package com.moud.server.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.MeshShapeSettings;
import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JobSystem;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilter;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.TempAllocator;
import com.github.stephengold.joltjni.TempAllocatorMalloc;
import com.github.stephengold.joltjni.Triangle;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.MutableCompoundShapeSettings;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.enumerate.EPhysicsUpdateError;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.server.entity.ModelManager;
import com.moud.server.instance.InstanceManager;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.physics.mesh.ChunkMesher;
import com.moud.server.physics.mesh.ModelCollisionLibrary;
import com.moud.server.physics.mesh.ModelCollisionLibrary.MeshData;
import com.moud.server.proxy.ModelProxy;
import com.moud.server.proxy.ModelProxyBootstrap;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.instance.InstanceChunkLoadEvent;
import net.minestom.server.event.instance.InstanceChunkUnloadEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PhysicsService {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            PhysicsService.class,
            LogContext.builder().put("subsystem", "physics").build()
    );


    public static final int LAYER_DYNAMIC = 0;
    public static final int LAYER_STATIC = 1;

    private static final class Holder {
        private static final PhysicsService INSTANCE = new PhysicsService();
    }

    public static PhysicsService getInstance() {
        return Holder.INSTANCE;
    }

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Moud-Physics");
        t.setDaemon(true);
        return t;
    });

    private static final double PLAYER_VELOCITY_THRESHOLD = 0.01;
    private static final float PLAYER_PUSH_STRENGTH = 0.35f;
    private PhysicsSystem physicsSystem;
    private TempAllocator tempAllocator;
    private JobSystem jobSystem;
    private volatile boolean initialized;
    private final ConcurrentHashMap<Long, PhysicsObject> physicsObjects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> chunkBodies = new ConcurrentHashMap<>();
    private volatile float lastDeltaSeconds = 0f;

    private PhysicsService() {
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        com.moud.server.proxy.ModelProxyBootstrap.ensureAssetsReady();
        LOGGER.info("Initializing physics service");
        PhysicsNativeLoader.loadLibrary();

        JoltPhysicsObject.startCleaner();
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        if (!Jolt.newFactory()) {
            throw new IllegalStateException("Failed to initialize Jolt factory");
        }
        Jolt.registerTypes();

        physicsSystem = new PhysicsSystem();

        ObjectLayerPairFilterTable layerFilter = new ObjectLayerPairFilterTable(2);
        layerFilter.enableCollision(LAYER_DYNAMIC, LAYER_DYNAMIC);
        layerFilter.enableCollision(LAYER_DYNAMIC, LAYER_STATIC);
        layerFilter.disableCollision(LAYER_STATIC, LAYER_STATIC);

        var broadPhaseTable = new com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable(2, 1);
        broadPhaseTable.mapObjectToBroadPhaseLayer(LAYER_DYNAMIC, 0);
        broadPhaseTable.mapObjectToBroadPhaseLayer(LAYER_STATIC, 0);

        ObjectVsBroadPhaseLayerFilter layerVsBroadPhase = new ObjectVsBroadPhaseLayerFilterTable(
                broadPhaseTable,
                1,
                layerFilter,
                2
        );

        int maxBodies = 10_000;
        int numBodyMutexes = 0;
        int maxBodyPairs = 65_536;
        int maxContacts = 20_480;
        physicsSystem.init(
                maxBodies,
                numBodyMutexes,
                maxBodyPairs,
                maxContacts,
                broadPhaseTable,
                layerVsBroadPhase,
                layerFilter
        );
        physicsSystem.optimizeBroadPhase();

        hookChunkEvents();
        primeInitialChunks();

        tempAllocator = new TempAllocatorMalloc();
        jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

        int physicsFps = Integer.parseInt(System.getProperty("moud.physics.fps", "60"));
        long intervalMs = Math.max(1, 1000 / physicsFps);
        executor.scheduleAtFixedRate(() -> stepSimulation(intervalMs / 1000f), 0L, intervalMs, TimeUnit.MILLISECONDS);

        initialized = true;
        LOGGER.info(LogContext.builder()
                .put("fps", physicsFps)
                .build(), "Physics service ready at {} FPS", physicsFps);
    }

    private void stepSimulation(float deltaSeconds) {
        lastDeltaSeconds = deltaSeconds;
        if (physicsSystem == null) {
            return;
        }
        try {
            applyConstraints(deltaSeconds);
            int steps = 1;
            int code = physicsSystem.update(deltaSeconds, steps, tempAllocator, jobSystem);
            if (code != EPhysicsUpdateError.None) {
                LOGGER.warn(LogContext.builder().put("code", code).build(), "Physics update error {}", code);
            }
            physicsObjects.values().forEach(PhysicsObject::syncVisual);
        } catch (Throwable t) {
            LOGGER.error("Physics tick failed", t);
        }
    }

    private void applyConstraints(float deltaSeconds) {
        physicsObjects.values().forEach(obj -> obj.applyConstraints(deltaSeconds));
    }

    public BodyInterface getBodyInterface() {
        return requireBodyInterface();
    }

    public void detachModel(ModelProxy model) {
        PhysicsObject obj = physicsObjects.remove(model.getId());
        if (obj != null) {
            BodyInterface bi = getBodyInterface();
            bi.removeBody(obj.body.getId());
            bi.destroyBody(obj.body.getId());
        }
    }

    public void handleModelManualTransform(ModelProxy model, Vector3 position, Quaternion rotation) {
        PhysicsObject obj = physicsObjects.get(model.getId());
        if (obj != null) {
            obj.applyManualTransform(position, rotation);
        }
    }

    public int applyExplosion(Vector3 center, double radius, double strength, double verticalBoost) {
        if (center == null || radius <= 0 || strength == 0 || physicsObjects.isEmpty()) {
            return 0;
        }
        int affected = 0;
        for (PhysicsObject obj : physicsObjects.values()) {
            if (obj.applyExplosion(center, radius, strength, verticalBoost)) {
                affected++;
            }
        }
        return affected;
    }

    public void handlePlayerInteraction(Player player) {
        if (player == null || physicsObjects.isEmpty()) {
            return;
        }
        BoundingBox baseBox = player.getBoundingBox();
        if (baseBox == null || baseBox.width() <= 0 || baseBox.height() <= 0 || baseBox.depth() <= 0) {
            return;
        }
        BoundingBox playerBox = baseBox.withOffset(player.getPosition());
        Vec velocity = player.getVelocity();
        if (velocity == null) {
            return;
        }
        double speedSq = velocity.x() * velocity.x() + velocity.y() * velocity.y() + velocity.z() * velocity.z();
        if (speedSq < PLAYER_VELOCITY_THRESHOLD) {
            return;
        }
        for (PhysicsObject object : physicsObjects.values()) {
            object.pushIfIntersecting(playerBox, velocity);
        }
    }

    private ConstShape createConvexHullShape(ModelProxy model) {
        List<float[]> hulls = ModelCollisionLibrary.getConvexHulls(model.getModelPath());
        boolean hasHulls = hulls != null && !hulls.isEmpty();
        float[] baseVertices = hasHulls ? null : ModelCollisionLibrary.getVertices(model.getModelPath());
        if (!hasHulls && (baseVertices == null || baseVertices.length < 9)) {
            return null;
        }

        Vector3 scale = model.getScale() != null ? model.getScale() : Vector3.one();
        float sx = (float) Math.max(Math.abs(scale.x), 1e-3);
        float sy = (float) Math.max(Math.abs(scale.y), 1e-3);
        float sz = (float) Math.max(Math.abs(scale.z), 1e-3);

        try {
            if (hasHulls && hulls.size() > 1) {
                MutableCompoundShapeSettings compound = new MutableCompoundShapeSettings();
                for (float[] hull : hulls) {
                    if (hull == null || hull.length < 9) {
                        continue;
                    }
                    ConstShape shape = buildHullShape(hull, sx, sy, sz);
                    if (shape != null) {
                        compound.addShape(Vec3.sZero(), Quat.sIdentity(), shape);
                    }
                }
                ShapeResult result = compound.create();
                if (result.hasError()) {
                    LOGGER.warn("Failed to build compound hull for model {}: {}", model.getModelPath(), result.getError());
                    return null;
                }
                return result.get();
            }

            float[] hullVerts = hasHulls ? hulls.get(0) : baseVertices;
            return buildHullShape(hullVerts, sx, sy, sz);
        } catch (Exception e) {
            LOGGER.warn("Exception while creating convex hull for model {}", model.getModelPath(), e);
            return null;
        }
    }

    private ConstShape buildHullShape(float[] verts, float sx, float sy, float sz) {
        if (verts == null || verts.length < 9) {
            return null;
        }
        float[] scaled = new float[verts.length];
        for (int i = 0; i < verts.length; i += 3) {
            scaled[i] = verts[i] * sx;
            scaled[i + 1] = verts[i + 1] * sy;
            scaled[i + 2] = verts[i + 2] * sz;
        }
        ByteBuffer direct = ByteBuffer.allocateDirect(scaled.length * Float.BYTES).order(ByteOrder.nativeOrder());
        FloatBuffer buffer = direct.asFloatBuffer();
        buffer.put(scaled);
        buffer.flip();

        ConvexHullShapeSettings hullSettings = new ConvexHullShapeSettings(scaled.length / 3, buffer);
        ShapeResult result = hullSettings.create();
        if (result.hasError()) {
            return null;
        }
        return result.get();
    }

    private ConstShape createMeshShape(ModelProxy model) {
        MeshData mesh = ModelCollisionLibrary.getMesh(model.getModelPath());
        if (mesh == null || mesh.vertices() == null || mesh.indices() == null || mesh.indices().length < 3) {
            return null;
        }

        Vector3 scale = model.getScale() != null ? model.getScale() : Vector3.one();
        float sx = (float) Math.max(Math.abs(scale.x), 1e-3);
        float sy = (float) Math.max(Math.abs(scale.y), 1e-3);
        float sz = (float) Math.max(Math.abs(scale.z), 1e-3);

        float[] verts = mesh.vertices();
        int[] indices = mesh.indices();

        List<Triangle> triangles = new ArrayList<>(indices.length / 3);
        for (int i = 0; i < indices.length; i += 3) {
            int ia = indices[i] * 3;
            int ib = indices[i + 1] * 3;
            int ic = indices[i + 2] * 3;
            if (ia < 0 || ib < 0 || ic < 0 || ia + 2 >= verts.length || ib + 2 >= verts.length || ic + 2 >= verts.length) {
                continue;
            }
            Vec3 a = new Vec3(verts[ia] * sx, verts[ia + 1] * sy, verts[ia + 2] * sz);
            Vec3 b = new Vec3(verts[ib] * sx, verts[ib + 1] * sy, verts[ib + 2] * sz);
            Vec3 c = new Vec3(verts[ic] * sx, verts[ic + 1] * sy, verts[ic + 2] * sz);
            triangles.add(new Triangle(a, b, c));
        }

        if (triangles.isEmpty()) {
            return null;
        }

        MeshShapeSettings settings = new MeshShapeSettings(triangles);
        ShapeResult result = settings.create();
        if (result.hasError()) {
            LOGGER.warn("Failed to build mesh shape for model {}: {}", model.getModelPath(), result.getError());
            return null;
        }
        return result.get();
    }

    public void attachDynamicModel(ModelProxy model, Vector3 halfExtents, float mass, Vector3 initialVelocity, boolean allowPlayerPush) {
        Vector3 startPos = model.getPosition();

        ensureChunksLoadedForPosition(model, startPos);

        ConstShape collisionShape = switch (model.getCollisionMode()) {
            case STATIC_MESH -> createConvexHullShape(model);
            case CAPSULE -> null; // capsule not implemented :')
            case AUTO, CONVEX -> createConvexHullShape(model);
        };
        if (collisionShape == null) {
            LOGGER.debug(LogContext.builder()
                    .put("modelId", model.getId())
                    .put("modelPath", model.getModelPath())
                    .build(), "Failed to create collision shape for model {} - using box fallback", model.getModelPath());

            // Ensure minimum half-extents to avoid Jolt assertion failure
            float hx = Math.max((float) halfExtents.x, 0.051f);
            float hy = Math.max((float) halfExtents.y, 0.051f);
            float hz = Math.max((float) halfExtents.z, 0.051f);
            collisionShape = new BoxShape(hx, hy, hz);
        }
        BodyCreationSettings settings = new BodyCreationSettings()
                .setMotionType(EMotionType.Dynamic)
                .setObjectLayer(LAYER_DYNAMIC)
                .setShape(collisionShape)
                .setLinearDamping(0.1f)
                .setAngularDamping(0.1f)
                .setPosition(new RVec3(startPos.x, startPos.y, startPos.z));

        MassProperties massProperties = new MassProperties();
        massProperties.setMassAndInertiaOfSolidBox(new Vec3(halfExtents.x * 2, halfExtents.y * 2, halfExtents.z * 2), mass);
        settings.setOverrideMassProperties(EOverrideMassProperties.MassAndInertiaProvided);
        settings.setMassPropertiesOverride(massProperties);

        Body body = getBodyInterface().createBody(settings);
        getBodyInterface().addBody(body, EActivation.Activate);

        if (initialVelocity != null) {
            body.setLinearVelocity(new Vec3(initialVelocity.x, initialVelocity.y, initialVelocity.z));
        }

        physicsObjects.put(model.getId(), new PhysicsObject(this, model, body, allowPlayerPush));
    }

    private void ensureChunksLoadedForPosition(ModelProxy model, Vector3 position) {
        Entity entity = model.getEntity();
        if (entity == null) {
            return;
        }

        Instance instance = entity.getInstance();
        if (instance == null) {
            return;
        }

        int chunkX = (int) Math.floor(position.x / 16.0);
        int chunkZ = (int) Math.floor(position.z / 16.0);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int targetX = chunkX + dx;
                int targetZ = chunkZ + dz;

                Chunk chunk = instance.getChunk(targetX, targetZ);
                if (chunk != null) {

                    refreshChunk(chunk);
                } else {

                    instance.loadChunk(targetX, targetZ).thenAccept(loadedChunk -> {
                        if (loadedChunk != null) {
                            refreshChunk(loadedChunk);
                        }
                    });
                }
            }
        }
    }

    private void hookChunkEvents() {
        var handler = MinecraftServer.getGlobalEventHandler();
        handler.addListener(InstanceChunkLoadEvent.class, event -> {
            Chunk chunk = event.getChunk();
            if (chunk != null && shouldHandleChunk(chunk)) {
                refreshChunk(chunk);
            }
        });
        handler.addListener(InstanceChunkUnloadEvent.class, event -> {
            Chunk chunk = event.getChunk();
            if (chunk != null && shouldHandleChunk(chunk)) {
                removeChunk(chunk);
            }
        });
        handler.addListener(PlayerBlockPlaceEvent.class, event -> {
            Chunk chunk = event.getInstance().getChunkAt(event.getBlockPosition());
            if (chunk != null && shouldHandleChunk(chunk)) refreshChunk(chunk);
        });
        handler.addListener(PlayerBlockBreakEvent.class, event -> {
            Chunk chunk = event.getInstance().getChunkAt(event.getBlockPosition());
            if (chunk != null && shouldHandleChunk(chunk)) refreshChunk(chunk);
        });
        handler.addListener(PlayerMoveEvent.class, event -> handlePlayerInteraction(event.getPlayer()));
    }

    private boolean shouldHandleChunk(Chunk chunk) {
        return chunk != null && shouldHandleInstance(chunk.getInstance());
    }

    private void primeInitialChunks() {
        Instance instance = InstanceManager.getInstance().getDefaultInstance();
        if (instance == null) {
            return;
        }
        int radius = Integer.parseInt(System.getProperty("moud.physics.chunkRadius", "2"));
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int chunkX = x;
                int chunkZ = z;
                instance.loadChunk(chunkX, chunkZ).thenAccept(this::refreshChunk);
            }
        }
    }

    private void removeChunk(Chunk chunk) {
        if (chunk == null || !shouldHandleInstance(chunk.getInstance())) return;
        long index = CoordIndex.chunkIndex(chunk.getChunkX(), chunk.getChunkZ());
        Integer bodyId = chunkBodies.remove(index);
        if (bodyId != null) {
            BodyInterface bi = getBodyInterface();
            bi.removeBody(bodyId);
            bi.destroyBody(bodyId);
        }
    }

    public void refreshChunk(Chunk chunk) {
        if (chunk == null || !shouldHandleInstance(chunk.getInstance())) return;
        BodyInterface bi = getBodyInterface();
        long index = CoordIndex.chunkIndex(chunk.getChunkX(), chunk.getChunkZ());
        Integer previousBody = chunkBodies.get(index);
        if (previousBody != null) {
            bi.removeBody(previousBody);
            bi.destroyBody(previousBody);
        }
        boolean fullBlocksOnly = !isDefaultInstance(chunk.getInstance());
        BodyCreationSettings settings;
        try {
            settings = ChunkMesher.createChunk(chunk, fullBlocksOnly);
        } catch (Exception ex) {
            LOGGER.warn(LogContext.builder()
                            .put("chunkX", chunk.getChunkX())
                            .put("chunkZ", chunk.getChunkZ())
                            .put("fullBlocksOnly", fullBlocksOnly)
                            .build(),
                    "Failed to mesh chunk for physics; skipping this chunk", ex);
            chunkBodies.remove(index);
            return;
        }
        if (settings == null) {
            chunkBodies.remove(index);
            return;
        }
        Body body = bi.createBody(settings);
        bi.addBody(body, EActivation.DontActivate);
        chunkBodies.put(index, body.getId());
    }

    private boolean shouldHandleInstance(Instance instance) {
        if (instance == null) return false;
        Instance defaultInstance = InstanceManager.getInstance().getDefaultInstance();
        if (defaultInstance != null && instance == defaultInstance) {
            return true;
        }
        return Boolean.getBoolean("moud.physics.processImportedChunks");
    }

    private boolean isDefaultInstance(Instance instance) {
        Instance defaultInstance = InstanceManager.getInstance().getDefaultInstance();
        return defaultInstance != null && instance == defaultInstance;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public void attachFollow(ModelProxy model, java.util.UUID targetUuid, Vector3 offset, boolean kinematic) {
        PhysicsObject obj = physicsObjects.get(model.getId());
        if (obj != null) {
            obj.setFollowConstraint(targetUuid, offset, kinematic);
        }
    }

    public void attachSpring(ModelProxy model, Vector3 anchor, double stiffness, double damping, Double restLength) {
        PhysicsObject obj = physicsObjects.get(model.getId());
        if (obj != null) {
            obj.setSpringConstraint(anchor, stiffness, damping, restLength);
        }
    }

    public void clearConstraints(ModelProxy model) {
        PhysicsObject obj = physicsObjects.get(model.getId());
        if (obj != null) {
            obj.clearConstraints();
        }
    }

    public PhysicsState getState(ModelProxy model) {
        PhysicsObject obj = physicsObjects.get(model.getId());
        if (obj == null) {
            return null;
        }
        return obj.snapshotState();
    }

    public record PhysicsState(
            Vector3 linearVelocity,
            Vector3 angularVelocity,
            boolean active,
            boolean onGround,
            Vector3 lastImpulse,
            boolean hasFollowConstraint,
            boolean hasSpringConstraint
    ) {}

    private static final class PhysicsObject {
        private final PhysicsService service;
        private final ModelProxy model;
        private final Body body;
        private final boolean allowPlayerPush;
        private int lastChunkX;
        private int lastChunkZ;
        private boolean chunkInitialized;
        private java.util.UUID followEntityUuid;
        private Vector3 followOffset = Vector3.zero();
        private boolean followKinematic = false;
        private Vector3 springAnchor;
        private double springStiffness;
        private double springDamping;
        private Double springRestLength;
        private Vec3 lastImpulse = new Vec3(0, 0, 0);
        private Vec3 lastLinearVelocity = new Vec3(0, 0, 0);
        private Vec3 lastAngularVelocity = new Vec3(0, 0, 0);
        private Vector3 lastPosition = null;
        private boolean lastOnGround = false;

        private PhysicsObject(PhysicsService service, ModelProxy model, Body body, boolean allowPlayerPush) {
            this.service = service;
            this.model = model;
            this.body = body;
            this.allowPlayerPush = allowPlayerPush;
        }

        private void setFollowConstraint(java.util.UUID target, Vector3 offset, boolean kinematic) {
            this.followEntityUuid = target;
            this.followOffset = offset != null ? offset : Vector3.zero();
            this.followKinematic = kinematic;
        }

        private void setSpringConstraint(Vector3 anchor, double stiffness, double damping, Double restLength) {
            this.springAnchor = anchor;
            this.springStiffness = Math.max(0.0, stiffness);
            this.springDamping = Math.max(0.0, damping);
            this.springRestLength = restLength;
        }

        private void clearConstraints() {
            this.followEntityUuid = null;
            this.springAnchor = null;
            this.springRestLength = null;
            this.springStiffness = 0.0;
            this.springDamping = 0.0;
        }

        private PhysicsState snapshotState() {
            Vector3 lin = toVector3(lastLinearVelocity);
            Vector3 ang = toVector3(lastAngularVelocity);
            Vector3 impulse = toVector3(lastImpulse);
            return new PhysicsState(
                    lin,
                    ang,
                    body.isActive(),
                    lastOnGround,
                    impulse,
                    followEntityUuid != null,
                    springAnchor != null
            );
        }

        private void applyConstraints(float deltaSeconds) {
            BodyInterface bi = service.getBodyInterface();
            if (followEntityUuid != null) {
                Entity target = resolveFollowTarget(followEntityUuid);
                if (target != null) {
                    net.minestom.server.coordinate.Point p = target.getPosition();
                    RVec3 targetPos = new RVec3(
                            p.x() + followOffset.x,
                            p.y() + followOffset.y,
                            p.z() + followOffset.z
                    );
                    bi.setPositionAndRotation(body.getId(), targetPos, body.getRotation(), followKinematic ? EActivation.DontActivate : EActivation.Activate);
                    body.setLinearVelocity(new Vec3(0, 0, 0));
                }
            }

            if (springAnchor != null && springStiffness > 0) {
                RVec3 pos = body.getPosition();
                Vec3 vel = body.getLinearVelocity();
                Vec3 toAnchor = new Vec3(
                        (float) (springAnchor.x - ((Number) pos.getX()).doubleValue()),
                        (float) (springAnchor.y - ((Number) pos.getY()).doubleValue()),
                        (float) (springAnchor.z - ((Number) pos.getZ()).doubleValue())
                );
                float dist = toAnchor.length();
                if (dist > 1e-4f) {
                    Vec3 dir = toAnchor.normalized();
                    double rest = springRestLength != null ? springRestLength : 0.0;
                    double displacement = dist - rest;
                    double forceMag = displacement * springStiffness;
                    double damp = vel.dot(dir) * springDamping;
                    double total = (forceMag - damp) * deltaSeconds;
                    Vec3 impulse = new Vec3(
                            (float) (dir.getX() * total),
                            (float) (dir.getY() * total),
                            (float) (dir.getZ() * total)
                    );
                    body.addImpulse(impulse);
                    lastImpulse = impulse;
                }
            }
        }

        private Entity resolveFollowTarget(UUID uuid) {
            Player p = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
            if (p != null) {
                return p;
            }
            ModelProxy modelProxy = ModelManager.getInstance().getByEntityUuid(uuid);
            if (modelProxy != null) {
                return modelProxy.getEntity();
            }
            return null;
        }

        private void syncVisual() {
            if (model == null || body == null) {
                return;
            }
            RVec3 pos = body.getPosition();
            Quat rot = body.getRotation();
            model.syncPhysicsTransform(toVector3(pos), toQuaternion(rot));
            maybeEnsureChunks(pos);
            this.lastAngularVelocity = body.getAngularVelocity();
            this.lastLinearVelocity = body.getLinearVelocity();
            Vector3 currentPos = toVector3(pos);
            if (lastPosition != null) {
                boolean verticalStill = Math.abs(lastLinearVelocity.getY()) < 0.05;
                boolean notAscending = currentPos.y <= lastPosition.y + 0.05;
                this.lastOnGround = verticalStill && notAscending;
            }
            this.lastPosition = currentPos;
        }

        private void applyManualTransform(Vector3 position, Quaternion rotation) {
            BodyInterface bi = service.getBodyInterface();
            RVec3 targetPos = position != null ? new RVec3(position.x, position.y, position.z) : body.getPosition();

            Quat targetRot;
            if (rotation != null) {
                // Jolt requires normalized quaternions
                Quaternion norm = rotation.normalize();
                targetRot = new Quat(norm.x, norm.y, norm.z, norm.w);
            } else {
                targetRot = body.getRotation();
            }

            bi.setPositionAndRotation(body.getId(), targetPos, targetRot, EActivation.Activate);
        }

        private void pushIfIntersecting(BoundingBox playerBox, Vec playerVelocity) {
            if (!allowPlayerPush) {
                return;
            }
            BoundingBox modelBox = getWorldBoundingBox();
            if (modelBox == null) {
                return;
            }
            if (!boxesIntersect(playerBox, modelBox)) {
                return;
            }
            Vec3 impulse = new Vec3(
                    (float) (playerVelocity.x() * PLAYER_PUSH_STRENGTH),
                    (float) (playerVelocity.y() * PLAYER_PUSH_STRENGTH),
                    (float) (playerVelocity.z() * PLAYER_PUSH_STRENGTH)
            );
            body.addImpulse(impulse);
            body.resetSleepTimer();
            lastImpulse = impulse;
        }

        private boolean applyExplosion(Vector3 center, double radius, double strength, double verticalBoost) {
            if (center == null || radius <= 0 || strength == 0) {
                return false;
            }
            BodyInterface bi = service.getBodyInterface();
            RVec3 bodyPos = body.getPosition();
            double dx = ((Number) bodyPos.getX()).doubleValue() - center.x;
            double dy = ((Number) bodyPos.getY()).doubleValue() - center.y;
            double dz = ((Number) bodyPos.getZ()).doubleValue() - center.z;
            dy += verticalBoost;
            double distSq = dx * dx + dy * dy + dz * dz;
            double radiusSq = radius * radius;
            if (distSq > radiusSq) {
                return false;
            }
            double dist = Math.sqrt(Math.max(distSq, 1.0e-8));
            double falloff = 1.0 - (dist / radius);
            if (falloff <= 0) {
                return false;
            }
            double scale = (strength * falloff) / dist;
            Vec3 impulse = new Vec3(
                    (float) (dx * scale),
                    (float) (dy * scale),
                    (float) (dz * scale)
            );
            bi.activateBody(body.getId());
            body.addImpulse(impulse);
            body.resetSleepTimer();
            lastImpulse = impulse;
            return true;
        }

        private BoundingBox getWorldBoundingBox() {
            if (model == null) {
                return null;
            }
            Entity entity = model.getEntity();
            if (entity == null) {
                return null;
            }
            BoundingBox box = entity.getBoundingBox();
            if (box == null || box.width() <= 0 || box.height() <= 0 || box.depth() <= 0) {
                return null;
            }
            Pos pos = entity.getPosition();
            return box.withOffset(pos);
        }

        private static Vector3 toVector3(RVec3 vec) {
            if (vec == null) {
                return Vector3.zero();
            }
            double x = ((Number) vec.getX()).doubleValue();
            double y = ((Number) vec.getY()).doubleValue();
            double z = ((Number) vec.getZ()).doubleValue();
            return new Vector3(x, y, z);
        }

        private static Vector3 toVector3(Vec3 vec) {
            if (vec == null) {
                return Vector3.zero();
            }
            return new Vector3(vec.getX(), vec.getY(), vec.getZ());
        }

        private static Quaternion toQuaternion(Quat quat) {
            if (quat == null) {
                return Quaternion.identity();
            }
            return new Quaternion(quat.getX(), quat.getY(), quat.getZ(), quat.getW());
        }

        private void maybeEnsureChunks(RVec3 pos) {
            if (pos == null) {
                return;
            }
            int chunkX = (int) Math.floor(((Number) pos.getX()).doubleValue() / 16.0);
            int chunkZ = (int) Math.floor(((Number) pos.getZ()).doubleValue() / 16.0);
            if (!chunkInitialized || chunkX != lastChunkX || chunkZ != lastChunkZ) {
                service.ensureChunksLoadedForPosition(model, new Vector3(
                        ((Number) pos.getX()).doubleValue(),
                        ((Number) pos.getY()).doubleValue(),
                        ((Number) pos.getZ()).doubleValue()
                ));
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
                chunkInitialized = true;
            }
        }
    }

    private static boolean boxesIntersect(BoundingBox a, BoundingBox b) {
        return a.maxX() > b.minX() && a.minX() < b.maxX()
                && a.maxY() > b.minY() && a.minY() < b.maxY()
                && a.maxZ() > b.minZ() && a.minZ() < b.maxZ();
    }

    private BodyInterface requireBodyInterface() {
        if (physicsSystem == null) {
            throw new IllegalStateException("Physics service not initialized");
        }
        return physicsSystem.getBodyInterface();
    }



}