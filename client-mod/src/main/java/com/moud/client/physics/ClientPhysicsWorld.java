package com.moud.client.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPhysicsWorld {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPhysicsWorld.class);

    private static ClientPhysicsWorld instance;

    private static final int LAYER_STATIC = 0;
    private static final int LAYER_PLAYER = 1;

    private static final float MAX_SLOPE_DEGREES = 50f;

    private PhysicsSystem physicsSystem;
    private TempAllocator tempAllocator;
    private ObjectLayerPairFilterTable pairFilter;
    private BroadPhaseLayerInterfaceTable bpInterface;
    private ObjectVsBroadPhaseLayerFilterTable bpFilter;

    private CharacterVirtual playerCharacter;
    private CharacterVirtualSettings characterSettings;
    private float characterWidth = 0.6f;
    private float characterHeight = 1.8f;

    private final Map<Long, Body> staticBodies = new ConcurrentHashMap<>();
    private final Map<Long, Quaternionf> staticRotations = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;

    public static synchronized ClientPhysicsWorld getInstance() {
        if (instance == null) {
            instance = new ClientPhysicsWorld();
        }
        return instance;
    }

    private ClientPhysicsWorld() {
    }
    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        try {
            ClientPhysicsLoader.ensureLoaded();
        } catch (Exception e) {
            LOGGER.error("Failed to load Jolt native library, client physics disabled", e);
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
                50_000,  // max bodies
                0,       // num body mutexes (0 = auto)
                65_536,  // max body pairs
                10_240,  // max contact constraints
                bpInterface,
                bpFilter,
                pairFilter
        );

        physicsSystem.setGravity(new Vec3(0, 0, 0));

        tempAllocator = new TempAllocatorMalloc();
        createOrUpdatePlayerCharacter(0.6f, 1.8f);

        initialized = true;
        LOGGER.info("ClientPhysicsWorld initialized with Jolt Physics");
    }

    private void createOrUpdatePlayerCharacter(float width, float height) {
        if (physicsSystem == null) {
            return;
        }
        float safeWidth = width > 0 ? width : 0.6f;
        float safeHeight = height > 0 ? height : 1.8f;

        if (playerCharacter != null
                && Math.abs(safeWidth - characterWidth) < 1.0e-6f
                && Math.abs(safeHeight - characterHeight) < 1.0e-6f) {
            return;
        }

        characterWidth = safeWidth;
        characterHeight = safeHeight;

        float radius = Math.max(0.01f, safeWidth / 2f);
        float cylinderHeight = Math.max(0.0f, safeHeight - 2f * radius);

        CapsuleShape capsule = new CapsuleShape(cylinderHeight / 2f, radius);

        characterSettings = new CharacterVirtualSettings();
        characterSettings.setShape(capsule);
        characterSettings.setMaxSlopeAngle((float) Math.toRadians(MAX_SLOPE_DEGREES));
        characterSettings.setMaxStrength(500f);
        characterSettings.setPenetrationRecoverySpeed(8.0f);
        characterSettings.setMass(70f);

        float innerRadius = radius * 0.9f;
        float innerCylinderHeight = cylinderHeight * 0.9f;
        CapsuleShape innerCapsule = new CapsuleShape(innerCylinderHeight / 2f, innerRadius);
        characterSettings.setInnerBodyShape(innerCapsule);

        playerCharacter = new CharacterVirtual(
                characterSettings,
                new RVec3(0, 100, 0),
                new Quat(),
                0,
                physicsSystem
        );
    }
    public boolean isInitialized() {
        return initialized && physicsSystem != null && playerCharacter != null;
    }
    public boolean hasStaticBodies() {
        return !staticBodies.isEmpty();
    }
    public boolean hasStaticMesh(long id) {
        return staticBodies.containsKey(id);
    }

    public static ShapeRefC buildMeshShape(float[] vertices, int[] indices, Vector3f scale) {
        if (vertices == null || vertices.length < 9 || indices == null || indices.length < 3) {
            return null;
        }

        try {
            float sx = scale != null ? scale.x : 1f;
            float sy = scale != null ? scale.y : 1f;
            float sz = scale != null ? scale.z : 1f;

            List<Triangle> triangles = new ArrayList<>(indices.length / 3);
            for (int i = 0; i + 2 < indices.length; i += 3) {
                int ia = indices[i] * 3;
                int ib = indices[i + 1] * 3;
                int ic = indices[i + 2] * 3;

                if (ia < 0 || ib < 0 || ic < 0
                        || ia + 2 >= vertices.length
                        || ib + 2 >= vertices.length
                        || ic + 2 >= vertices.length) {
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
            ShapeResult result = meshSettings.create();
            if (result.hasError()) {
                LOGGER.warn("Failed to create mesh shape: {}", result.getError());
                return null;
            }
            return result.get();
        } catch (Exception e) {
            LOGGER.error("Error building mesh shape", e);
            return null;
        }
    }

    public void addStaticMesh(long id, float[] vertices, int[] indices,
                              Vector3f position, Quaternionf rotation, Vector3f scale) {
        if (!isInitialized()) {
            LOGGER.warn("Cannot add mesh {}: physics not initialized", id);
            return;
        }
        int triCount = indices != null ? indices.length / 3 : 0;
        LOGGER.info("Building Jolt mesh shape for model {} ({} vertices, {} triangles)",
                id, vertices != null ? vertices.length / 3 : 0, triCount);

        ShapeRefC shape = buildMeshShape(vertices, indices, scale);
        if (shape == null) {
            LOGGER.warn("Failed to build mesh shape for model {}: buildMeshShape returned null", id);
            return;
        }
        addStaticMeshShape(id, shape, position, rotation);
        LOGGER.info("Added Jolt static mesh body for model {} at ({}, {}, {})",
                id, position != null ? position.x : 0, position != null ? position.y : 0, position != null ? position.z : 0);
    }

    public synchronized void addStaticMeshShape(long id, ShapeRefC shape, Vector3f position, Quaternionf rotation) {
        if (!isInitialized()) {
            return;
        }
        if (shape == null) {
            return;
        }

        removeStaticMesh(id);

        try {
            Quat joltRotation = rotation != null
                    ? new Quat(rotation.x, rotation.y, rotation.z, rotation.w)
                    : new Quat();

            BodyCreationSettings bodySettings = new BodyCreationSettings();
            bodySettings.setShape(shape);
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

            staticBodies.put(id, body);
            staticRotations.put(id, rotation != null ? new Quaternionf(rotation) : new Quaternionf());
        } catch (Exception e) {
            LOGGER.error("Error adding static mesh {}", id, e);
        }
    }

    public synchronized void removeStaticMesh(long id) {
        Body body = staticBodies.remove(id);
        staticRotations.remove(id);
        if (body != null && physicsSystem != null) {
            try {
                BodyInterface bi = physicsSystem.getBodyInterface();
                int bodyId = body.getId();
                bi.removeBody(bodyId);
                bi.destroyBody(bodyId);
                LOGGER.debug("Removed static mesh {}", id);
            } catch (Exception e) {
                LOGGER.error("Error removing static mesh {}", id, e);
            }
        }
    }

    public synchronized void updateMeshTransform(long id, Vector3f position, Quaternionf rotation) {
        Body body = staticBodies.get(id);
        if (body != null && physicsSystem != null) {
            try {
                BodyInterface bi = physicsSystem.getBodyInterface();

                RVec3 joltPos = new RVec3(
                        position != null ? position.x : 0,
                        position != null ? position.y : 0,
                        position != null ? position.z : 0
                );

                Quaternionf effectiveRotation = rotation;
                if (effectiveRotation == null) {
                    effectiveRotation = staticRotations.get(id);
                } else {
                    staticRotations.put(id, new Quaternionf(effectiveRotation));
                }

                Quat joltRot = effectiveRotation != null
                        ? new Quat(effectiveRotation.x, effectiveRotation.y, effectiveRotation.z, effectiveRotation.w)
                        : new Quat();

                bi.setPositionAndRotation(body.getId(), joltPos, joltRot, EActivation.DontActivate);

            } catch (Exception e) {
                LOGGER.error("Error updating mesh transform {}", id, e);
            }
        }
    }

    public synchronized void clearAllMeshes() {
        if (physicsSystem != null) {
            BodyInterface bi = physicsSystem.getBodyInterface();
            for (Body body : staticBodies.values()) {
                try {
                    int bodyId = body.getId();
                    bi.removeBody(bodyId);
                    bi.destroyBody(bodyId);
                } catch (Exception ignored) {
                }
            }
        }
        staticBodies.clear();
        staticRotations.clear();
        LOGGER.info("Cleared all static meshes");
    }

    public synchronized Vector3f movePlayer(Vector3f currentPos, Vector3f desiredMovement, float deltaTime) {
        return movePlayer(currentPos, desiredMovement, deltaTime, characterWidth, characterHeight, 0.6f, true);
    }

    public synchronized Vector3f movePlayer(
            Vector3f currentPos,
            Vector3f desiredMovement,
            float deltaTime,
            float width,
            float height,
            float stepHeight,
            boolean allowStep
    ) {
        if (!isInitialized() || staticBodies.isEmpty()) {
            return desiredMovement;
        }

        try {
            createOrUpdatePlayerCharacter(width, height);
            float safeHeight = height > 0 ? height : 1.8f;
            float centerOffsetY = safeHeight / 2f;

            RVec3 centerPos = new RVec3(
                    currentPos.x,
                    currentPos.y + centerOffsetY,
                    currentPos.z
            );
            playerCharacter.setPosition(centerPos);

            Vec3 velocity = new Vec3(
                    desiredMovement.x / deltaTime,
                    desiredMovement.y / deltaTime,
                    desiredMovement.z / deltaTime
            );
            playerCharacter.setLinearVelocity(velocity);

            float effectiveStep = allowStep ? Math.max(0.0f, stepHeight) : 0.0f;
            float stickDown = allowStep ? Math.min(effectiveStep, 0.1f) : 0.0f;
            ExtendedUpdateSettings updateSettings = new ExtendedUpdateSettings();
            updateSettings.setStickToFloorStepDown(new Vec3(0, -stickDown, 0));
            updateSettings.setWalkStairsStepUp(new Vec3(0, effectiveStep, 0));

            playerCharacter.extendedUpdate(
                    deltaTime,
                    new Vec3(0, 0, 0),
                    updateSettings,
                    physicsSystem.getDefaultBroadPhaseLayerFilter(LAYER_PLAYER),
                    physicsSystem.getDefaultLayerFilter(LAYER_PLAYER),
                    new BodyFilter(),
                    new ShapeFilter(),
                    tempAllocator
            );
            RVec3 newCenterPos = playerCharacter.getPosition();

            return new Vector3f(
                    (float) (newCenterPos.xx() - centerPos.xx()),
                    (float) (newCenterPos.yy() - centerPos.yy()),
                    (float) (newCenterPos.zz() - centerPos.zz())
            );

        } catch (Exception e) {
            LOGGER.error("Error in player collision", e);
            return desiredMovement;
        }
    }

    public synchronized boolean isPlayerOnGround() {
        if (!isInitialized()) {
            return false;
        }
        return playerCharacter.getGroundState() == EGroundState.OnGround;
    }

    public synchronized Vector3f getPlayerGroundNormal() {
        if (!isInitialized()) {
            return new Vector3f(0, 1, 0);
        }
        Vec3 normal = playerCharacter.getGroundNormal();
        return new Vector3f(normal.getX(), normal.getY(), normal.getZ());
    }

    public synchronized Vector3f getPlayerGroundVelocity() {
        if (!isInitialized()) {
            return new Vector3f(0, 0, 0);
        }
        Vec3 vel = playerCharacter.getGroundVelocity();
        return new Vector3f(vel.getX(), vel.getY(), vel.getZ());
    }

    public synchronized void shutdown() {
        if (!initialized) {
            return;
        }

        clearAllMeshes();

        playerCharacter = null;
        characterSettings = null;
        physicsSystem = null;
        tempAllocator = null;
        pairFilter = null;
        bpInterface = null;
        bpFilter = null;

        initialized = false;
        LOGGER.info("ClientPhysicsWorld shutdown");
    }

    public synchronized void reset() {
        clearAllMeshes();
        if (playerCharacter != null) {
            playerCharacter.setPosition(new RVec3(0, 100, 0));
            playerCharacter.setLinearVelocity(new Vec3(0, 0, 0));
        }
        LOGGER.info("ClientPhysicsWorld reset");
    }
}
