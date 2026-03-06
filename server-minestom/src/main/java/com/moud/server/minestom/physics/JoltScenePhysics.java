package com.moud.server.minestom.physics;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyFilter;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.ExtendedUpdateSettings;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeFilter;
import com.github.stephengold.joltjni.TempAllocatorImpl;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.CapsuleShape;
import com.github.stephengold.joltjni.CharacterVirtual;
import com.github.stephengold.joltjni.CharacterVirtualSettings;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EGroundState;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.moud.core.scene.Node;
import com.moud.core.scene.SceneTree;
import com.moud.server.minestom.engine.Engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public final class JoltScenePhysics implements AutoCloseable {
    private static final int OBJECT_LAYER_STATIC = 0;
    private static final int OBJECT_LAYER_MOVING = 1;
    private static final int BROADPHASE_LAYER_STATIC = 0;
    private static final int BROADPHASE_LAYER_MOVING = 1;

    private static final float GRAVITY = 30.0f;
    private static final float JUMP_VELOCITY = 10.0f;
    private static final float SPRINT_MULT = 1.5f;

    private static final float DEFAULT_CAPSULE_RADIUS = 0.3f;
    private static final float DEFAULT_CAPSULE_HALF_CYL = 0.6f;

    private final PhysicsSystem system = new PhysicsSystem();
    private final BodyInterface bodies;
    private final TempAllocatorImpl tempAllocator = new TempAllocatorImpl(16 * 1024 * 1024);
    private final com.github.stephengold.joltjni.JobSystemSingleThreaded jobs = new com.github.stephengold.joltjni.JobSystemSingleThreaded(1024);

    private final ExtendedUpdateSettings updateSettings = new ExtendedUpdateSettings();
    private final BodyFilter bodyFilter = new BodyFilter();
    private final ShapeFilter shapeFilter = new ShapeFilter();

    private long lastCsgRevision = Long.MIN_VALUE;
    private final ArrayList<Integer> staticBodyIds = new ArrayList<>();

    private final HashMap<UUID, CharacterController> charactersByPlayer = new HashMap<>();

    public static JoltScenePhysics tryCreate() {
        try {
            if (!JoltBootstrap.isAvailable()) {
                return null;
            }
            return new JoltScenePhysics();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public JoltScenePhysics() {
        JoltBootstrap.ensureInitialized();

        BroadPhaseLayerInterfaceTable bpLayers = new BroadPhaseLayerInterfaceTable(2, 2)
                .mapObjectToBroadPhaseLayer(OBJECT_LAYER_STATIC, BROADPHASE_LAYER_STATIC)
                .mapObjectToBroadPhaseLayer(OBJECT_LAYER_MOVING, BROADPHASE_LAYER_MOVING);

        ObjectLayerPairFilterTable layerPairs = new ObjectLayerPairFilterTable(2)
                .enableCollision(OBJECT_LAYER_MOVING, OBJECT_LAYER_MOVING)
                .enableCollision(OBJECT_LAYER_MOVING, OBJECT_LAYER_STATIC)
                .enableCollision(OBJECT_LAYER_STATIC, OBJECT_LAYER_MOVING);

        ObjectVsBroadPhaseLayerFilterTable objVsBp = new ObjectVsBroadPhaseLayerFilterTable(bpLayers, 2, layerPairs, 2);

        system.init(
                32_768, // max bodies
                0, // num body mutexes (0=auto)
                32_768, // max body pairs
                32_768, // max contact constraints
                bpLayers,
                objVsBp,
                layerPairs
        );
        system.setGravity(0.0f, -GRAVITY, 0.0f);
        bodies = system.getBodyInterface();

        updateSettings.setStickToFloorStepDown(new Vec3(0.0f, -0.5f, 0.0f));
        updateSettings.setWalkStairsStepUp(new Vec3(0.0f, 0.75f, 0.0f));
        updateSettings.setWalkStairsMinStepForward(0.01f);
        updateSettings.setWalkStairsStepForwardTest(0.01f);
        updateSettings.setWalkStairsCosAngleForwardContact(0.0f);
    }

    public void syncStaticColliders(Engine engine) {
        if (engine == null) {
            return;
        }
        long rev = engine.csgRevision();
        if (rev == lastCsgRevision) {
            return;
        }
        lastCsgRevision = rev;

        destroyStaticBodies();
        SceneTree tree = engine.sceneTree();
        if (tree == null || tree.root() == null) {
            return;
        }
        collectStatic(tree.root(), Transform.IDENTITY, engine, staticBodyIds);
        system.optimizeBroadPhase();
    }

    public MovementResult moveCharacter(UUID playerId,
                                        Node bodyNode,
                                        float moveX,
                                        float moveZ,
                                        float yawDeg,
                                        float speed,
                                        boolean jump,
                                        boolean sprint,
                                        float dtSeconds) {
        if (playerId == null) {
            return null;
        }
        if (dtSeconds <= 0.0f) {
            return null;
        }
        CharacterController controller = charactersByPlayer.computeIfAbsent(playerId, k -> new CharacterController());
        if (bodyNode == null) {
            return null;
        }

        Transform authored = worldTransform(bodyNode);
        controller.ensureCreated(system, authored.pos.x, authored.pos.y, authored.pos.z);

        float effectiveSpeed = sprint ? speed * SPRINT_MULT : speed;
        double yawRad = Math.toRadians(yawDeg);
        float cos = (float) Math.cos(yawRad);
        float sin = (float) Math.sin(yawRad);
        float targetVelX = (-moveZ * sin + moveX * cos) * effectiveSpeed;
        float targetVelZ = (moveZ * cos + moveX * sin) * effectiveSpeed;

        Vec3 curVel = controller.character.getLinearVelocity();
        float vy = curVel.getY();
        if (jump && controller.character.isSupported()) {
            vy = JUMP_VELOCITY;
        }
        controller.character.setLinearVelocity(targetVelX, vy, targetVelZ);

        Vec3 gravity = new Vec3(0.0f, -GRAVITY, 0.0f);
        controller.character.extendedUpdate(
                dtSeconds,
                gravity,
                updateSettings,
                system.getDefaultBroadPhaseLayerFilter(OBJECT_LAYER_MOVING),
                system.getDefaultLayerFilter(OBJECT_LAYER_MOVING),
                bodyFilter,
                shapeFilter,
                tempAllocator
        );

        RVec3 p = controller.character.getPosition();
        Vec3 v = controller.character.getLinearVelocity();
        boolean supported = controller.character.isSupported()
                || controller.character.getGroundState() == EGroundState.OnGround;
        return new MovementResult(p.x(), p.y(), p.z(), v.getX(), v.getY(), v.getZ(), supported);
    }

    public void onPlayerRemoved(UUID playerId) {
        if (playerId == null) {
            return;
        }
        CharacterController c = charactersByPlayer.remove(playerId);
        if (c != null) {
            c.close();
        }
    }

    public void clearAllCharacters() {
        for (CharacterController c : charactersByPlayer.values()) {
            if (c != null) {
                c.close();
            }
        }
        charactersByPlayer.clear();
    }

    private void collectStatic(Node node, Transform parentWorld, Engine engine, ArrayList<Integer> outBodyIds) {
        if (node == null || engine == null) {
            return;
        }
        String typeId = engine.nodeTypes().typeIdFor(node);
        Transform local = localTransform(node, typeId);
        Transform world = shouldInheritTransform(node) ? parentWorld.compose(local) : local;

        if (isStaticColliderNode(typeId, node)) {
            int bodyId = createStaticBox(world);
            if (bodyId != 0 && bodyId != com.github.stephengold.joltjni.Jolt.cInvalidBodyId) {
                outBodyIds.add(bodyId);
            }
        }
        for (Node child : node.children()) {
            collectStatic(child, world, engine, outBodyIds);
        }
    }

    private static boolean isStaticColliderNode(String typeId, Node node) {
        if (typeId == null || node == null) {
            return false;
        }
        if (!("CSGBlock".equals(typeId) || "CSGBox".equals(typeId))) {
            return false;
        }
        String solid = node.getProperty("solid");
        if (solid == null || solid.isBlank()) {
            return true;
        }
        String s = solid.trim().toLowerCase();
        return !("false".equals(s) || "0".equals(s));
    }

    private int createStaticBox(Transform world) {
        if (world == null) {
            return 0;
        }
        float sx = (float) Math.max(1e-6, world.scale.x);
        float sy = (float) Math.max(1e-6, world.scale.y);
        float sz = (float) Math.max(1e-6, world.scale.z);

        com.github.stephengold.joltjni.BoxShape shape = new com.github.stephengold.joltjni.BoxShape(sx * 0.5f, sy * 0.5f, sz * 0.5f);
        try {
            BodyCreationSettings settings = new BodyCreationSettings(
                    shape,
                    new RVec3(world.pos.x, world.pos.y, world.pos.z),
                    new Quat((float) world.rot.x, (float) world.rot.y, (float) world.rot.z, (float) world.rot.w),
                    EMotionType.Static,
                    OBJECT_LAYER_STATIC
            );
            try {
                return bodies.createAndAddBody(settings, EActivation.DontActivate);
            } finally {
                settings.close();
            }
        } finally {
            shape.close();
        }
    }

    private void destroyStaticBodies() {
        for (int id : staticBodyIds) {
            try {
                bodies.removeBody(id);
            } catch (Exception ignored) {
            }
            try {
                bodies.destroyBody(id);
            } catch (Exception ignored) {
            }
        }
        staticBodyIds.clear();
    }

    private static boolean shouldInheritTransform(Node node) {
        if (node == null) {
            return true;
        }
        String v = node.getProperty("@inherit_transform");
        if (v == null || v.isBlank()) {
            return true;
        }
        String s = v.trim().toLowerCase();
        return !("false".equals(s) || "0".equals(s));
    }

    private static Transform worldTransform(Node node) {
        if (node == null) {
            return Transform.IDENTITY;
        }
        Transform parent = shouldInheritTransform(node) ? worldTransform(node.parent()) : Transform.IDENTITY;
        Transform local = localTransform(node, null);
        return parent.compose(local);
    }

    private static Transform localTransform(Node node, String typeId) {
        if (node == null) {
            return Transform.IDENTITY;
        }
        float x = parseFloat(node.getProperty("x"), 0.0f);
        float y = parseFloat(node.getProperty("y"), 0.0f);
        float z = parseFloat(node.getProperty("z"), 0.0f);

        float rxDeg = parseFloat(node.getProperty("rx"), 0.0f);
        float ryDeg = parseFloat(node.getProperty("ry"), 0.0f);
        float rzDeg = parseFloat(node.getProperty("rz"), 0.0f);
        QuatD rot = QuatD.fromEulerDeg(rxDeg, ryDeg, rzDeg);

        boolean hasScale = node.getProperty("sx") != null || node.getProperty("sy") != null || node.getProperty("sz") != null;
        double sx = hasScale ? safeScale(parseFloat(node.getProperty("sx"), 1.0f)) : 1.0;
        double sy = hasScale ? safeScale(parseFloat(node.getProperty("sy"), 1.0f)) : 1.0;
        double sz = hasScale ? safeScale(parseFloat(node.getProperty("sz"), 1.0f)) : 1.0;

        boolean pivotIsMinCorner = "CSGBox".equals(typeId) || "CSGBlock".equals(typeId);
        double px = x;
        double py = y;
        double pz = z;
        if (pivotIsMinCorner && hasScale) {
            px = x + sx * 0.5;
            py = y + sy * 0.5;
            pz = z + sz * 0.5;
        }
        return new Transform(new Vec3d(px, py, pz), rot, new Vec3d(sx, sy, sz));
    }

    private static double safeScale(double value) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }
        double v = Math.abs(value) < 1e-6 ? 0.0 : value;
        return Math.max(1e-6, v);
    }

    private static float parseFloat(String v, float fallback) {
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            float f = Float.parseFloat(v.trim());
            return Float.isFinite(f) ? f : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public void close() {
        clearAllCharacters();
        destroyStaticBodies();
        try {
            shapeFilter.close();
        } catch (Exception ignored) {
        }
        try {
            bodyFilter.close();
        } catch (Exception ignored) {
        }
        try {
            updateSettings.close();
        } catch (Exception ignored) {
        }
        try {
            jobs.close();
        } catch (Exception ignored) {
        }
        try {
            tempAllocator.close();
        } catch (Exception ignored) {
        }
        try {
            system.forgetMe();
        } catch (Exception ignored) {
        }
        try {
            system.close();
        } catch (Exception ignored) {
        }
    }

    public record MovementResult(
            float x, float y, float z,
            float velX, float velY, float velZ,
            boolean onFloor
    ) {
    }

    private static final class CharacterController implements AutoCloseable {
        private CharacterVirtual character;
        private CapsuleShape shape;
        private CharacterVirtualSettings settings;
        private boolean created;

        void ensureCreated(PhysicsSystem system, double x, double y, double z) {
            if (created) {
                return;
            }
            Objects.requireNonNull(system, "system");
            shape = new CapsuleShape(DEFAULT_CAPSULE_HALF_CYL, DEFAULT_CAPSULE_RADIUS);
            settings = new CharacterVirtualSettings();
            settings.setInnerBodyShape(shape);
            settings.setInnerBodyLayer(OBJECT_LAYER_MOVING);
            settings.setMass(80.0f);
            character = new CharacterVirtual(settings, new RVec3(x, y, z), Quat.sIdentity(), 0L, system);
            created = true;
        }

        @Override
        public void close() {
            if (character != null) {
                try {
                    character.close();
                } catch (Exception ignored) {
                }
                character = null;
            }
            if (settings != null) {
                try {
                    settings.close();
                } catch (Exception ignored) {
                }
                settings = null;
            }
            if (shape != null) {
                try {
                    shape.close();
                } catch (Exception ignored) {
                }
                shape = null;
            }
            created = false;
        }
    }

    private record Transform(Vec3d pos, QuatD rot, Vec3d scale) {
        static final Transform IDENTITY = new Transform(new Vec3d(0, 0, 0), QuatD.IDENTITY, new Vec3d(1, 1, 1));

        Transform compose(Transform child) {
            Vec3d scaled = new Vec3d(child.pos.x * scale.x, child.pos.y * scale.y, child.pos.z * scale.z);
            Vec3d worldPos = pos.add(rot.rotate(scaled));
            QuatD worldRot = rot.mul(child.rot).normalized();
            Vec3d worldScale = scale.mul(child.scale);
            return new Transform(worldPos, worldRot, worldScale);
        }
    }

    private record Vec3d(double x, double y, double z) {
        Vec3d add(Vec3d o) {
            return new Vec3d(x + o.x, y + o.y, z + o.z);
        }

        Vec3d mul(Vec3d o) {
            return new Vec3d(x * o.x, y * o.y, z * o.z);
        }
    }

    private record QuatD(double x, double y, double z, double w) {
        static final QuatD IDENTITY = new QuatD(0, 0, 0, 1);

        static QuatD fromEulerDeg(float rxDeg, float ryDeg, float rzDeg) {
            double rx = Math.toRadians(Float.isFinite(rxDeg) ? rxDeg : 0.0f);
            double ry = Math.toRadians(Float.isFinite(ryDeg) ? ryDeg : 0.0f);
            double rz = Math.toRadians(Float.isFinite(rzDeg) ? rzDeg : 0.0f);

            double hx = rx * 0.5;
            double hy = ry * 0.5;
            double hz = rz * 0.5;

            double sx = Math.sin(hx), cx = Math.cos(hx);
            double sy = Math.sin(hy), cy = Math.cos(hy);
            double sz = Math.sin(hz), cz = Math.cos(hz);

            QuatD qx = new QuatD(sx, 0, 0, cx);
            QuatD qy = new QuatD(0, sy, 0, cy);
            QuatD qz = new QuatD(0, 0, sz, cz);

            return qz.mul(qy).mul(qx).normalized();
        }

        QuatD mul(QuatD b) {
            double nx = w * b.x + x * b.w + y * b.z - z * b.y;
            double ny = w * b.y - x * b.z + y * b.w + z * b.x;
            double nz = w * b.z + x * b.y - y * b.x + z * b.w;
            double nw = w * b.w - x * b.x - y * b.y - z * b.z;
            return new QuatD(nx, ny, nz, nw);
        }

        QuatD normalized() {
            double n = Math.sqrt(x * x + y * y + z * z + w * w);
            if (n <= 0.0) {
                return IDENTITY;
            }
            double inv = 1.0 / n;
            return new QuatD(x * inv, y * inv, z * inv, w * inv);
        }

        Vec3d rotate(Vec3d v) {
            double vx = v.x, vy = v.y, vz = v.z;
            double tx = 2.0 * (y * vz - z * vy);
            double ty = 2.0 * (z * vx - x * vz);
            double tz = 2.0 * (x * vy - y * vx);
            return new Vec3d(
                    vx + w * tx + (y * tz - z * ty),
                    vy + w * ty + (z * tx - x * tz),
                    vz + w * tz + (x * ty - y * tx)
            );
        }
    }
}
