package com.moud.server.minestom.physics.rapier;

import com.moud.core.physics.CollisionGeometry;
import com.moud.physics.api.BodyHandle;
import com.moud.physics.api.CollisionGroups;
import com.moud.physics.api.ContactEvent;
import com.moud.physics.api.DynamicProps;
import com.moud.physics.api.QueryFilter;
import com.moud.physics.api.Quat;
import com.moud.physics.api.RaycastHit;
import com.moud.physics.api.ShapeDesc;
import com.moud.physics.api.Transform;
import com.moud.physics.api.Vec3;
import com.moud.physics.rapier.RapierCharacterController;
import com.moud.physics.rapier.RapierPhysicsWorld;
import com.moud.core.scene.Node;
import com.moud.core.scene.SceneTree;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.RigidBodyEntry;
import com.moud.server.minestom.collision.CollisionBakeService;
import com.moud.server.minestom.collision.CollisionStrategy;
import com.moud.server.minestom.collision.CollisionUsage;
import com.moud.server.minestom.collision.SceneNodeTransform;
import com.moud.server.minestom.engine.Engine;
import com.moud.server.minestom.physics.CollisionEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RapierScenePhysicsWorld implements AutoCloseable {
    private static final CollisionGroups DEFAULT_GROUPS = new CollisionGroups(1, 1);
    private static final QueryFilter DEFAULT_FILTER = QueryFilter.ALL;
    private static final float FIXED_DT = 1.0f / 60.0f;

    private final CollisionBakeService collisionBakeService;
    private final RapierPhysicsWorld world = new RapierPhysicsWorld();
    private final RapierCharacterController characterController = new RapierCharacterController(world);
    private final Map<Long, BodyHandle> nodeToBody = new HashMap<>();
    private final Map<Long, Long> bodyToNode = new HashMap<>();
    private final Map<Long, String> nodeTypes = new HashMap<>();
    private final Map<Long, BodyHandle> characterBodies = new HashMap<>();
    private final List<CollisionEvent> collisionEvents = new ArrayList<>();
    private final Set<Long> currentContactPairs = new HashSet<>();
    private final Set<Long> previousContactPairs = new HashSet<>();
    private final Set<Long> blockedContactPairs = new HashSet<>();
    private long lastPhysicsRevision = Long.MIN_VALUE;
    private long physicsTickCount;

    public record CharacterTickResult(
            double x, double y, double z,
            double vx, double vy, double vz,
            boolean onGround, boolean onSteepGround,
            double groundNx, double groundNy, double groundNz
    ) {}

    public RapierScenePhysicsWorld(CollisionBakeService collisionBakeService) {
        this.collisionBakeService = collisionBakeService;
    }

    public synchronized void syncStaticColliders(Engine engine) {
        rebuildIfNeeded(engine);
    }

    public synchronized void syncDynamicBodies(Engine engine) {
        rebuildIfNeeded(engine);
    }

    public synchronized void syncCollisionFilters(Engine engine) {
    }

    public synchronized void tickFixed() {
        world.step(FIXED_DT);
        physicsTickCount++;
        drainContacts();
    }

    public long physicsTick() {
        return physicsTickCount;
    }

    public synchronized void writeDynamicBodiesBack(Engine engine) {
        if (engine == null) return;
        SceneTree tree = engine.sceneTree();
        if (tree == null) return;
        for (Map.Entry<Long, BodyHandle> entry : nodeToBody.entrySet()) {
            if (!"RigidBody3D".equals(nodeTypes.get(entry.getKey()))) {
                continue;
            }
            Node node = tree.getNode(entry.getKey());
            if (node == null) continue;
            Transform t = world.getTransform(entry.getValue());
            node.setProperty("x", ParseUtils.trimFloat(t.pos().x()));
            node.setProperty("y", ParseUtils.trimFloat(t.pos().y()));
            node.setProperty("z", ParseUtils.trimFloat(t.pos().z()));
            SceneNodeTransform.Vec3 euler = new SceneNodeTransform.Quat(
                    t.rot().x(), t.rot().y(), t.rot().z(), t.rot().w()).toEulerDegXYZ();
            node.setProperty("rx", ParseUtils.trimFloat((float) euler.x()));
            node.setProperty("ry", ParseUtils.trimFloat((float) euler.y()));
            node.setProperty("rz", ParseUtils.trimFloat((float) euler.z()));
        }
    }

    public synchronized void tickRaycasts(Engine engine) {
    }

    public synchronized Optional<RaycastHit> raycast(double ox, double oy, double oz,
                                                     double dx, double dy, double dz,
                                                     double maxDist) {
        return world.raycast(
                new Vec3((float) ox, (float) oy, (float) oz),
                new Vec3((float) dx, (float) dy, (float) dz),
                (float) maxDist,
                DEFAULT_FILTER);
    }

    public synchronized long[] overlapSphere(double x, double y, double z, double radius) {
        return world.overlap(
                new ShapeDesc.Sphere((float) Math.max(0.01, radius)),
                new Transform(new Vec3((float) x, (float) y, (float) z), Quat.IDENTITY),
                DEFAULT_FILTER);
    }

    public synchronized List<CollisionEvent> consumeCollisionEvents() {
        List<CollisionEvent> out = List.copyOf(collisionEvents);
        collisionEvents.clear();
        return out;
    }

    public synchronized Set<Long> currentContactPairs() {
        return Set.copyOf(currentContactPairs);
    }

    public synchronized Set<Long> previousContactPairs() {
        return Set.copyOf(previousContactPairs);
    }

    public synchronized Long nodeIdForBody(long bodyId) {
        return bodyToNode.get(bodyId);
    }

    public synchronized Long bodyIdForNode(long nodeId) {
        BodyHandle h = nodeToBody.get(nodeId);
        return h == null ? null : h.id();
    }

    public synchronized List<Long> activeDynamicNodeIds() {
        return dynamicNodeIds();
    }

    public synchronized List<Long> dynamicNodeIds() {
        ArrayList<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, String> entry : nodeTypes.entrySet()) {
            if ("RigidBody3D".equals(entry.getValue())) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    public synchronized List<RigidBodyEntry> snapshotDynamicBodies() {
        ArrayList<RigidBodyEntry> entries = new ArrayList<>();
        for (Map.Entry<Long, BodyHandle> entry : nodeToBody.entrySet()) {
            if (!"RigidBody3D".equals(nodeTypes.get(entry.getKey()))) continue;
            Transform t = world.getTransform(entry.getValue());
            Vec3 v = world.getLinearVelocity(entry.getValue());
            entries.add(new RigidBodyEntry(entry.getKey(),
                    t.pos().x(), t.pos().y(), t.pos().z(),
                    t.rot().x(), t.rot().y(), t.rot().z(), t.rot().w(),
                    v.x(), v.y(), v.z(),
                    0f, 0f, 0f,
                    false));
        }
        return entries;
    }

    public synchronized void applyForce(long nodeId, float fx, float fy, float fz) {
        BodyHandle h = nodeToBody.get(nodeId);
        if (h != null) world.applyForce(h, new Vec3(fx, fy, fz));
    }

    public synchronized void applyImpulse(long nodeId, float fx, float fy, float fz) {
        BodyHandle h = nodeToBody.get(nodeId);
        if (h != null) world.applyImpulse(h, new Vec3(fx, fy, fz));
    }

    public synchronized void setLinearVelocity(long nodeId, float vx, float vy, float vz) {
        BodyHandle h = nodeToBody.get(nodeId);
        if (h != null) world.setLinearVelocity(h, new Vec3(vx, vy, vz));
    }

    public synchronized float[] getLinearVelocity(long nodeId) {
        BodyHandle h = nodeToBody.get(nodeId);
        if (h == null) return new float[]{0f, 0f, 0f};
        Vec3 v = world.getLinearVelocity(h);
        return new float[]{v.x(), v.y(), v.z()};
    }

    public synchronized CharacterTickResult tickCharacter(Node node,
                                                          double x, double y, double z,
                                                          double vx, double vy, double vz,
                                                          double dt) {
        if (node == null) return null;
        BodyHandle h = characterBodies.computeIfAbsent(node.nodeId(), ignored -> world.addKinematic(
                characterShape(node),
                new Transform(new Vec3((float) x, (float) y, (float) z), Quat.IDENTITY),
                DEFAULT_GROUPS));
        Transform start = new Transform(new Vec3((float) x, (float) y, (float) z), Quat.IDENTITY);
        world.setTransform(h, start);
        Vec3 desired = new Vec3((float) (vx * dt), (float) (vy * dt), (float) (vz * dt));
        RapierCharacterController.Result moved = characterController.move(h, desired, (float) dt);
        Vec3 d = moved.corrected();
        double nx = x + d.x();
        double ny = y + d.y();
        double nz = z + d.z();
        world.setTransform(h, new Transform(new Vec3((float) nx, (float) ny, (float) nz), Quat.IDENTITY));
        double actualVx = dt > 1.0e-8 ? d.x() / dt : 0.0;
        double actualVy = moved.grounded() ? 0.0 : (dt > 1.0e-8 ? d.y() / dt : 0.0);
        double actualVz = dt > 1.0e-8 ? d.z() / dt : 0.0;
        return new CharacterTickResult(nx, ny, nz, actualVx, actualVy, actualVz,
                moved.grounded(), false, 0.0, moved.grounded() ? 1.0 : 0.0, 0.0);
    }

    public synchronized void blockContactPair(long nodeIdA, long nodeIdB) {
        blockedContactPairs.add(packPair(nodeIdA, nodeIdB));
    }

    public synchronized void unblockContactPair(long nodeIdA, long nodeIdB) {
        blockedContactPairs.remove(packPair(nodeIdA, nodeIdB));
    }

    public synchronized boolean isContactPairBlocked(long nodeIdA, long nodeIdB) {
        return blockedContactPairs.contains(packPair(nodeIdA, nodeIdB));
    }

    public synchronized void removeCharacter(long nodeId) {
        BodyHandle h = characterBodies.remove(nodeId);
        if (h != null) world.remove(h);
    }

    public synchronized void retainCharacters(Set<Long> liveNodeIds) {
        characterBodies.entrySet().removeIf(entry -> {
            if (liveNodeIds != null && liveNodeIds.contains(entry.getKey())) {
                return false;
            }
            world.remove(entry.getValue());
            return true;
        });
    }

    public synchronized List<String> debugDump() {
        return List.of("[physics] rapierBodies=" + nodeToBody.size() + " physicsTick=" + physicsTickCount);
    }

    private void rebuildIfNeeded(Engine engine) {
        if (engine == null || engine.physicsRevision() == lastPhysicsRevision) {
            return;
        }
        clearSceneBodies();
        SceneTree tree = engine.sceneTree();
        if (tree != null && tree.root() != null) {
            ArrayDeque<NodeFrame> stack = new ArrayDeque<>();
            stack.push(new NodeFrame(tree.root(), SceneNodeTransform.Transform.IDENTITY));
            while (!stack.isEmpty()) {
                NodeFrame frame = stack.pop();
                Node node = frame.node();
                if (node == null) continue;
                String typeId = engine.nodeTypes().typeIdFor(node);
                SceneNodeTransform.Transform local = SceneNodeTransform.localTransform(node, typeId);
                SceneNodeTransform.Transform worldTransform = SceneNodeTransform.shouldInheritTransform(node)
                        ? frame.parentWorld().compose(local)
                        : local;
                if (isPhysicsBody(typeId) && nodeIsCollisionEnabled(node, typeId)) {
                    createBody(node, typeId, worldTransform, engine);
                }
                for (Node child : node.children()) {
                    if (child != null) stack.push(new NodeFrame(child, worldTransform));
                }
            }
        }
        lastPhysicsRevision = engine.physicsRevision();
    }

    private void createBody(Node node, String typeId, SceneNodeTransform.Transform worldTransform, Engine engine) {
        ShapeDesc shape = shapeFor(node, typeId, engine);
        Transform transform = toRapierTransform(worldTransform);
        BodyHandle handle;
        if ("RigidBody3D".equals(typeId)) {
            handle = world.addDynamic(shape, transform, dynamicProps(node), groups(node));
        } else if ("Area3D".equals(typeId)) {
            handle = world.addArea(shape, transform, groups(node));
        } else {
            handle = world.addStatic(shape, transform, groups(node));
        }
        nodeToBody.put(node.nodeId(), handle);
        bodyToNode.put(handle.id(), node.nodeId());
        nodeTypes.put(node.nodeId(), typeId);
    }

    private ShapeDesc shapeFor(Node node, String typeId, Engine engine) {
        if ("CSGBox".equals(typeId) || "CSGBlock".equals(typeId)) {
            return boxShapeFromSize(node);
        }
        CollisionStrategy strategy = CollisionBakeService.strategyForNode(node, typeId,
                "RigidBody3D".equals(typeId) ? CollisionUsage.DYNAMIC : CollisionUsage.STATIC);
        if (strategy == CollisionStrategy.SPHERE) {
            return new ShapeDesc.Sphere((float) Math.max(0.01, propDouble(node, "radius", 0.5)));
        }
        if (strategy == CollisionStrategy.CAPSULE) {
            return characterShape(node);
        }
        if (!"RigidBody3D".equals(typeId) && (strategy == CollisionStrategy.MESH || strategy == CollisionStrategy.AUTO)) {
            CollisionBakeService.BakeResult baked = collisionBakeService.bake(node, typeId, engine, CollisionUsage.STATIC);
            if (!baked.hulls().isEmpty()) {
                CollisionGeometry g = baked.hulls().get(0);
                return new ShapeDesc.Trimesh(g.vertices(), g.indices());
            }
        }
        return boxShapeFromSize(node);
    }

    private ShapeDesc boxShapeFromSize(Node node) {
        return new ShapeDesc.Box(new Vec3(
                (float) Math.max(0.01, propDouble(node, "sx", 1.0) * 0.5),
                (float) Math.max(0.01, propDouble(node, "sy", 1.0) * 0.5),
                (float) Math.max(0.01, propDouble(node, "sz", 1.0) * 0.5)));
    }

    private ShapeDesc.Capsule characterShape(Node node) {
        double radius = Math.max(0.01, propDouble(node, "radius", 0.3));
        double height = Math.max(radius * 2.0, propDouble(node, "height", 1.8));
        return new ShapeDesc.Capsule((float) radius, (float) Math.max(1.0e-6, height * 0.5 - radius));
    }

    private DynamicProps dynamicProps(Node node) {
        return new DynamicProps(
                (float) Math.max(0.01, propDouble(node, "mass", 1.0)),
                (float) propDouble(node, "gravity_scale", 1.0),
                (float) Math.max(0.0, propDouble(node, "linear_damping", 0.0)),
                (float) Math.max(0.0, propDouble(node, "angular_damping", 0.0)),
                propBool(node, "ccd", false));
    }

    private CollisionGroups groups(Node node) {
        return new CollisionGroups((int) propLong(node, "collision_layer", 1L), (int) propLong(node, "collision_mask", 1L));
    }

    private static Transform toRapierTransform(SceneNodeTransform.Transform t) {
        return new Transform(
                new Vec3((float) t.pos().x(), (float) t.pos().y(), (float) t.pos().z()),
                new Quat((float) t.rot().x(), (float) t.rot().y(), (float) t.rot().z(), (float) t.rot().w()));
    }

    private void drainContacts() {
        previousContactPairs.clear();
        previousContactPairs.addAll(currentContactPairs);
        currentContactPairs.clear();
        for (ContactEvent e : world.drainContactEvents()) {
            Long a = bodyToNode.get(e.a().id());
            Long b = bodyToNode.get(e.b().id());
            if (a == null || b == null || blockedContactPairs.contains(packPair(a, b))) {
                continue;
            }
            currentContactPairs.add(packPair(a, b));
            collisionEvents.add(new CollisionEvent(a, b,
                    e.point().x(), e.point().y(), e.point().z(),
                    e.normal().x(), e.normal().y(), e.normal().z(),
                    0f, 0f, 0f));
        }
    }

    private void clearSceneBodies() {
        for (BodyHandle h : nodeToBody.values()) {
            world.remove(h);
        }
        nodeToBody.clear();
        bodyToNode.clear();
        nodeTypes.clear();
        currentContactPairs.clear();
        previousContactPairs.clear();
        collisionEvents.clear();
    }

    private static long packPair(long a, long b) {
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        return (lo & 0xFFFFFFFFL) | ((hi & 0xFFFFFFFFL) << 32);
    }

    private static boolean isPhysicsBody(String typeId) {
        return "StaticBody3D".equals(typeId)
                || "RigidBody3D".equals(typeId)
                || "Area3D".equals(typeId)
                || "CSGBox".equals(typeId)
                || "CSGBlock".equals(typeId);
    }

    private static boolean nodeIsCollisionEnabled(Node node, String typeId) {
        if ("CSGBox".equals(typeId) || "CSGBlock".equals(typeId)) {
            return propBool(node, "solid", true);
        }
        return propBool(node, "enabled", true);
    }

    private static boolean propBool(Node node, String key, boolean fallback) {
        return ParseUtils.parseBool(node == null ? null : node.getProperty(key), fallback);
    }

    private static double propDouble(Node node, String key, double fallback) {
        return ParseUtils.parseDouble(node == null ? null : node.getProperty(key), fallback);
    }

    private static long propLong(Node node, String key, long fallback) {
        try {
            String v = node == null ? null : node.getProperty(key);
            return v == null || v.isBlank() ? fallback : Long.parseLong(v.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public synchronized void close() {
        clearSceneBodies();
        for (BodyHandle h : characterBodies.values()) {
            world.remove(h);
        }
        characterBodies.clear();
        characterController.close();
        world.close();
    }

    private record NodeFrame(Node node, SceneNodeTransform.Transform parentWorld) {
    }
}
