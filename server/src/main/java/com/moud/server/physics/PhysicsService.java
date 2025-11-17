package com.moud.server.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BoxShape;
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
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.enumerate.EPhysicsUpdateError;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.server.instance.InstanceManager;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.physics.mesh.ChunkMesher;
import com.moud.server.physics.mesh.ModelCollisionLibrary;
import com.moud.server.proxy.ModelProxy;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
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

    private PhysicsService() {
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
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
        if (physicsSystem == null) {
            return;
        }
        try {
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
        float[] baseVertices = ModelCollisionLibrary.getVertices(model.getModelPath());
        if (baseVertices == null || baseVertices.length < 9) {
            return null;
        }

        Vector3 scale = model.getScale() != null ? model.getScale() : Vector3.one();
        float sx = (float) Math.max(Math.abs(scale.x), 1e-3);
        float sy = (float) Math.max(Math.abs(scale.y), 1e-3);
        float sz = (float) Math.max(Math.abs(scale.z), 1e-3);

        float[] scaled = new float[baseVertices.length];
        for (int i = 0; i < baseVertices.length; i += 3) {
            scaled[i] = baseVertices[i] * sx;
            scaled[i + 1] = baseVertices[i + 1] * sy;
            scaled[i + 2] = baseVertices[i + 2] * sz;
        }

        try {
            ByteBuffer direct = ByteBuffer.allocateDirect(scaled.length * Float.BYTES).order(ByteOrder.nativeOrder());
            FloatBuffer buffer = direct.asFloatBuffer();
            buffer.put(scaled);
            buffer.flip();

            ConvexHullShapeSettings hullSettings = new ConvexHullShapeSettings(baseVertices.length / 3, buffer);
            ShapeResult result = hullSettings.create();
            if (result.hasError()) {
                LOGGER.warn("Failed to build convex hull for model {}: {}", model.getModelPath(), result.getError());
                return null;
            }
            return result.get();
        } catch (Exception e) {
            LOGGER.warn("Exception while creating convex hull for model {}", model.getModelPath(), e);
            return null;
        }
    }

    public void attachDynamicModel(ModelProxy model, Vector3 halfExtents, float mass, Vector3 initialVelocity) {
        Vector3 startPos = model.getPosition();

        ensureChunksLoadedForPosition(model, startPos);

        ConstShape collisionShape = createConvexHullShape(model);
        if (collisionShape == null) {
            LOGGER.debug(LogContext.builder()
                    .put("modelId", model.getId())
                    .put("modelPath", model.getModelPath())
                    .build(), "Failed to create convex hull for model {} - using box fallback", model.getModelPath());
            collisionShape = new BoxShape(halfExtents.x, halfExtents.y, halfExtents.z);
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

        physicsObjects.put(model.getId(), new PhysicsObject(this, model, body));
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
            if (chunk != null) {
                refreshChunk(chunk);
            }
        });
        handler.addListener(InstanceChunkUnloadEvent.class, event -> {
            Chunk chunk = event.getChunk();
            if (chunk != null) {
                removeChunk(chunk);
            }
        });
        handler.addListener(PlayerBlockPlaceEvent.class, event -> {
            Chunk chunk = event.getInstance().getChunkAt(event.getBlockPosition());
            if (chunk != null) refreshChunk(chunk);
        });
        handler.addListener(PlayerBlockBreakEvent.class, event -> {
            Chunk chunk = event.getInstance().getChunkAt(event.getBlockPosition());
            if (chunk != null) refreshChunk(chunk);
        });
        handler.addListener(PlayerMoveEvent.class, event -> handlePlayerInteraction(event.getPlayer()));
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
        if (chunk == null) return;
        long index = CoordIndex.chunkIndex(chunk.getChunkX(), chunk.getChunkZ());
        Integer bodyId = chunkBodies.remove(index);
        if (bodyId != null) {
            BodyInterface bi = getBodyInterface();
            bi.removeBody(bodyId);
            bi.destroyBody(bodyId);
        }
    }

    public void refreshChunk(Chunk chunk) {
        if (chunk == null) return;
        BodyInterface bi = getBodyInterface();
        long index = CoordIndex.chunkIndex(chunk.getChunkX(), chunk.getChunkZ());
        Integer previousBody = chunkBodies.get(index);
        if (previousBody != null) {
            bi.removeBody(previousBody);
            bi.destroyBody(previousBody);
        }
        BodyCreationSettings settings = ChunkMesher.createChunk(chunk);
        if (settings == null) {
            chunkBodies.remove(index);
            return;
        }
        Body body = bi.createBody(settings);
        bi.addBody(body, EActivation.DontActivate);
        chunkBodies.put(index, body.getId());
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static final class PhysicsObject {
        private final PhysicsService service;
        private final ModelProxy model;
        private final Body body;

        private PhysicsObject(PhysicsService service, ModelProxy model, Body body) {
            this.service = service;
            this.model = model;
            this.body = body;
        }

        private void syncVisual() {
            if (model == null || body == null) {
                return;
            }
            RVec3 pos = body.getPosition();
            Quat rot = body.getRotation();
            model.syncPhysicsTransform(toVector3(pos), toQuaternion(rot));
        }

        private void applyManualTransform(Vector3 position, Quaternion rotation) {
            BodyInterface bi = service.getBodyInterface();
            RVec3 targetPos = position != null ? new RVec3(position.x, position.y, position.z) : body.getPosition();
            Quat targetRot = rotation != null ? new Quat(rotation.x, rotation.y, rotation.z, rotation.w) : body.getRotation();
            bi.setPositionAndRotation(body.getId(), targetPos, targetRot, EActivation.Activate);
        }

        private void pushIfIntersecting(BoundingBox playerBox, Vec playerVelocity) {
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

        private static Quaternion toQuaternion(Quat quat) {
            if (quat == null) {
                return Quaternion.identity();
            }
            return new Quaternion(quat.getX(), quat.getY(), quat.getZ(), quat.getW());
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