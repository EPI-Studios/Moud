package com.moud.server.movement;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EGroundState;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.primitives.PrimitiveType;
import com.moud.server.entity.ModelManager;
import com.moud.server.physics.mesh.ModelCollisionLibrary;
import com.moud.server.proxy.ModelProxy;
import com.moud.server.primitives.PrimitiveInstance;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JoltPredictionCollisionWorld {
    private static final Logger LOGGER = LoggerFactory.getLogger(JoltPredictionCollisionWorld.class);

    private static final int LAYER_STATIC = 0;
    private static final int LAYER_PLAYER = 1;

    private static final float DEFAULT_MAX_SLOPE_DEGREES = 50f;

    private static JoltPredictionCollisionWorld instance;

    public static synchronized JoltPredictionCollisionWorld getInstance() {
        if (instance == null) {
            instance = new JoltPredictionCollisionWorld();
        }
        return instance;
    }

    private PhysicsSystem physicsSystem;
    private TempAllocator tempAllocator;
    private ObjectLayerPairFilterTable pairFilter;
    private BroadPhaseLayerInterfaceTable bpInterface;
    private ObjectVsBroadPhaseLayerFilterTable bpFilter;
    private volatile boolean initialized = false;

    private final Map<Long, StaticMeshEntry> staticMeshes = new ConcurrentHashMap<>();
    private final Map<ChunkMeshKey, ChunkMeshEntry> chunkMeshes = new ConcurrentHashMap<>();
    private final Map<Long, PrimitiveMeshEntry> primitiveMeshes = new ConcurrentHashMap<>();
    private long syncTick = 0L;

    private JoltPredictionCollisionWorld() {
    }

    public synchronized void initializeIfNeeded() {
        if (initialized) {
            return;
        }

        pairFilter = new ObjectLayerPairFilterTable(2);
        pairFilter.enableCollision(LAYER_PLAYER, LAYER_STATIC);

        bpInterface = new BroadPhaseLayerInterfaceTable(2, 2);
        bpInterface.mapObjectToBroadPhaseLayer(LAYER_STATIC, 0);
        bpInterface.mapObjectToBroadPhaseLayer(LAYER_PLAYER, 1);

        bpFilter = new ObjectVsBroadPhaseLayerFilterTable(bpInterface, 2, pairFilter, 2);

        physicsSystem = new PhysicsSystem();
        physicsSystem.init(
                50_000,
                0,
                65_536,
                10_240,
                bpInterface,
                bpFilter,
                pairFilter
        );
        physicsSystem.setGravity(new Vec3(0, 0, 0));
        physicsSystem.optimizeBroadPhase();

        tempAllocator = new TempAllocatorMalloc();
        initialized = true;
        LOGGER.info("JoltPredictionCollisionWorld initialized");
    }

    public boolean isInitialized() {
        return initialized && physicsSystem != null && tempAllocator != null;
    }

    public CharacterVirtual createCharacter(float width, float height) {
        initializeIfNeeded();
        if (!isInitialized()) {
            throw new IllegalStateException("Prediction collision world is not initialized");
        }

        float safeWidth = width > 0 ? width : 0.6f;
        float safeHeight = height > 0 ? height : 1.8f;

        float radius = Math.max(0.01f, safeWidth / 2f);
        float cylinderHeight = Math.max(0.0f, safeHeight - 2f * radius);

        CapsuleShape capsule = new CapsuleShape(cylinderHeight / 2f, radius);

        CharacterVirtualSettings settings = new CharacterVirtualSettings();
        settings.setShape(capsule);
        settings.setMaxSlopeAngle((float) Math.toRadians(DEFAULT_MAX_SLOPE_DEGREES));
        settings.setMaxStrength(500f);
        settings.setPenetrationRecoverySpeed(8.0f);
        settings.setMass(70f);

        float innerRadius = radius * 0.9f;
        float innerCylinderHeight = cylinderHeight * 0.9f;
        CapsuleShape innerCapsule = new CapsuleShape(innerCylinderHeight / 2f, innerRadius);
        settings.setInnerBodyShape(innerCapsule);

        return new CharacterVirtual(
                settings,
                new RVec3(0, 100, 0),
                new Quat(),
                0,
                physicsSystem
        );
    }

    public synchronized void syncMeshesForInstance(Instance instance) {
        if (instance == null) {
            return;
        }
        initializeIfNeeded();
        if (!isInitialized()) {
            return;
        }

        syncTick++;
        long tick = syncTick;

        List<ModelProxy> models = new ArrayList<>(ModelManager.getInstance().getAllModels());
        for (ModelProxy model : models) {
            if (model == null) {
                continue;
            }
            if (!isMeshCollisionEnabled(model)) {
                continue;
            }
            Entity entity = model.getEntity();
            if (entity == null || entity.getInstance() != instance) {
                continue;
            }

            String modelPath = model.getModelPath();
            if (modelPath == null || modelPath.isBlank()) {
                continue;
            }

            Vector3 position = model.getPosition();
            if (position == null) {
                Pos pos = entity.getPosition();
                position = new Vector3(pos.x(), pos.y(), pos.z());
            }

            Quaternion rotation = model.getRotation() != null ? model.getRotation() : Quaternion.identity();
            Vector3 scale = model.getScale() != null ? model.getScale() : Vector3.one();

            upsertStaticMesh(model.getId(), instance, modelPath, position, rotation, scale, tick);
        }

        for (Map.Entry<Long, StaticMeshEntry> entry : staticMeshes.entrySet()) {
            StaticMeshEntry meshEntry = entry.getValue();
            if (meshEntry == null) {
                continue;
            }
            if (meshEntry.lastSeenTick == tick) {
                continue;
            }
            if (meshEntry.instance != instance) {
                continue;
            }
            removeStaticMesh(entry.getKey(), meshEntry);
        }
    }

    public synchronized void upsertChunkMesh(Instance instance, int chunkX, int chunkZ, float[] vertices, int[] indices) {
        if (instance == null) {
            return;
        }
        initializeIfNeeded();
        if (!isInitialized()) {
            return;
        }

        ChunkMeshKey key = new ChunkMeshKey(resolveInstanceId(instance), chunkX, chunkZ);
        if (vertices == null || vertices.length < 9 || indices == null || indices.length < 3) {
            removeChunkMesh(key, chunkMeshes.get(key));
            return;
        }

        int meshHash = hashMesh(vertices, indices);
        ChunkMeshEntry existing = chunkMeshes.get(key);
        if (existing == null || existing.body == null || existing.meshHash != meshHash) {
            removeChunkMesh(key, existing);
            Body body = createStaticMeshBody(vertices, indices, Vector3.zero(), Quaternion.identity(), Vector3.one(), "chunk(" + chunkX + "," + chunkZ + ")");
            if (body == null) {
                return;
            }
            chunkMeshes.put(key, new ChunkMeshEntry(instance, meshHash, body));
        }
    }

    public synchronized void removeChunkMesh(Instance instance, int chunkX, int chunkZ) {
        if (instance == null) {
            return;
        }
        ChunkMeshKey key = new ChunkMeshKey(resolveInstanceId(instance), chunkX, chunkZ);
        removeChunkMesh(key, chunkMeshes.get(key));
    }

    private void removeChunkMesh(ChunkMeshKey key, ChunkMeshEntry entry) {
        if (entry == null || key == null) {
            return;
        }
        chunkMeshes.remove(key, entry);
        if (entry.body == null || physicsSystem == null) {
            return;
        }
        try {
            BodyInterface bi = physicsSystem.getBodyInterface();
            bi.removeBody(entry.body.getId());
            bi.destroyBody(entry.body.getId());
        } catch (Throwable ignored) {
        }
    }

    public synchronized void upsertPrimitive(Instance instance, PrimitiveInstance primitive) {
        if (instance == null || primitive == null) {
            return;
        }
        initializeIfNeeded();
        if (!isInitialized()) {
            return;
        }

        long id = primitive.getId();
        PrimitiveType type = primitive.getType();
        if (type == null || type == PrimitiveType.LINE || type == PrimitiveType.LINE_STRIP) {
            removePrimitive(id);
            return;
        }

        Vector3 position = primitive.getPosition() != null ? primitive.getPosition() : Vector3.zero();
        Quaternion rotation = primitive.getRotation() != null ? primitive.getRotation() : Quaternion.identity();
        Vector3 scale = primitive.getScale() != null ? primitive.getScale() : Vector3.one();

        int meshSourceHash = hashMeshSource(primitive);

        PrimitiveMeshEntry existing = primitiveMeshes.get(id);
        if (existing == null
                || existing.body == null
                || existing.instance != instance
                || existing.type != type
                || !rotationEquals(existing.rotation, rotation)
                || !sameScale(existing.scale, scale)
                || existing.meshSourceHash != meshSourceHash) {
            removePrimitive(id, existing);
            Body body = createPrimitiveBody(type, position, rotation, scale, primitive);
            if (body == null) {
                return;
            }
            primitiveMeshes.put(id, new PrimitiveMeshEntry(instance, type, rotation, scale, meshSourceHash, body));
            return;
        }

        updatePrimitiveTransform(existing, position);
    }

    public synchronized void removePrimitive(long primitiveId) {
        removePrimitive(primitiveId, primitiveMeshes.get(primitiveId));
    }

    private void removePrimitive(long primitiveId, PrimitiveMeshEntry entry) {
        if (entry == null) {
            return;
        }
        primitiveMeshes.remove(primitiveId, entry);
        if (entry.body == null || physicsSystem == null) {
            return;
        }
        try {
            BodyInterface bi = physicsSystem.getBodyInterface();
            bi.removeBody(entry.body.getId());
            bi.destroyBody(entry.body.getId());
        } catch (Throwable ignored) {
        }
    }

    private void updatePrimitiveTransform(PrimitiveMeshEntry entry, Vector3 position) {
        if (entry == null || entry.body == null || physicsSystem == null) {
            return;
        }
        Vector3 resolved = position != null ? position : Vector3.zero();
        try {
            BodyInterface bi = physicsSystem.getBodyInterface();
            bi.setPositionAndRotation(
                    entry.body.getId(),
                    new RVec3(resolved.x, resolved.y, resolved.z),
                    new Quat(),
                    EActivation.DontActivate
            );
        } catch (Throwable t) {
            LOGGER.warn("Failed to update primitive transform {}", entry.body.getId(), t);
        }
    }

    private Body createPrimitiveBody(
            PrimitiveType type,
            Vector3 position,
            Quaternion rotation,
            Vector3 scale,
            PrimitiveInstance primitive
    ) {
        if (type == null || position == null) {
            return null;
        }

        try {
            List<PredictionPrimitiveGeometry.Triangle> source = PredictionPrimitiveGeometry.generateMesh(
                    type,
                    Vector3.zero(),
                    rotation,
                    scale,
                    primitive != null ? primitive.getVertices() : null,
                    primitive != null ? primitive.getIndices() : null
            );
            if (source == null || source.isEmpty()) {
                return null;
            }

            List<Triangle> triangles = new ArrayList<>(source.size());
            for (PredictionPrimitiveGeometry.Triangle tri : source) {
                if (tri == null || tri.v0() == null || tri.v1() == null || tri.v2() == null) {
                    continue;
                }
                Vector3 v0 = tri.v0();
                Vector3 v1 = tri.v1();
                Vector3 v2 = tri.v2();
                triangles.add(new Triangle(
                        new Vec3((float) v0.x, (float) v0.y, (float) v0.z),
                        new Vec3((float) v1.x, (float) v1.y, (float) v1.z),
                        new Vec3((float) v2.x, (float) v2.y, (float) v2.z)
                ));
            }

            if (triangles.isEmpty()) {
                return null;
            }

            MeshShapeSettings meshSettings = new MeshShapeSettings(triangles);
            ShapeResult shapeResult = meshSettings.create();
            if (shapeResult.hasError()) {
                LOGGER.warn("Failed to create primitive mesh shape for {}: {}", type, shapeResult.getError());
                return null;
            }

            BodyCreationSettings bodySettings = new BodyCreationSettings();
            bodySettings.setShape(shapeResult.get());
            bodySettings.setPosition(new RVec3(position.x, position.y, position.z));
            bodySettings.setRotation(new Quat());
            bodySettings.setMotionType(EMotionType.Static);
            bodySettings.setObjectLayer(LAYER_STATIC);

            BodyInterface bi = physicsSystem.getBodyInterface();
            Body body = bi.createBody(bodySettings);
            bi.addBody(body.getId(), EActivation.DontActivate);
            return body;
        } catch (Throwable t) {
            LOGGER.warn("Failed to create primitive body for {}", type, t);
            return null;
        }
    }

    private void upsertStaticMesh(
            long id,
            Instance instance,
            String modelPath,
            Vector3 position,
            Quaternion rotation,
            Vector3 scale,
            long tick
    ) {
        StaticMeshEntry entry = staticMeshes.get(id);
        if (entry == null
                || entry.body == null
                || entry.instance != instance
                || !modelPath.equals(entry.modelPath)
                || !sameScale(scale, entry.scale)
        ) {
            removeStaticMesh(id, entry);
            Body body = createStaticMeshBody(modelPath, position, rotation, scale);
            if (body == null) {
                return;
            }
            StaticMeshEntry newEntry = new StaticMeshEntry(instance, modelPath, scale, body);
            newEntry.lastSeenTick = tick;
            staticMeshes.put(id, newEntry);
            return;
        }

        entry.lastSeenTick = tick;
        updateStaticTransform(id, position, rotation);
    }

    private Body createStaticMeshBody(String modelPath, Vector3 position, Quaternion rotation, Vector3 scale) {
        ModelCollisionLibrary.MeshData meshData = ModelCollisionLibrary.getMesh(modelPath);
        if (meshData == null || meshData.vertices() == null || meshData.indices() == null) {
            return null;
        }
        return createStaticMeshBody(
                meshData.vertices(),
                meshData.indices(),
                position != null ? position : Vector3.zero(),
                rotation != null ? rotation : Quaternion.identity(),
                scale != null ? scale : Vector3.one(),
                modelPath
        );
    }

    private Body createStaticMeshBody(
            float[] vertices,
            int[] indices,
            Vector3 position,
            Quaternion rotation,
            Vector3 scale,
            String debugName
    ) {
        if (vertices == null || vertices.length < 9 || indices == null || indices.length < 3) {
            return null;
        }

        try {
            float sx = scale != null ? (float) scale.x : 1f;
            float sy = scale != null ? (float) scale.y : 1f;
            float sz = scale != null ? (float) scale.z : 1f;

            List<Triangle> triangles = new ArrayList<>(indices.length / 3);
            for (int i = 0; i + 2 < indices.length; i += 3) {
                int ia = indices[i] * 3;
                int ib = indices[i + 1] * 3;
                int ic = indices[i + 2] * 3;

                if (ia + 2 >= vertices.length || ib + 2 >= vertices.length || ic + 2 >= vertices.length) {
                    continue;
                }

                Vec3 a = new Vec3(vertices[ia] * sx, vertices[ia + 1] * sy, vertices[ia + 2] * sz);
                Vec3 b = new Vec3(vertices[ib] * sx, vertices[ib + 1] * sy, vertices[ib + 2] * sz);
                Vec3 c = new Vec3(vertices[ic] * sx, vertices[ic + 1] * sy, vertices[ic + 2] * sz);
                triangles.add(new Triangle(a, b, c));
            }

            if (triangles.isEmpty()) {
                return null;
            }

            MeshShapeSettings meshSettings = new MeshShapeSettings(triangles);
            ShapeResult shapeResult = meshSettings.create();
            if (shapeResult.hasError()) {
                LOGGER.warn("Failed to create mesh shape for {}: {}", debugName, shapeResult.getError());
                return null;
            }

            Quat joltRotation = rotation != null
                    ? new Quat((float) rotation.x, (float) rotation.y, (float) rotation.z, (float) rotation.w)
                    : new Quat();

            BodyCreationSettings bodySettings = new BodyCreationSettings();
            bodySettings.setShape(shapeResult.get());
            bodySettings.setPosition(new RVec3(
                    position != null ? position.x : 0,
                    position != null ? position.y : 0,
                    position != null ? position.z : 0
            ));
            bodySettings.setRotation(joltRotation);
            bodySettings.setMotionType(EMotionType.Static);
            bodySettings.setObjectLayer(LAYER_STATIC);

            BodyInterface bi = physicsSystem.getBodyInterface();
            Body body = bi.createBody(bodySettings);
            bi.addBody(body.getId(), EActivation.DontActivate);
            return body;
        } catch (Throwable t) {
            LOGGER.warn("Failed to create static mesh body for {}", debugName, t);
            return null;
        }
    }

    private void updateStaticTransform(long id, Vector3 position, Quaternion rotation) {
        StaticMeshEntry entry = staticMeshes.get(id);
        if (entry == null || entry.body == null || physicsSystem == null) {
            return;
        }
        try {
            BodyInterface bi = physicsSystem.getBodyInterface();
            RVec3 joltPos = new RVec3(
                    position != null ? position.x : 0,
                    position != null ? position.y : 0,
                    position != null ? position.z : 0
            );
            Quat joltRot = rotation != null
                    ? new Quat((float) rotation.x, (float) rotation.y, (float) rotation.z, (float) rotation.w)
                    : new Quat();
            bi.setPositionAndRotation(entry.body.getId(), joltPos, joltRot, EActivation.DontActivate);
        } catch (Throwable t) {
            LOGGER.warn("Failed to update static mesh transform {}", id, t);
        }
    }

    private void removeStaticMesh(long id, StaticMeshEntry entry) {
        if (entry == null) {
            return;
        }
        staticMeshes.remove(id, entry);
        if (entry.body == null || physicsSystem == null) {
            return;
        }
        try {
            BodyInterface bi = physicsSystem.getBodyInterface();
            bi.removeBody(entry.body.getId());
            bi.destroyBody(entry.body.getId());
        } catch (Throwable ignored) {
        }
    }

    public MoveResult moveCharacter(
            CharacterVirtual character,
            double feetX,
            double feetY,
            double feetZ,
            double desiredX,
            double desiredY,
            double desiredZ,
            float dtSeconds,
            float height,
            float stepHeight,
            boolean allowStep
    ) {
        if (character == null || !isInitialized() || dtSeconds <= 0.0f) {
            return new MoveResult(desiredX, desiredY, desiredZ, false, false);
        }

        float safeHeight = height > 0 ? height : 1.8f;
        float centerOffsetY = safeHeight / 2f;

        try {
            RVec3 centerPos = new RVec3(feetX, feetY + centerOffsetY, feetZ);
            character.setPosition(centerPos);

            Vec3 velocity = new Vec3(
                    (float) (desiredX / dtSeconds),
                    (float) (desiredY / dtSeconds),
                    (float) (desiredZ / dtSeconds)
            );
            character.setLinearVelocity(velocity);

            float effectiveStep = allowStep ? Math.max(0.0f, stepHeight) : 0.0f;
            float stickDown = allowStep ? Math.min(effectiveStep, 0.1f) : 0.0f;
            ExtendedUpdateSettings updateSettings = new ExtendedUpdateSettings();
            updateSettings.setStickToFloorStepDown(new Vec3(0, -stickDown, 0));
            updateSettings.setWalkStairsStepUp(new Vec3(0, effectiveStep, 0));

            character.extendedUpdate(
                    dtSeconds,
                    new Vec3(0, 0, 0),
                    updateSettings,
                    physicsSystem.getDefaultBroadPhaseLayerFilter(LAYER_PLAYER),
                    physicsSystem.getDefaultLayerFilter(LAYER_PLAYER),
                    new BodyFilter(),
                    new ShapeFilter(),
                    tempAllocator
            );

            RVec3 newCenterPos = character.getPosition();
            double dx = newCenterPos.xx() - centerPos.xx();
            double dy = newCenterPos.yy() - centerPos.yy();
            double dz = newCenterPos.zz() - centerPos.zz();

            boolean onGround = character.getGroundState() == EGroundState.OnGround;
            boolean collidingHorizontally = axisBlocked(desiredX, dx) || axisBlocked(desiredZ, dz);

            return new MoveResult(dx, dy, dz, onGround, collidingHorizontally);
        } catch (Throwable t) {
            LOGGER.warn("Jolt moveCharacter failed", t);
            return new MoveResult(desiredX, desiredY, desiredZ, false, false);
        }
    }

    public boolean probeGround(
            CharacterVirtual character,
            double feetX,
            double feetY,
            double feetZ,
            float dtSeconds,
            float height,
            float stepDownDistance
    ) {
        if (character == null || !isInitialized() || dtSeconds <= 0.0f) {
            return false;
        }

        float safeHeight = height > 0 ? height : 1.8f;
        float centerOffsetY = safeHeight / 2f;

        try {
            RVec3 centerPos = new RVec3(feetX, feetY + centerOffsetY, feetZ);
            character.setPosition(centerPos);
            character.setLinearVelocity(new Vec3(0, 0, 0));

            float stepDown = Math.max(0.0f, stepDownDistance);
            ExtendedUpdateSettings updateSettings = new ExtendedUpdateSettings();
            updateSettings.setStickToFloorStepDown(new Vec3(0, -stepDown, 0));
            updateSettings.setWalkStairsStepUp(new Vec3(0, 0, 0));

            character.extendedUpdate(
                    dtSeconds,
                    new Vec3(0, 0, 0),
                    updateSettings,
                    physicsSystem.getDefaultBroadPhaseLayerFilter(LAYER_PLAYER),
                    physicsSystem.getDefaultLayerFilter(LAYER_PLAYER),
                    new BodyFilter(),
                    new ShapeFilter(),
                    tempAllocator
            );

            return character.getGroundState() == EGroundState.OnGround;
        } catch (Throwable t) {
            LOGGER.warn("Jolt probeGround failed", t);
            return false;
        }
    }

    private static boolean axisBlocked(double requested, double allowed) {
        if (Math.abs(requested) <= 1e-9) {
            return false;
        }
        double diff = allowed - requested;
        if (Math.abs(diff) <= 1e-6) {
            return false;
        }
        if (allowed * requested < -1e-9) {
            return true;
        }
        return Math.signum(allowed) == Math.signum(requested) && Math.abs(allowed) < Math.abs(requested) - 1e-6;
    }

    private static boolean isMeshCollisionEnabled(ModelProxy model) {
        if (model == null) {
            return false;
        }
        var mode = model.getWireCollisionMode();
        if (mode == null || mode == com.moud.network.MoudPackets.CollisionMode.BOX) {
            return false;
        }
        if (model.getCompressedVertices() == null || model.getCompressedIndices() == null) {
            model.ensureCollisionPayload();
        }
        return model.getCompressedVertices() != null && model.getCompressedIndices() != null;
    }

    private static boolean sameScale(Vector3 a, Vector3 b) {
        Vector3 sa = a != null ? a : Vector3.one();
        Vector3 sb = b != null ? b : Vector3.one();
        return Math.abs(sa.x - sb.x) <= 1e-6
                && Math.abs(sa.y - sb.y) <= 1e-6
                && Math.abs(sa.z - sb.z) <= 1e-6;
    }

    private static boolean rotationEquals(Quaternion a, Quaternion b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        double dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        dot = Math.abs(dot);
        return dot > 0.999999;
    }

    private static UUID resolveInstanceId(Instance instance) {
        UUID id = instance != null ? instance.getUniqueId() : null;
        return id != null ? id : ChunkMeshKey.FALLBACK_INSTANCE_ID;
    }

    private static int hashMesh(float[] vertices, int[] indices) {
        int h = 1;
        h = h * 31 + vertices.length;
        h = h * 31 + indices.length;
        h = h * 31 + sampleFloatHash(vertices, 0);
        h = h * 31 + sampleFloatHash(vertices, vertices.length / 2);
        h = h * 31 + sampleFloatHash(vertices, vertices.length - 1);
        h = h * 31 + sampleIntHash(indices, 0);
        h = h * 31 + sampleIntHash(indices, indices.length / 2);
        h = h * 31 + sampleIntHash(indices, indices.length - 1);
        return h;
    }

    private static int hashMeshSource(PrimitiveInstance primitive) {
        if (primitive == null || primitive.getType() != PrimitiveType.MESH) {
            return 0;
        }
        List<Vector3> verts = primitive.getVertices();
        List<Integer> inds = primitive.getIndices();
        int h = 1;
        if (verts != null) {
            h = h * 31 + verts.size();
            h = h * 31 + sampleVertexHash(verts, 0);
            h = h * 31 + sampleVertexHash(verts, verts.size() / 2);
            h = h * 31 + sampleVertexHash(verts, verts.size() - 1);
        }
        if (inds != null) {
            h = h * 31 + inds.size();
            h = h * 31 + sampleIndexHash(inds, 0);
            h = h * 31 + sampleIndexHash(inds, inds.size() / 2);
            h = h * 31 + sampleIndexHash(inds, inds.size() - 1);
        }
        return h;
    }

    private static int sampleFloatHash(float[] vertices, int idx) {
        if (vertices == null || vertices.length == 0) {
            return 0;
        }
        int clamped = idx;
        if (clamped < 0) {
            clamped = 0;
        } else if (clamped >= vertices.length) {
            clamped = vertices.length - 1;
        }
        return Float.floatToIntBits(vertices[clamped]);
    }

    private static int sampleIntHash(int[] indices, int idx) {
        if (indices == null || indices.length == 0) {
            return 0;
        }
        int clamped = idx;
        if (clamped < 0) {
            clamped = 0;
        } else if (clamped >= indices.length) {
            clamped = indices.length - 1;
        }
        return indices[clamped];
    }

    private static int sampleVertexHash(List<Vector3> vertices, int idx) {
        if (vertices == null || vertices.isEmpty()) {
            return 0;
        }
        int clamped = idx;
        if (clamped < 0) {
            clamped = 0;
        } else if (clamped >= vertices.size()) {
            clamped = vertices.size() - 1;
        }
        Vector3 v = vertices.get(clamped);
        if (v == null) {
            return 0;
        }
        int h = 1;
        h = h * 31 + Float.floatToIntBits((float) v.x);
        h = h * 31 + Float.floatToIntBits((float) v.y);
        h = h * 31 + Float.floatToIntBits((float) v.z);
        return h;
    }

    private static int sampleIndexHash(List<Integer> indices, int idx) {
        if (indices == null || indices.isEmpty()) {
            return 0;
        }
        int clamped = idx;
        if (clamped < 0) {
            clamped = 0;
        } else if (clamped >= indices.size()) {
            clamped = indices.size() - 1;
        }
        Integer v = indices.get(clamped);
        return v != null ? v : 0;
    }

    private static final class StaticMeshEntry {
        private final Instance instance;
        private final String modelPath;
        private final Vector3 scale;
        private final Body body;
        private volatile long lastSeenTick;

        private StaticMeshEntry(Instance instance, String modelPath, Vector3 scale, Body body) {
            this.instance = instance;
            this.modelPath = modelPath;
            this.scale = scale != null ? new Vector3(scale) : Vector3.one();
            this.body = body;
        }
    }

    private record ChunkMeshKey(UUID instanceId, int chunkX, int chunkZ) {
        private static final UUID FALLBACK_INSTANCE_ID = new UUID(0L, 0L);
    }

    private static final class ChunkMeshEntry {
        private final Instance instance;
        private final int meshHash;
        private final Body body;

        private ChunkMeshEntry(Instance instance, int meshHash, Body body) {
            this.instance = instance;
            this.meshHash = meshHash;
            this.body = body;
        }
    }

    private static final class PrimitiveMeshEntry {
        private final Instance instance;
        private final PrimitiveType type;
        private final Quaternion rotation;
        private final Vector3 scale;
        private final int meshSourceHash;
        private final Body body;

        private PrimitiveMeshEntry(
                Instance instance,
                PrimitiveType type,
                Quaternion rotation,
                Vector3 scale,
                int meshSourceHash,
                Body body
        ) {
            this.instance = instance;
            this.type = type;
            this.rotation = rotation != null ? new Quaternion(rotation) : Quaternion.identity();
            this.scale = scale != null ? new Vector3(scale) : Vector3.one();
            this.meshSourceHash = meshSourceHash;
            this.body = body;
        }
    }

    public record MoveResult(double dx, double dy, double dz, boolean onGround, boolean collidingHorizontally) {
    }
}
