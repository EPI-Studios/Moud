package com.moud.server.minestom.physics;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyFilter;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.CapsuleShape;
import com.github.stephengold.joltjni.JobSystemSingleThreaded;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Shape;
import com.github.stephengold.joltjni.ShapeFilter;
import com.github.stephengold.joltjni.SphereShape;
import com.github.stephengold.joltjni.TempAllocatorImpl;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.Quat;
import com.moud.server.minestom.engine.Engine;
import com.moud.core.math.Transform;
import com.moud.core.physics.BodyHandle;
import com.moud.core.physics.CollisionShape;
import com.moud.core.physics.PhysicsWorld;
import com.moud.core.physics.RaycastResult;
import com.moud.core.scene.Node;
import com.moud.core.scene.SceneTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Jolt-backed {@link PhysicsWorld} implementation.
 *
 * <p>In addition to the generic interface, this class exposes
 * {@link #syncStaticColliders(Engine)} which rebuilds static collision geometry
 * whenever the engine's CSG revision changes. Call it every scene tick.
 */
public final class JoltPhysicsWorld implements PhysicsWorld {
    private static final int OBJECT_LAYER_STATIC = 0;
    private static final int OBJECT_LAYER_MOVING = 1;
    private static final int BROADPHASE_LAYER_STATIC = 0;
    private static final int BROADPHASE_LAYER_MOVING = 1;

    private static final float GRAVITY = 30.0f;

    private final PhysicsSystem system = new PhysicsSystem();
    private final BodyInterface bodies;
    private final TempAllocatorImpl tempAllocator = new TempAllocatorImpl(16 * 1024 * 1024);
    private final JobSystemSingleThreaded jobs =
            new JobSystemSingleThreaded(1024);
    private final BodyFilter bodyFilter = new BodyFilter();
    private final ShapeFilter shapeFilter = new ShapeFilter();

    private long lastCsgRevision = Long.MIN_VALUE;
    private final ArrayList<Integer> staticBodyIds = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Try to create a {@link JoltPhysicsWorld}.
     *
     * @return a new instance, or {@code null} if Jolt is not available on this platform
     */
    public static JoltPhysicsWorld tryCreate() {
        try {
            if (!JoltBootstrap.isAvailable()) {
                return null;
            }
            return new JoltPhysicsWorld();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public JoltPhysicsWorld() {
        JoltBootstrap.ensureInitialized();

        BroadPhaseLayerInterfaceTable bpLayers = new BroadPhaseLayerInterfaceTable(2, 2)
                .mapObjectToBroadPhaseLayer(OBJECT_LAYER_STATIC, BROADPHASE_LAYER_STATIC)
                .mapObjectToBroadPhaseLayer(OBJECT_LAYER_MOVING, BROADPHASE_LAYER_MOVING);

        ObjectLayerPairFilterTable layerPairs = new ObjectLayerPairFilterTable(2)
                .enableCollision(OBJECT_LAYER_MOVING, OBJECT_LAYER_MOVING)
                .enableCollision(OBJECT_LAYER_MOVING, OBJECT_LAYER_STATIC)
                .enableCollision(OBJECT_LAYER_STATIC, OBJECT_LAYER_MOVING);

        ObjectVsBroadPhaseLayerFilterTable objVsBp =
                new ObjectVsBroadPhaseLayerFilterTable(bpLayers, 2, layerPairs, 2);

        system.init(
                32_768, // max bodies
                0,      // num body mutexes (0=auto)
                32_768, // max body pairs
                32_768, // max contact constraints
                bpLayers,
                objVsBp,
                layerPairs
        );
        system.setGravity(0.0f, -GRAVITY, 0.0f);
        bodies = system.getBodyInterface();
    }

    // -----------------------------------------------------------------------
    // PhysicsWorld
    // -----------------------------------------------------------------------

    @Override
    public void step(float dt) {
        if (dt <= 0.0f) {
            return;
        }
        try {
            system.update(dt, 1, tempAllocator, jobs);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public BodyHandle addStaticBody(CollisionShape shape, double x, double y, double z,
                                    float rxDeg, float ryDeg, float rzDeg) {
        int bodyId = createBody(shape, x, y, z, rxDeg, ryDeg, rzDeg, EMotionType.Static, OBJECT_LAYER_STATIC, 0.0f);
        return bodyId == Jolt.cInvalidBodyId
                ? BodyHandle.INVALID
                : new BodyHandle(bodyId);
    }

    @Override
    public BodyHandle addDynamicBody(CollisionShape shape, double x, double y, double z,
                                     float rxDeg, float ryDeg, float rzDeg, float mass) {
        int bodyId = createBody(shape, x, y, z, rxDeg, ryDeg, rzDeg, EMotionType.Dynamic, OBJECT_LAYER_MOVING, mass);
        return bodyId == Jolt.cInvalidBodyId
                ? BodyHandle.INVALID
                : new BodyHandle(bodyId);
    }

    @Override
    public void removeBody(BodyHandle handle) {
        if (handle == null || !handle.isValid()) {
            return;
        }
        try {
            bodies.removeBody(handle.id());
        } catch (Exception ignored) {
        }
        try {
            bodies.destroyBody(handle.id());
        } catch (Exception ignored) {
        }
    }

    @Override
    public Optional<RaycastResult> raycast(double ox, double oy, double oz,
                                           double dx, double dy, double dz,
                                           double maxDist) {
        // TODO: implement using PhysicsSystem.getNarrowPhaseQuery().castRay()
        return Optional.empty();
    }

    @Override
    public List<BodyHandle> overlapSphere(double x, double y, double z, double radius) {
        // TODO: implement using PhysicsSystem.getNarrowPhaseQuery().collideSphere()
        return List.of();
    }

    @Override
    public void close() {
        destroyStaticBodies();
        safeClose(shapeFilter);
        safeClose(bodyFilter);
        safeClose(jobs);
        safeClose(tempAllocator);
        try {
            system.forgetMe();
        } catch (Exception ignored) {
        }
        safeClose(system);
    }

    // -----------------------------------------------------------------------
    // CSG sync (engine-specific, called by ServerScene)
    // -----------------------------------------------------------------------

    /**
     * Rebuild static collision bodies from the engine's CSG nodes whenever the
     * CSG revision changes. No-op if the revision has not changed.
     */
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
        collectStatic(tree.root(), Transform.IDENTITY, engine);
        system.optimizeBroadPhase();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private int createBody(CollisionShape shape, double x, double y, double z,
                           float rxDeg, float ryDeg, float rzDeg,
                           EMotionType motionType, int layer, float mass) {
        Shape joltShape = toJoltShape(shape);
        if (joltShape == null) {
            return Jolt.cInvalidBodyId;
        }
        try {
            QuatD rot = QuatD.fromEulerDeg(rxDeg, ryDeg, rzDeg);
            BodyCreationSettings settings = new BodyCreationSettings(
                    joltShape,
                    new RVec3(x, y, z),
                    new Quat((float) rot.x(), (float) rot.y(), (float) rot.z(), (float) rot.w()),
                    motionType,
                    layer
            );
            if (motionType == EMotionType.Dynamic && mass > 0.0f) {
                settings.setMassPropertiesOverride(
                        new MassProperties().setMass(mass));
                settings.setOverrideMassProperties(
                        EOverrideMassProperties.CalculateInertia);
            }
            try {
                return bodies.createAndAddBody(settings, EActivation.Activate);
            } finally {
                settings.close();
            }
        } finally {
            joltShape.close();
        }
    }

    private static Shape toJoltShape(CollisionShape shape) {
        if (shape == null) {
            return null;
        }
        try {
            return switch (shape) {
                case CollisionShape.Box b -> new BoxShape(
                        (float) Math.max(1e-6, b.halfX()),
                        (float) Math.max(1e-6, b.halfY()),
                        (float) Math.max(1e-6, b.halfZ()));
                case CollisionShape.Sphere s -> new SphereShape(
                        (float) Math.max(1e-6, s.radius()));
                case CollisionShape.Capsule c -> new CapsuleShape(
                        (float) Math.max(1e-6, c.halfHeight()),
                        (float) Math.max(1e-6, c.radius()));
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void collectStatic(Node node, Transform parentWorld, Engine engine) {
        if (node == null || engine == null) {
            return;
        }
        String typeId = engine.nodeTypes().typeIdFor(node);
        Transform local = localTransform(node, typeId);
        Transform world = shouldInheritTransform(node) ? parentWorld.compose(local) : local;

        if (isStaticColliderNode(typeId, node)) {
            int bodyId = createStaticBox(world);
            if (bodyId != 0 && bodyId != Jolt.cInvalidBodyId) {
                staticBodyIds.add(bodyId);
            }
        }
        for (Node child : node.children()) {
            collectStatic(child, world, engine);
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
        float sx = (float) Math.max(1e-6, world.scale().x());
        float sy = (float) Math.max(1e-6, world.scale().y());
        float sz = (float) Math.max(1e-6, world.scale().z());

        BoxShape shape =
                new BoxShape(sx * 0.5f, sy * 0.5f, sz * 0.5f);
        try {
            QuatD rot = world.rot();
            BodyCreationSettings settings = new BodyCreationSettings(
                    shape,
                    new RVec3(world.pos().x(), world.pos().y(), world.pos().z()),
                    new Quat((float) rot.x(), (float) rot.y(), (float) rot.z(), (float) rot.w()),
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

        boolean hasScale = node.getProperty("sx") != null
                || node.getProperty("sy") != null
                || node.getProperty("sz") != null;
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
        return Math.max(1e-6, Math.abs(value) < 1e-6 ? 1e-6 : value);
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

    private static void safeClose(AutoCloseable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }

    // -----------------------------------------------------------------------
    // Private math types (double-precision, kept internal)
    // -----------------------------------------------------------------------

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
        Vec3d add(Vec3d o) { return new Vec3d(x + o.x, y + o.y, z + o.z); }
        Vec3d mul(Vec3d o) { return new Vec3d(x * o.x, y * o.y, z * o.z); }
    }

    private record QuatD(double x, double y, double z, double w) {
        static final QuatD IDENTITY = new QuatD(0, 0, 0, 1);

        static QuatD fromEulerDeg(float rxDeg, float ryDeg, float rzDeg) {
            double rx = Math.toRadians(Float.isFinite(rxDeg) ? rxDeg : 0.0f);
            double ry = Math.toRadians(Float.isFinite(ryDeg) ? ryDeg : 0.0f);
            double rz = Math.toRadians(Float.isFinite(rzDeg) ? rzDeg : 0.0f);
            double hx = rx * 0.5, hy = ry * 0.5, hz = rz * 0.5;
            double sx = Math.sin(hx), cx = Math.cos(hx);
            double sy = Math.sin(hy), cy = Math.cos(hy);
            double sz = Math.sin(hz), cz = Math.cos(hz);
            return new QuatD(sx, 0, 0, cx).mul(new QuatD(0, sy, 0, cy)).mul(new QuatD(0, 0, sz, cz)).normalized();
        }

        QuatD mul(QuatD b) {
            return new QuatD(
                    w * b.x + x * b.w + y * b.z - z * b.y,
                    w * b.y - x * b.z + y * b.w + z * b.x,
                    w * b.z + x * b.y - y * b.x + z * b.w,
                    w * b.w - x * b.x - y * b.y - z * b.z);
        }

        QuatD normalized() {
            double n = Math.sqrt(x * x + y * y + z * z + w * w);
            if (n <= 0.0) return IDENTITY;
            double inv = 1.0 / n;
            return new QuatD(x * inv, y * inv, z * inv, w * inv);
        }

        Vec3d rotate(Vec3d v) {
            double tx = 2.0 * (y * v.z - z * v.y);
            double ty = 2.0 * (z * v.x - x * v.z);
            double tz = 2.0 * (x * v.y - y * v.x);
            return new Vec3d(
                    v.x + w * tx + (y * tz - z * ty),
                    v.y + w * ty + (z * tx - x * tz),
                    v.z + w * tz + (x * ty - y * tx));
        }
    }
}
