package com.moud.server.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.CollisionGroup;
import com.github.stephengold.joltjni.Constraint;
import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.DistanceConstraintSettings;
import com.github.stephengold.joltjni.FixedConstraintSettings;
import com.github.stephengold.joltjni.HingeConstraintSettings;
import com.github.stephengold.joltjni.JobSystem;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.MeshShapeSettings;
import com.github.stephengold.joltjni.MutableCompoundShapeSettings;
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
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.enumerate.EPhysicsUpdateError;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.moud.api.collision.AABB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.server.entity.ModelManager;
import com.moud.server.instance.InstanceManager;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.physics.chunk.ChunkPhysicsManager;
import com.moud.server.physics.core.PhysicsThreadDispatcher;
import com.moud.server.physics.mesh.ModelCollisionLibrary;
import com.moud.server.physics.mesh.ModelCollisionLibrary.MeshData;
import com.moud.server.proxy.ModelProxy;
import com.moud.server.proxy.ModelProxyBootstrap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class PhysicsService {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            PhysicsService.class,
            LogContext.builder().put("subsystem", "physics").build()
    );

    public static final int LAYER_DYNAMIC = 0;
    public static final int LAYER_STATIC = 1;

    private static PhysicsService instance;

    public static synchronized void install(PhysicsService physicsService) {
        instance = Objects.requireNonNull(physicsService, "physicsService");
    }

    public static synchronized PhysicsService getInstance() {
        if (instance == null) {
            instance = new PhysicsService();
        }
        return instance;
    }

    private final PhysicsThreadDispatcher dispatcher = new PhysicsThreadDispatcher();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Moud-Physics");
        t.setDaemon(true);
        dispatcher.setPhysicsThread(t);
        return t;
    });

    private static final boolean FORCE_DEBUG = true;
    private PhysicsSystem physicsSystem;
    private ObjectLayerPairFilterTable objectLayerPairFilter;
    private BroadPhaseLayerInterfaceTable broadPhaseLayerInterface;
    private ObjectVsBroadPhaseLayerFilter objectVsBroadPhaseLayerFilter;
    private TempAllocator tempAllocator;
    private JobSystem jobSystem;
    private CollisionGroup defaultCollisionGroup;
    private volatile boolean initialized;
    private final ConcurrentHashMap<Long, PhysicsObject> physicsObjects = new ConcurrentHashMap<>();
    private volatile float lastDeltaSeconds = 0f;
    private final AtomicBoolean gravityFactorWarned = new AtomicBoolean(false);

    private final ChunkPhysicsManager chunkPhysics = new ChunkPhysicsManager(this);

    private final ConcurrentHashMap<Long, Constraint> constraints = new ConcurrentHashMap<>();
    private final AtomicLong nextConstraintId = new AtomicLong(1L);

    public PhysicsService() {
    }

    public void executeOnPhysicsThread(Runnable task) {
        dispatcher.execute(task);
    }

    public boolean isOnPhysicsThread() {
        return dispatcher.isOnPhysicsThread();
    }

    public synchronized void initialize() {
        if (initialized) return;
        ModelProxyBootstrap.ensureAssetsReady();
        LOGGER.info("Initializing physics service");
        PhysicsNativeLoader.loadLibrary();

        JoltPhysicsObject.startCleaner();
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        if (!Jolt.newFactory()) throw new IllegalStateException("Failed to initialize Jolt factory");
        Jolt.registerTypes();

        physicsSystem = new PhysicsSystem();

        // Setup Layers (Dynamic vs Static)
        objectLayerPairFilter = new ObjectLayerPairFilterTable(2);
        objectLayerPairFilter.enableCollision(LAYER_DYNAMIC, LAYER_DYNAMIC);
        objectLayerPairFilter.enableCollision(LAYER_DYNAMIC, LAYER_STATIC);
        objectLayerPairFilter.enableCollision(LAYER_STATIC, LAYER_DYNAMIC);
        objectLayerPairFilter.disableCollision(LAYER_STATIC, LAYER_STATIC);

        broadPhaseLayerInterface = new BroadPhaseLayerInterfaceTable(2, 2);
        broadPhaseLayerInterface.mapObjectToBroadPhaseLayer(LAYER_DYNAMIC, 0);
        broadPhaseLayerInterface.mapObjectToBroadPhaseLayer(LAYER_STATIC, 1);

        objectVsBroadPhaseLayerFilter = new ObjectVsBroadPhaseLayerFilterTable(
                broadPhaseLayerInterface, 2, objectLayerPairFilter, 2
        );

        physicsSystem.init(10_000, 0, 65_536, 20_480,
                broadPhaseLayerInterface, objectVsBroadPhaseLayerFilter, objectLayerPairFilter);
        physicsSystem.optimizeBroadPhase();

        defaultCollisionGroup = new CollisionGroup();
        chunkPhysics.registerEventHandlers();
        chunkPhysics.primeInitialChunks();

        tempAllocator = new TempAllocatorMalloc();
        jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

        int physicsFps = Integer.parseInt(System.getProperty("moud.physics.fps", "60"));
        long intervalMs = Math.max(1, 1000 / physicsFps);
        executor.scheduleAtFixedRate(() -> stepSimulation(intervalMs / 1000f), 0L, intervalMs, TimeUnit.MILLISECONDS);

        initialized = true;
        LOGGER.info("Physics service ready at {} FPS", physicsFps);
    }


    private void stepSimulation(float deltaSeconds) {
        lastDeltaSeconds = deltaSeconds;
        if (physicsSystem == null) return;
        try {
            dispatcher.drain(LOGGER);

            applyConstraints(deltaSeconds);

            int code = physicsSystem.update(deltaSeconds, 4, tempAllocator, jobSystem);
            if (code != EPhysicsUpdateError.None) {
                LOGGER.warn("Physics update error {}", code);
            }

            physicsObjects.values().forEach(PhysicsObject::syncVisual);
            PrimitivePhysicsManager.getInstance().syncDynamicPrimitives();
        } catch (Throwable t) {
            LOGGER.error("Physics tick failed", t);
        }
    }

    private void applyConstraints(float deltaSeconds) {
        physicsObjects.values().forEach(obj -> obj.applyConstraints(deltaSeconds));
    }

    public Body getModelPhysicsBody(long modelId) {
        PhysicsObject obj = physicsObjects.get(modelId);
        return obj != null ? obj.body : null;
    }

    public List<AABB> getPlayerBlockingColliders(Instance instance, AABB queryBox) {
        if (instance == null || queryBox == null || physicsObjects.isEmpty()) {
            return List.of();
        }
        List<AABB> colliders = new ArrayList<>();
        for (PhysicsObject object : physicsObjects.values()) {
            if (object == null || object.model == null || object.body == null) {
                continue;
            }
            Entity entity = object.model.getEntity();
            if (entity == null || entity.getInstance() != instance) {
                continue;
            }
            boolean blocksPlayer = object.body.getMotionType() == EMotionType.Static || !object.allowPlayerPush;
            if (!blocksPlayer) {
                continue;
            }
            BoundingBox box = object.getWorldBoundingBox();
            if (box == null) {
                continue;
            }
            AABB aabb = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
            if (!aabb.intersects(queryBox)) {
                continue;
            }
            colliders.add(aabb);
        }
        return colliders.isEmpty() ? List.of() : colliders;
    }

    public void applyPlayerPush(AABB playerBox, Vector3 playerVelocity, float strength) {
        if (playerBox == null || playerVelocity == null || strength == 0.0f || physicsObjects.isEmpty()) {
            return;
        }
        double speedSq = playerVelocity.x * playerVelocity.x + playerVelocity.y * playerVelocity.y + playerVelocity.z * playerVelocity.z;
        if (speedSq < 1.0e-8) {
            return;
        }

        AABB queryBox = playerBox.expanded(0.02, 0.02, 0.02);
        ArrayList<Long> toPush = null;
        for (Map.Entry<Long, PhysicsObject> entry : physicsObjects.entrySet()) {
            PhysicsObject object = entry.getValue();
            if (object == null || !object.allowPlayerPush) {
                continue;
            }
            BoundingBox modelBox = object.getWorldBoundingBox();
            if (modelBox == null) {
                continue;
            }
            AABB modelAabb = new AABB(modelBox.minX(), modelBox.minY(), modelBox.minZ(), modelBox.maxX(), modelBox.maxY(), modelBox.maxZ());
            if (!queryBox.intersects(modelAabb)) {
                continue;
            }
            if (toPush == null) {
                toPush = new ArrayList<>();
            }
            toPush.add(entry.getKey());
        }

        if (toPush == null || toPush.isEmpty()) {
            return;
        }

        float impX = playerVelocity.x * strength;
        float impY = playerVelocity.y * strength;
        float impZ = playerVelocity.z * strength;

        ArrayList<Long> finalToPush = toPush;
        executeOnPhysicsThread(() -> {
            Vec3 impulse = new Vec3(impX, impY, impZ);
            for (Long id : finalToPush) {
                PhysicsObject object = physicsObjects.get(id);
                if (object != null) {
                    object.applyPlayerPushImpulse(impulse);
                }
            }
        });
    }

    public long createDistanceConstraint(
            Body bodyA,
            Body bodyB,
            Vector3 pointA,
            Vector3 pointB,
            double minDistance,
            double maxDistance
    ) {
        if (physicsSystem == null) {
            throw new IllegalStateException("Physics service not initialized");
        }
        if (bodyA == null) {
            throw new IllegalArgumentException("bodyA cannot be null");
        }
        Body resolvedBodyB = bodyB != null ? bodyB : Body.sFixedToWorld();
        Vector3 resolvedPointA = pointA != null ? pointA : Vector3.zero();
        Vector3 resolvedPointB = pointB != null ? pointB : Vector3.zero();
        float min = (float) Math.max(0.0, minDistance);
        float max = (float) Math.max(min, maxDistance);

        DistanceConstraintSettings settings = new DistanceConstraintSettings();
        settings.setSpace(EConstraintSpace.WorldSpace);
        settings.setPoint1(resolvedPointA.x, resolvedPointA.y, resolvedPointA.z);
        settings.setPoint2(resolvedPointB.x, resolvedPointB.y, resolvedPointB.z);
        settings.setMinDistance(min);
        settings.setMaxDistance(max);
        settings.getLimitsSpringSettings().setFrequency(5.0f);
        settings.getLimitsSpringSettings().setDamping(0.5f);

        long id = nextConstraintId.getAndIncrement();
        executeOnPhysicsThread(() -> {
            if (physicsSystem == null) {
                return;
            }
            Constraint constraint = settings.create(bodyA, resolvedBodyB);
            physicsSystem.addConstraint(constraint);
            constraints.put(id, constraint);
        });
        return id;
    }

    // [Other constraint methods kept as-is]
    public long createHingeConstraint(
            Body bodyA,
            Body bodyB,
            Vector3 pivot,
            Vector3 hingeAxis,
            Vector3 normalAxis,
            Double limitsMin,
            Double limitsMax,
            Double maxFrictionTorque
    ) {
        if (physicsSystem == null) throw new IllegalStateException("Physics service not initialized");
        if (bodyA == null) throw new IllegalArgumentException("bodyA cannot be null");
        Body resolvedBodyB = bodyB != null ? bodyB : Body.sFixedToWorld();
        Vector3 resolvedPivot = pivot != null ? pivot : Vector3.zero();
        Vector3 axis = normalize(hingeAxis, new Vector3(0, 1, 0));
        Vector3 normal = normalize(normalAxis, pickPerpendicular(axis));

        HingeConstraintSettings settings = new HingeConstraintSettings();
        settings.setSpace(EConstraintSpace.WorldSpace);
        settings.setPoint1(new RVec3(resolvedPivot.x, resolvedPivot.y, resolvedPivot.z));
        settings.setPoint2(new RVec3(resolvedPivot.x, resolvedPivot.y, resolvedPivot.z));
        settings.setHingeAxis1(new Vec3((float) axis.x, (float) axis.y, (float) axis.z));
        settings.setHingeAxis2(new Vec3((float) axis.x, (float) axis.y, (float) axis.z));
        settings.setNormalAxis1(new Vec3((float) normal.x, (float) normal.y, (float) normal.z));
        settings.setNormalAxis2(new Vec3((float) normal.x, (float) normal.y, (float) normal.z));
        if (limitsMin != null) settings.setLimitsMin(limitsMin.floatValue());
        if (limitsMax != null) settings.setLimitsMax(limitsMax.floatValue());
        if (maxFrictionTorque != null) settings.setMaxFrictionTorque(maxFrictionTorque.floatValue());

        long id = nextConstraintId.getAndIncrement();
        executeOnPhysicsThread(() -> {
            if (physicsSystem == null) {
                return;
            }
            Constraint constraint = settings.create(bodyA, resolvedBodyB);
            physicsSystem.addConstraint(constraint);
            constraints.put(id, constraint);
        });
        return id;
    }

    public long createFixedConstraint(
            Body bodyA,
            Body bodyB,
            Vector3 pivot,
            Vector3 axisX,
            Vector3 axisY,
            boolean autoDetectPoint
    ) {
        if (physicsSystem == null) throw new IllegalStateException("Physics service not initialized");
        if (bodyA == null) throw new IllegalArgumentException("bodyA cannot be null");
        Body resolvedBodyB = bodyB != null ? bodyB : Body.sFixedToWorld();
        Vector3 resolvedPivot = pivot != null ? pivot : Vector3.zero();
        Vector3 resolvedAxisX = normalize(axisX, new Vector3(1, 0, 0));
        Vector3 resolvedAxisY = normalize(axisY, new Vector3(0, 1, 0));

        FixedConstraintSettings settings = new FixedConstraintSettings();
        settings.setSpace(EConstraintSpace.WorldSpace);
        settings.setAutoDetectPoint(autoDetectPoint);
        settings.setPoint1(new RVec3(resolvedPivot.x, resolvedPivot.y, resolvedPivot.z));
        settings.setPoint2(new RVec3(resolvedPivot.x, resolvedPivot.y, resolvedPivot.z));
        settings.setAxisX1(new Vec3((float) resolvedAxisX.x, (float) resolvedAxisX.y, (float) resolvedAxisX.z));
        settings.setAxisX2(new Vec3((float) resolvedAxisX.x, (float) resolvedAxisX.y, (float) resolvedAxisX.z));
        settings.setAxisY1(new Vec3((float) resolvedAxisY.x, (float) resolvedAxisY.y, (float) resolvedAxisY.z));
        settings.setAxisY2(new Vec3((float) resolvedAxisY.x, (float) resolvedAxisY.y, (float) resolvedAxisY.z));

        long id = nextConstraintId.getAndIncrement();
        executeOnPhysicsThread(() -> {
            if (physicsSystem == null) {
                return;
            }
            Constraint constraint = settings.create(bodyA, resolvedBodyB);
            physicsSystem.addConstraint(constraint);
            constraints.put(id, constraint);
        });
        return id;
    }

    public boolean removeConstraint(long constraintId) {
        if (constraintId <= 0) return false;
        Constraint constraint = constraints.remove(constraintId);
        if (constraint == null) {
            return false;
        }
        executeOnPhysicsThread(() -> {
            if (physicsSystem != null) {
                physicsSystem.removeConstraint(constraint);
            }
        });
        return true;
    }

    public void removeConstraintsForBody(int bodyId) {
        if (bodyId == 0 || constraints.isEmpty()) {
            return;
        }
        if (!isOnPhysicsThread()) {
            executeOnPhysicsThread(() -> removeConstraintsForBody(bodyId));
            return;
        }
        if (physicsSystem == null) {
            return;
        }
        for (Map.Entry<Long, Constraint> entry : constraints.entrySet()) {
            Constraint constraint = entry.getValue();
            if (!(constraint instanceof com.github.stephengold.joltjni.TwoBodyConstraint twoBodyConstraint)) continue;
            if (twoBodyConstraint.getBody1().getId() == bodyId || twoBodyConstraint.getBody2().getId() == bodyId) {
                long id = entry.getKey();
                constraints.remove(id);
                physicsSystem.removeConstraint(constraint);
            }
        }
    }

    private static Vector3 normalize(Vector3 vector, Vector3 fallback) {
        if (vector == null) return fallback != null ? fallback : Vector3.forward();
        double lenSq = vector.x * vector.x + vector.y * vector.y + vector.z * vector.z;
        if (lenSq <= 1.0e-12) return fallback != null ? fallback : Vector3.forward();
        double len = Math.sqrt(lenSq);
        return new Vector3(vector.x / len, vector.y / len, vector.z / len);
    }

    private static Vector3 pickPerpendicular(Vector3 axis) {
        Vector3 a = axis != null ? axis : new Vector3(0, 1, 0);
        Vector3 ref = Math.abs(a.y) < 0.9 ? new Vector3(0, 1, 0) : new Vector3(1, 0, 0);
        double nx = a.y * ref.z - a.z * ref.y;
        double ny = a.z * ref.x - a.x * ref.z;
        double nz = a.x * ref.y - a.y * ref.x;
        return new Vector3(nx, ny, nz);
    }

    public BodyInterface getBodyInterface() {
        return requireBodyInterface();
    }

    public PhysicsSystem getPhysicsSystem() {
        if (physicsSystem == null) {
            throw new IllegalStateException("Physics service not initialized");
        }
        return physicsSystem;
    }

    public void applyImpulse(Body body, Vector3 impulse) {
        if (body == null || impulse == null) {
            return;
        }
        executeOnPhysicsThread(() -> {
            if (body.getMotionType() == EMotionType.Static) {
                return;
            }
            BodyInterface bi = getBodyInterface();
            bi.activateBody(body.getId());
            Vec3 joltImpulse = new Vec3((float) impulse.x, (float) impulse.y, (float) impulse.z);
            body.addImpulse(joltImpulse);
            body.resetSleepTimer();
        });
    }

    public void setLinearVelocity(Body body, Vector3 velocity) {
        if (body == null || velocity == null) {
            return;
        }
        executeOnPhysicsThread(() -> {
            if (body.getMotionType() == EMotionType.Static) {
                return;
            }
            BodyInterface bi = getBodyInterface();
            bi.activateBody(body.getId());
            body.setLinearVelocity(new Vec3(velocity.x, velocity.y, velocity.z));
            body.resetSleepTimer();
        });
    }

    public void setGravityFactor(Body body, float factor) {
        if (body == null) {
            return;
        }
        float clamped = Math.max(0.0f, Math.min(10.0f, factor));
        executeOnPhysicsThread(() -> setGravityFactorNoLock(body, clamped));
    }

    void setGravityFactorNoLock(Body body, float factor) {
        if (body == null) {
            return;
        }
        if (body.getMotionType() == EMotionType.Static) {
            return;
        }

        boolean applied = false;
        try {
            BodyInterface bi = getBodyInterface();
            try {
                var method = bi.getClass().getMethod("setGravityFactor", int.class, float.class);
                method.invoke(bi, body.getId(), factor);
                applied = true;
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Throwable ignored) {
        }

        if (!applied) {
            try {
                var method = body.getClass().getMethod("setGravityFactor", float.class);
                method.invoke(body, factor);
                applied = true;
            } catch (Throwable ignored) {
            }
        }

        if (!applied && gravityFactorWarned.compareAndSet(false, true)) {
            LOGGER.warn("Gravity factor API not available in the current Jolt-JNI version; ignoring setGravityFactor()");
        }

        try {
            BodyInterface bi = getBodyInterface();
            bi.activateBody(body.getId());
            body.resetSleepTimer();
        } catch (Throwable ignored) {
        }
    }

    public void detachModel(ModelProxy model) {
        if (model == null) {
            return;
        }
        executeOnPhysicsThread(() -> {
            PhysicsObject obj = physicsObjects.remove(model.getId());
            if (obj == null) {
                return;
            }
            removeConstraintsForBody(obj.body.getId());
            BodyInterface bi = getBodyInterface();
            bi.removeBody(obj.body.getId());
            bi.destroyBody(obj.body.getId());
        });
    }

    public void handleModelManualTransform(ModelProxy model, Vector3 position, Quaternion rotation) {
        if (model == null) {
            return;
        }
        executeOnPhysicsThread(() -> {
            PhysicsObject obj = physicsObjects.get(model.getId());
            if (obj != null) {
                obj.applyManualTransform(position, rotation);
            }
        });
    }

    public int applyExplosion(Vector3 center, double radius, double strength, double verticalBoost) {
        if (center == null || radius <= 0 || strength == 0 || physicsObjects.isEmpty()) {
            return 0;
        }
        if (isOnPhysicsThread()) {
            return applyExplosionInternal(center, radius, strength, verticalBoost);
        }

        AtomicInteger affected = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        executeOnPhysicsThread(() -> {
            try {
                affected.set(applyExplosionInternal(center, radius, strength, verticalBoost));
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return affected.get();
    }

    private int applyExplosionInternal(Vector3 center, double radius, double strength, double verticalBoost) {
        int affected = 0;
        for (PhysicsObject obj : physicsObjects.values()) {
            if (obj.applyExplosion(center, radius, strength, verticalBoost)) {
                affected++;
            }
        }
        return affected;
    }

    private ConstShape createConvexHullShape(ModelProxy model) {
        List<float[]> hulls = ModelCollisionLibrary.getConvexHulls(model.getModelPath());
        boolean hasHulls = hulls != null && !hulls.isEmpty();
        float[] baseVertices = hasHulls ? null : ModelCollisionLibrary.getVertices(model.getModelPath());
        if (!hasHulls && (baseVertices == null || baseVertices.length < 9)) return null;
        Vector3 scale = model.getScale() != null ? model.getScale() : Vector3.one();
        float sx = (float) Math.max(Math.abs(scale.x), 1e-3);
        float sy = (float) Math.max(Math.abs(scale.y), 1e-3);
        float sz = (float) Math.max(Math.abs(scale.z), 1e-3);
        try {
            if (hasHulls && hulls.size() > 1) {
                MutableCompoundShapeSettings compound = new MutableCompoundShapeSettings();
                for (float[] hull : hulls) {
                    if (hull == null || hull.length < 9) continue;
                    ConstShape shape = buildHullShape(hull, sx, sy, sz);
                    if (shape != null) compound.addShape(Vec3.sZero(), Quat.sIdentity(), shape);
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
        if (verts == null || verts.length < 9) return null;
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
        if (result.hasError()) return null;
        return result.get();
    }

    private ConstShape createMeshShape(ModelProxy model) {
        MeshData mesh = ModelCollisionLibrary.getMesh(model.getModelPath());
        if (mesh == null || mesh.vertices() == null || mesh.indices() == null || mesh.indices().length < 3) return null;
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
            if (ia < 0 || ib < 0 || ic < 0 || ia + 2 >= verts.length || ib + 2 >= verts.length || ic + 2 >= verts.length) continue;
            Vec3 a = new Vec3(verts[ia] * sx, verts[ia + 1] * sy, verts[ia + 2] * sz);
            Vec3 b = new Vec3(verts[ib] * sx, verts[ib + 1] * sy, verts[ib + 2] * sz);
            Vec3 c = new Vec3(verts[ic] * sx, verts[ic + 1] * sy, verts[ic + 2] * sz);
            triangles.add(new Triangle(a, b, c));
        }
        if (triangles.isEmpty()) return null;
        MeshShapeSettings settings = new MeshShapeSettings(triangles);
        ShapeResult result = settings.create();
        if (result.hasError()) {
            LOGGER.warn("Failed to build mesh shape for model {}: {}", model.getModelPath(), result.getError());
            return null;
        }
        return result.get();
    }

    public void attachDynamicModel(ModelProxy model, Vector3 halfExtents, float mass, Vector3 initialVelocity, boolean allowPlayerPush) {
        if (model == null || halfExtents == null) {
            return;
        }
        Vector3 startPos = model.getPosition();
        ensureChunksLoadedForPosition(model, startPos);
        Entity entity = model.getEntity();
        Instance instance = entity != null ? entity.getInstance() : null;
        ModelProxy.CollisionMode mode = model.getCollisionMode();
        boolean staticBody = mode == ModelProxy.CollisionMode.STATIC_MESH;
        ConstShape collisionShape = null;
        if (mode == ModelProxy.CollisionMode.STATIC_MESH) {
            collisionShape = createMeshShape(model);
            if (collisionShape == null) {
                collisionShape = createConvexHullShape(model);
            }
        } else if (mode == ModelProxy.CollisionMode.AUTO || mode == ModelProxy.CollisionMode.CONVEX) {
            collisionShape = createConvexHullShape(model);
        }
        if (collisionShape == null) {
            float hx = Math.max((float) halfExtents.x, 0.051f);
            float hy = Math.max((float) halfExtents.y, 0.051f);
            float hz = Math.max((float) halfExtents.z, 0.051f);
            collisionShape = new BoxShape(hx, hy, hz);
        }
        BodyCreationSettings settings = new BodyCreationSettings()
                .setMotionType(staticBody ? EMotionType.Static : EMotionType.Dynamic)
                .setObjectLayer(staticBody ? LAYER_STATIC : LAYER_DYNAMIC)
                .setShape(collisionShape)
                .setPosition(new RVec3(startPos.x, startPos.y, startPos.z));
        Quaternion modelRotation = model.getRotation();
        if (modelRotation != null) {
            Quaternion norm = modelRotation.normalize();
            settings.setRotation(new Quat(norm.x, norm.y, norm.z, norm.w));
        }
        if (!staticBody) {
            settings.setLinearDamping(0.1f).setAngularDamping(0.1f);
        }
        settings.setCollisionGroup(collisionGroupForInstance(instance));
        if (!staticBody) {
            MassProperties massProperties = new MassProperties();
            massProperties.setMassAndInertiaOfSolidBox(new Vec3(halfExtents.x * 2, halfExtents.y * 2, halfExtents.z * 2), mass);
            settings.setOverrideMassProperties(EOverrideMassProperties.MassAndInertiaProvided);
            settings.setMassPropertiesOverride(massProperties);
        }

        executeOnPhysicsThread(() -> {
            BodyInterface bi = getBodyInterface();
            Body body = bi.createBody(settings);
            bi.addBody(body, staticBody ? EActivation.DontActivate : EActivation.Activate);
            if (!staticBody && initialVelocity != null) {
                body.setLinearVelocity(new Vec3(initialVelocity.x, initialVelocity.y, initialVelocity.z));
            }
            boolean canBePushed = !staticBody && allowPlayerPush;
            physicsObjects.put(model.getId(), new PhysicsObject(this, model, body, canBePushed));
        });
    }

    private void ensureChunksLoadedForPosition(ModelProxy model, Vector3 position) {
        chunkPhysics.ensureChunksLoadedForPosition(model, position);
    }


    public void onDefaultInstanceChanged(Instance instance) {
        if (!initialized || instance == null) {
            return;
        }
        chunkPhysics.onDefaultInstanceChanged(instance);
    }

    public boolean shouldHandleInstance(Instance instance) {
        if (instance == null) {
            return false;
        }
        Instance defaultInstance = InstanceManager.getInstance().getDefaultInstance();
        if (defaultInstance != null && instance == defaultInstance) {
            return true;
        }
        return Boolean.getBoolean("moud.physics.processImportedChunks");
    }

    public boolean isDefaultInstance(Instance instance) {
        Instance defaultInstance = InstanceManager.getInstance().getDefaultInstance();
        return defaultInstance != null && instance == defaultInstance;
    }

    public CollisionGroup collisionGroupForInstance(Instance instance) {
        CollisionGroup group = defaultCollisionGroup;
        if (group != null) {
            return group;
        }
        return new CollisionGroup();
    }

    public void requestChunkRefreshForBlock(Instance instance, int blockX, int blockZ) {
        if (!initialized || instance == null) {
            return;
        }
        if (!shouldHandleInstance(instance)) {
            return;
        }
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        chunkPhysics.requestChunkRefresh(instance, chunkX, chunkZ);
    }

    public void shutdown() {
        if (physicsSystem != null) {
            physicsSystem.removeAllConstraints();
        }
        constraints.clear();
        executor.shutdownNow();
    }

    public void attachFollow(ModelProxy model, java.util.UUID targetUuid, Vector3 offset, boolean kinematic) {
        if (model == null) {
            return;
        }
        executeOnPhysicsThread(() -> {
            PhysicsObject obj = physicsObjects.get(model.getId());
            if (obj != null) {
                obj.setFollowConstraint(targetUuid, offset, kinematic);
            }
        });
    }

    public void attachSpring(ModelProxy model, Vector3 anchor, double stiffness, double damping, Double restLength) {
        if (model == null) {
            return;
        }
        executeOnPhysicsThread(() -> {
            PhysicsObject obj = physicsObjects.get(model.getId());
            if (obj != null) {
                obj.setSpringConstraint(anchor, stiffness, damping, restLength);
            }
        });
    }

    public void clearConstraints(ModelProxy model) {
        if (model == null) {
            return;
        }
        executeOnPhysicsThread(() -> {
            PhysicsObject obj = physicsObjects.get(model.getId());
            if (obj != null) {
                obj.clearConstraints();
            }
        });
    }

    public PhysicsState getState(ModelProxy model) {
        PhysicsObject obj = physicsObjects.get(model.getId());
        if (obj == null) return null;
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
        private volatile java.util.UUID followEntityUuid;
        private Vector3 followOffset = Vector3.zero();
        private boolean followKinematic = false;
        private volatile Vector3 springAnchor;
        private double springStiffness;
        private double springDamping;
        private Double springRestLength;
        private volatile Vec3 lastImpulse = new Vec3(0, 0, 0);
        private volatile Vec3 lastLinearVelocity = new Vec3(0, 0, 0);
        private volatile Vec3 lastAngularVelocity = new Vec3(0, 0, 0);
        private volatile Vector3 lastPosition = null;
        private volatile boolean lastOnGround = false;
        private volatile boolean lastActive = false;

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
            return new PhysicsState(lin, ang, lastActive, lastOnGround, impulse, followEntityUuid != null, springAnchor != null);
        }

        private void applyConstraints(float deltaSeconds) {
            BodyInterface bi = service.getBodyInterface();
            boolean isStatic = body.getMotionType() == EMotionType.Static;
            if (followEntityUuid != null) {
                Entity target = resolveFollowTarget(followEntityUuid);
                if (target != null) {
                    net.minestom.server.coordinate.Point p = target.getPosition();
                    RVec3 targetPos = new RVec3(p.x() + followOffset.x, p.y() + followOffset.y, p.z() + followOffset.z);
                    EActivation activation = followKinematic || isStatic ? EActivation.DontActivate : EActivation.Activate;
                    bi.setPositionAndRotation(body.getId(), targetPos, body.getRotation(), activation);
                    if (!isStatic) {
                        body.setLinearVelocity(new Vec3(0, 0, 0));
                    }
                }
            }
            if (!isStatic && springAnchor != null && springStiffness > 0) {
                RVec3 pos = body.getPosition();
                Vec3 vel = body.getLinearVelocity();
                Vec3 toAnchor = new Vec3((float) (springAnchor.x - ((Number) pos.getX()).doubleValue()), (float) (springAnchor.y - ((Number) pos.getY()).doubleValue()), (float) (springAnchor.z - ((Number) pos.getZ()).doubleValue()));
                float dist = toAnchor.length();
                if (dist > 1e-4f) {
                    Vec3 dir = toAnchor.normalized();
                    double rest = springRestLength != null ? springRestLength : 0.0;
                    double displacement = dist - rest;
                    double forceMag = displacement * springStiffness;
                    double damp = vel.dot(dir) * springDamping;
                    double total = (forceMag - damp) * deltaSeconds;
                    Vec3 impulse = new Vec3((float) (dir.getX() * total), (float) (dir.getY() * total), (float) (dir.getZ() * total));
                    body.addImpulse(impulse);
                    lastImpulse = impulse;
                }
            }
        }

        private Entity resolveFollowTarget(UUID uuid) {
            Player p = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
            if (p != null) return p;
            ModelProxy modelProxy = ModelManager.getInstance().getByEntityUuid(uuid);
            if (modelProxy != null) return modelProxy.getEntity();
            return null;
        }

        private void syncVisual() {
            if (model == null || body == null) return;
            RVec3 pos = body.getPosition();
            Quat rot = body.getRotation();
            model.syncPhysicsTransform(toVector3(pos), toQuaternion(rot));
            maybeEnsureChunks(pos);
            this.lastAngularVelocity = body.getAngularVelocity();
            this.lastLinearVelocity = body.getLinearVelocity();
            this.lastActive = body.isActive();
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
                Quaternion norm = rotation.normalize();
                targetRot = new Quat(norm.x, norm.y, norm.z, norm.w);
            } else {
                targetRot = body.getRotation();
            }
            EActivation activation = body.getMotionType() == EMotionType.Static ? EActivation.DontActivate : EActivation.Activate;
            bi.setPositionAndRotation(body.getId(), targetPos, targetRot, activation);
        }

        private void applyPlayerPushImpulse(Vec3 impulse) {
            if (!allowPlayerPush || impulse == null || body.getMotionType() == EMotionType.Static) {
                return;
            }
            body.addImpulse(impulse);
            body.resetSleepTimer();
            lastImpulse = impulse;
        }

        private boolean applyExplosion(Vector3 center, double radius, double strength, double verticalBoost) {
            if (body.getMotionType() == EMotionType.Static) return false;
            if (center == null || radius <= 0 || strength == 0) return false;
            BodyInterface bi = service.getBodyInterface();
            RVec3 bodyPos = body.getPosition();
            double dx = ((Number) bodyPos.getX()).doubleValue() - center.x;
            double dy = ((Number) bodyPos.getY()).doubleValue() - center.y;
            double dz = ((Number) bodyPos.getZ()).doubleValue() - center.z;
            dy += verticalBoost;
            double distSq = dx * dx + dy * dy + dz * dz;
            double radiusSq = radius * radius;
            if (distSq > radiusSq) return false;
            double dist = Math.sqrt(Math.max(distSq, 1.0e-8));
            double falloff = 1.0 - (dist / radius);
            if (falloff <= 0) return false;
            double scale = (strength * falloff) / dist;
            Vec3 impulse = new Vec3((float) (dx * scale), (float) (dy * scale), (float) (dz * scale));
            bi.activateBody(body.getId());
            body.addImpulse(impulse);
            body.resetSleepTimer();
            lastImpulse = impulse;
            return true;
        }

        private BoundingBox getWorldBoundingBox() {
            if (model == null) return null;
            Entity entity = model.getEntity();
            if (entity == null) return null;
            BoundingBox box = entity.getBoundingBox();
            if (box == null || box.width() <= 0 || box.height() <= 0 || box.depth() <= 0) return null;
            Pos pos = entity.getPosition();
            return box.withOffset(pos);
        }

        private static Vector3 toVector3(RVec3 vec) {
            if (vec == null) return Vector3.zero();
            double x = ((Number) vec.getX()).doubleValue();
            double y = ((Number) vec.getY()).doubleValue();
            double z = ((Number) vec.getZ()).doubleValue();
            return new Vector3(x, y, z);
        }

        private static Vector3 toVector3(Vec3 vec) {
            if (vec == null) return Vector3.zero();
            return new Vector3(vec.getX(), vec.getY(), vec.getZ());
        }

        private static Quaternion toQuaternion(Quat quat) {
            if (quat == null) return Quaternion.identity();
            return new Quaternion(quat.getX(), quat.getY(), quat.getZ(), quat.getW());
        }

        private void maybeEnsureChunks(RVec3 pos) {
            if (pos == null) return;
            int chunkX = (int) Math.floor(((Number) pos.getX()).doubleValue() / 16.0);
            int chunkZ = (int) Math.floor(((Number) pos.getZ()).doubleValue() / 16.0);
            if (!chunkInitialized || chunkX != lastChunkX || chunkZ != lastChunkZ) {
                service.ensureChunksLoadedForPosition(model, new Vector3(((Number) pos.getX()).doubleValue(), ((Number) pos.getY()).doubleValue(), ((Number) pos.getZ()).doubleValue()));
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
                chunkInitialized = true;
            }
        }
    }

    private BodyInterface requireBodyInterface() {
        if (physicsSystem == null) throw new IllegalStateException("Physics service not initialized");
        return physicsSystem.getBodyInterface();
    }
}
