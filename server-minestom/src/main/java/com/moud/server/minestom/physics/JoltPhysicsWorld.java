package com.moud.server.minestom.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import com.moud.core.math.Quat;
import com.moud.core.physics.*;
import com.moud.core.scene.Node;
import com.moud.core.scene.SceneTree;
import com.moud.core.util.ParseUtils;
import com.moud.server.minestom.collision.CollisionBakeService;
import com.moud.server.minestom.engine.Engine;
import com.moud.server.minestom.util.DebugLog;

import java.util.*;

public final class JoltPhysicsWorld implements PhysicsWorld {

    private static final float GRAVITY = 30f;
    private static final int TEMP_ALLOCATOR_BYTES = 64 * 1024 * 1024;
    private static final float MIN_CAPSULE_HALF_HEIGHT = 1.0e-6f;

    private final PhysicsSystem system = new PhysicsSystem();
    private final BodyInterface bodies;
    private final TempAllocatorImpl tempAllocator = new TempAllocatorImpl(TEMP_ALLOCATOR_BYTES);
    private final JobSystemSingleThreaded jobs    = new JobSystemSingleThreaded(1024);
    private final BodyFilter bodyFilter           = new BodyFilter();
    private final ShapeFilter shapeFilter         = new ShapeFilter();
    private final CollisionBakeService collisionBakeService;

    private long lastPhysicsRevision = Long.MIN_VALUE;
    private long lastFilterRevision = Long.MIN_VALUE;
    private long pendingPhysicsRevision = Long.MIN_VALUE;
    private long pendingPhysicsSinceNs = 0L;
    private static final long PHYSICS_REBUILD_DEBOUNCE_NS = 200_000_000L;
    private final List<Integer> staticBodyIds  = new ArrayList<>();
    private final List<Integer> dynamicBodyIds = new ArrayList<>();
    private final Map<Integer, Long> bodyToNode = new HashMap<>();
    private final Map<Long, Integer> nodeToBody = new HashMap<>();

    private final List<CollisionEvent> collisionEvents  = new ArrayList<>();
    private final Set<Long> activeContacts              = new HashSet<>();
    private final Set<Long> previousContacts            = new HashSet<>();
    private final List<CollisionEvent> pendingAdded     = new ArrayList<>();
    private final ContactCollector contactCollector;

    public static JoltPhysicsWorld tryCreate(CollisionBakeService collisionBakeService) {
        try {
            return JoltBootstrap.isAvailable() ? new JoltPhysicsWorld(collisionBakeService) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public JoltPhysicsWorld(CollisionBakeService collisionBakeService) {
        JoltBootstrap.ensureInitialized();
        this.collisionBakeService = collisionBakeService;

        var bpLayers = new BroadPhaseLayerInterfaceTable(2, 2)
                .mapObjectToBroadPhaseLayer(JoltBodyFactory.LAYER_STATIC, 0)
                .mapObjectToBroadPhaseLayer(JoltBodyFactory.LAYER_MOVING, 1);

        var layerPairs = new ObjectLayerPairFilterTable(2)
                .enableCollision(JoltBodyFactory.LAYER_MOVING, JoltBodyFactory.LAYER_MOVING)
                .enableCollision(JoltBodyFactory.LAYER_MOVING, JoltBodyFactory.LAYER_STATIC)
                .enableCollision(JoltBodyFactory.LAYER_STATIC, JoltBodyFactory.LAYER_MOVING);

        var objVsBp = new ObjectVsBroadPhaseLayerFilterTable(bpLayers, 2, layerPairs, 2);

        system.init(32_768, 0, 32_768, 32_768, bpLayers, objVsBp, layerPairs);
        system.setGravity(0f, -GRAVITY, 0f);
        bodies = system.getBodyInterface();

        contactCollector = new ContactCollector(bodyToNode, pendingAdded, activeContacts);
        system.setContactListener(contactCollector);
    }

    @Override
    public void step(float dt) {
        if (dt <= 0f) return;
        synchronized (pendingAdded) {
            previousContacts.clear();
            previousContacts.addAll(activeContacts);
            activeContacts.clear();
            pendingAdded.clear();
        }
        try { system.update(dt, 1, tempAllocator, jobs); } catch (Throwable ignored) {}
        synchronized (pendingAdded) {
            collisionEvents.clear();
            collisionEvents.addAll(pendingAdded);
        }
    }

    @Override
    public BodyHandle addStaticBody(CollisionShape shape, double x, double y, double z,
                                    float rxDeg, float ryDeg, float rzDeg) {
        int id = JoltBodyFactory.createFromCollisionShape(bodies, shape, x, y, z, rxDeg, ryDeg, rzDeg,
                EMotionType.Static, JoltBodyFactory.LAYER_STATIC, 0f, 0f, 0f, 1f,
                CollisionLayerMask.DEFAULT_LAYER, CollisionLayerMask.DEFAULT_MASK);
        return id == Jolt.cInvalidBodyId ? BodyHandle.INVALID : new BodyHandle(id);
    }

    @Override
    public BodyHandle addDynamicBody(CollisionShape shape, double x, double y, double z,
                                     float rxDeg, float ryDeg, float rzDeg, float mass) {
        int id = JoltBodyFactory.createFromCollisionShape(bodies, shape, x, y, z, rxDeg, ryDeg, rzDeg,
                EMotionType.Dynamic, JoltBodyFactory.LAYER_MOVING, mass, 0.1f, 0.1f, 1f,
                CollisionLayerMask.DEFAULT_LAYER, CollisionLayerMask.DEFAULT_MASK);
        return id == Jolt.cInvalidBodyId ? BodyHandle.INVALID : new BodyHandle(id);
    }

    @Override
    public void removeBody(BodyHandle handle) {
        if (handle == null || !handle.isValid()) return;
        try { bodies.removeBody(handle.id());  } catch (Exception ignored) {}
        try { bodies.destroyBody(handle.id()); } catch (Exception ignored) {}
    }

    @Override
    public Optional<RaycastResult> raycast(double ox, double oy, double oz,
                                           double dx, double dy, double dz, double maxDist) {
        if (maxDist <= 0.0) return Optional.empty();
        try {
            try (var ray = new RRayCast(new RVec3(ox, oy, oz),
                    new Vec3((float) (dx * maxDist), (float) (dy * maxDist), (float) (dz * maxDist)));
                 var hit = new RayCastResult()) {
                if (!system.getNarrowPhaseQuery().castRay(ray, hit)) return Optional.empty();
                float f = hit.getFraction();
                return Optional.of(new RaycastResult(
                        ox + dx * maxDist * f, oy + dy * maxDist * f, oz + dz * maxDist * f,
                        0.0, 0.0, 0.0, maxDist * f, new BodyHandle(hit.getBodyId())));
            }
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private Optional<RaycastResult> raycastFiltered(double ox, double oy, double oz,
                                                    double dx, double dy, double dz, double maxDist,
                                                    int rayLayerBits, int rayMaskBits) {
        if (maxDist <= 0.0) return Optional.empty();
        int layerBits = CollisionLayerMask.clampBits(rayLayerBits);
        int maskBits = CollisionLayerMask.clampBits(rayMaskBits);
        if (layerBits == 0 || maskBits == 0) {
            return Optional.empty();
        }
        try {
            try (var ray = new RRayCast(new RVec3(ox, oy, oz),
                    new Vec3((float) (dx * maxDist), (float) (dy * maxDist), (float) (dz * maxDist)));
                 var settings = new RayCastSettings();
                 var collector = new AllHitCastRayCollector()) {
                system.getNarrowPhaseQuery().castRay(ray, settings, collector);
                collector.sort();

                int hits = collector.countHits();
                for (int i = 0; i < hits; i++) {
                    RayCastResult hit = collector.get(i);
                    int bodyId = hit.getBodyId();
                    long userData = bodies.getUserData(bodyId);
                    int otherLayer = CollisionLayerMask.clampBits(CollisionLayerMask.unpackLayer(userData));
                    int otherMask = CollisionLayerMask.clampBits(CollisionLayerMask.unpackMask(userData));
                    if (!CollisionLayerMask.canCollide(layerBits, maskBits, otherLayer, otherMask)) {
                        continue;
                    }
                    float f = hit.getFraction();
                    return Optional.of(new RaycastResult(
                            ox + dx * maxDist * f, oy + dy * maxDist * f, oz + dz * maxDist * f,
                            0.0, 0.0, 0.0, maxDist * f, new BodyHandle(bodyId)));
                }

                return Optional.empty();
            }
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    @Override
    public List<BodyHandle> overlapSphere(double x, double y, double z, double radius) {
        if (radius <= 0.0) return List.of();
        try {
            try (var collector = new AllHitCollideShapeBodyCollector()) {
                system.getBroadPhaseQuery().collideSphere(
                        new Vec3((float) x, (float) y, (float) z), (float) radius, collector);
                int count = collector.countHits();
                if (count == 0) return List.of();
                var result = new ArrayList<BodyHandle>(count);
                for (int i = 0; i < count; i++) result.add(new BodyHandle(collector.get(i)));
                return List.copyOf(result);
            }
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    @Override
    public void close() {
        destroyDynamicBodies();
        destroyStaticBodies();
        safeClose(contactCollector);
        safeClose(shapeFilter);
        safeClose(bodyFilter);
        safeClose(jobs);
        safeClose(tempAllocator);
        try { system.forgetMe(); } catch (Exception ignored) {}
        safeClose(system);
    }

    public void syncStaticColliders(Engine engine) {
        if (engine == null) return;
        long rev = engine.physicsRevision();
        if (rev == lastPhysicsRevision) return;

        long now = System.nanoTime();
        if (rev != pendingPhysicsRevision) {
            pendingPhysicsRevision = rev;
            pendingPhysicsSinceNs  = now;
            return;
        }
        if (now - pendingPhysicsSinceNs < PHYSICS_REBUILD_DEBOUNCE_NS) {
            return;
        }

        lastPhysicsRevision    = rev;
        pendingPhysicsRevision = Long.MIN_VALUE;

        destroyStaticBodies();
        destroyDynamicBodies();
        SceneTree tree = engine.sceneTree();
        if (tree == null || tree.root() == null) return;
        collectBodies(tree.root(), Transform.IDENTITY, engine, false);
        system.optimizeBroadPhase();
    }

    public void syncCollisionFilters(Engine engine) {
        if (engine == null) return;
        long rev = engine.collisionFilterRevision();
        if (rev == lastFilterRevision) return;
        lastFilterRevision = rev;

        SceneTree tree = engine.sceneTree();
        if (tree == null) return;
        syncCollisionFilters(tree, staticBodyIds);
        syncCollisionFilters(tree, dynamicBodyIds);
    }

    public void writeDynamicBodiesBack(Engine engine) {
        if (engine == null || dynamicBodyIds.isEmpty()) return;
        SceneTree tree = engine.sceneTree();
        if (tree == null) return;
        for (int bodyId : dynamicBodyIds) {
            Long nodeId = bodyToNode.get(bodyId);
            if (nodeId == null || nodeId <= 0L) continue;
            Node node = tree.getNode(nodeId);
            if (node == null) continue;
            try {
                if (!bodies.isActive(bodyId)) continue;
                RVec3 pos = bodies.getPosition(bodyId);
                var rot = bodies.getRotation(bodyId);
                node.setProperty("x", ParseUtils.trimFloat((float) pos.x()));
                node.setProperty("y", ParseUtils.trimFloat((float) pos.y()));
                node.setProperty("z", ParseUtils.trimFloat((float) pos.z()));
                var euler = new Quat(rot.getX(), rot.getY(), rot.getZ(), rot.getW()).toEulerDeg();
                node.setProperty("rx", ParseUtils.trimFloat((float) euler.x()));
                node.setProperty("ry", ParseUtils.trimFloat((float) euler.y()));
                node.setProperty("rz", ParseUtils.trimFloat((float) euler.z()));
            } catch (Throwable ignored) {}
        }
    }

    public void tickRaycasts(Engine engine) {
        if (engine == null) {
            return;
        }
        SceneTree tree = engine.sceneTree();
        if (tree == null || tree.root() == null) {
            return;
        }
        tickRaycasts(tree.root(), Transform.IDENTITY, engine);
    }

    public List<CollisionEvent> consumeCollisionEvents() {
        return collisionEvents.isEmpty() ? List.of() : List.copyOf(collisionEvents);
    }

    public Set<Long> currentContactPairs() {
        synchronized (pendingAdded) { return Set.copyOf(activeContacts); }
    }

    public Set<Long> previousContactPairs() {
        return Set.copyOf(previousContacts);
    }

    public Long  nodeIdForBody(int bodyId)  { return bodyToNode.get(bodyId); }
    public Integer bodyIdForNode(long nodeId) { return nodeToBody.get(nodeId); }

    public List<Long> activeDynamicNodeIds() {
        if (dynamicBodyIds.isEmpty()) {
            return List.of();
        }
        ArrayList<Long> out = new ArrayList<>(dynamicBodyIds.size());
        for (int bodyId : dynamicBodyIds) {
            try {
                if (!bodies.isActive(bodyId)) {
                    continue;
                }
            } catch (Throwable ignored) {
            }
            Long nodeId = bodyToNode.get(bodyId);
            if (nodeId != null && nodeId > 0L) {
                out.add(nodeId);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    public List<Long> dynamicNodeIds() {
        if (dynamicBodyIds.isEmpty()) {
            return List.of();
        }
        ArrayList<Long> out = new ArrayList<>(dynamicBodyIds.size());
        for (int bodyId : dynamicBodyIds) {
            Long nodeId = bodyToNode.get(bodyId);
            if (nodeId != null && nodeId > 0L) {
                out.add(nodeId);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    public void applyForce(long nodeId, float fx, float fy, float fz) {
        Integer id = nodeToBody.get(nodeId);
        if (id == null) return;
        try { bodies.addForce(id, new Vec3(fx, fy, fz)); } catch (Throwable ignored) {}
    }

    public void applyImpulse(long nodeId, float fx, float fy, float fz) {
        Integer id = nodeToBody.get(nodeId);
        if (id == null) return;
        try { bodies.addImpulse(id, new Vec3(fx, fy, fz)); } catch (Throwable ignored) {}
    }

    public void setLinearVelocity(long nodeId, float vx, float vy, float vz) {
        Integer id = nodeToBody.get(nodeId);
        if (id == null) return;
        try { bodies.setLinearVelocity(id, vx, vy, vz); } catch (Throwable ignored) {}
    }

    public float[] getLinearVelocity(long nodeId) {
        Integer id = nodeToBody.get(nodeId);
        if (id == null) return new float[]{0, 0, 0};
        try {
            Vec3 v = bodies.getLinearVelocity(id);
            return new float[]{v.getX(), v.getY(), v.getZ()};
        } catch (Throwable ignored) {
            return new float[]{0, 0, 0};
        }
    }

    public Optional<SweepHit> sweepCharacter(Node node, double x, double y, double z,
                                             double dx, double dy, double dz) {
        if (node == null) {
            return Optional.empty();
        }
        double lenSq = dx * dx + dy * dy + dz * dz;
        if (lenSq <= 1.0e-10) {
            return Optional.empty();
        }

        int layerBits = CollisionLayerMask.layer(node);
        int maskBits = CollisionLayerMask.mask(node);
        float radius = Math.max(0.05f, ParseUtils.parseFloat(node.getProperty("radius"), 0.5f));
        float height = Math.max(radius * 2.0f, ParseUtils.parseFloat(node.getProperty("height"), 2.0f));
        float halfHeightOfCylinder = Math.max(MIN_CAPSULE_HALF_HEIGHT, height * 0.5f - radius);
        double centerY = y + height * 0.5;

        var rotation = new com.github.stephengold.joltjni.Quat(0f, 0f, 0f, 1f);
        try (var shape = new CapsuleShape(halfHeightOfCylinder, radius);
             var start = RMat44.sRotationTranslation(rotation, new RVec3(x, centerY, z));
             var cast = new RShapeCast(shape, new Vec3(1f, 1f, 1f), start, new Vec3((float) dx, (float) dy, (float) dz));
             var settings = new ShapeCastSettings();
             var collector = new AllHitCastShapeCollector()) {
            settings.setUseShrunkenShapeAndConvexRadius(false);
            settings.setReturnDeepestPoint(true);
            system.getNarrowPhaseQuery().castShape(cast, settings, RVec3.sZero(), collector);
            collector.sort();

            int hits = collector.countHits();
            for (int i = 0; i < hits; i++) {
                try (ShapeCastResult hit = collector.get(i)) {
                    if (hit == null) {
                        continue;
                    }
                    int bodyId = hit.getBodyId2();
                    if (bodyId == Jolt.cInvalidBodyId) {
                        continue;
                    }
                    long userData;
                    try {
                        userData = bodies.getUserData(bodyId);
                    } catch (Throwable ignored) {
                        continue;
                    }
                    int otherLayer = CollisionLayerMask.clampBits(CollisionLayerMask.unpackLayer(userData));
                    int otherMask = CollisionLayerMask.clampBits(CollisionLayerMask.unpackMask(userData));
                    if (!CollisionLayerMask.canCollide(layerBits, maskBits, otherLayer, otherMask)) {
                        continue;
                    }

                    double px = 0.0, py = 0.0, pz = 0.0;
                    Vec3 point = hit.getContactPointOn2();
                    if (point != null) {
                        px = point.getX();
                        py = point.getY();
                        pz = point.getZ();
                    }

                    double nx = 0.0, ny = 0.0, nz = 0.0;
                    Vec3 axis = hit.getPenetrationAxis();
                    if (axis != null) {
                        nx = axis.getX();
                        ny = axis.getY();
                        nz = axis.getZ();
                    }
                    double normalLenSq = nx * nx + ny * ny + nz * nz;
                    if (normalLenSq <= 1.0e-10) {
                        continue;
                    }
                    double invLen = 1.0 / Math.sqrt(normalLenSq);
                    nx *= invLen;
                    ny *= invLen;
                    nz *= invLen;
                    if (nx * dx + ny * dy + nz * dz > 0.0) {
                        nx = -nx;
                        ny = -ny;
                        nz = -nz;
                    }

                    Long nodeId = bodyToNode.get(bodyId);
                    return Optional.of(new SweepHit(
                            hit.getFraction(),
                            px, py, pz,
                            nx, ny, nz,
                            nodeId == null ? 0L : nodeId
                    ));
                }
            }
            return Optional.empty();
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private void collectBodies(Node node, Transform parentWorld, Engine engine, boolean physicsAncestor) {
        if (node == null || engine == null) return;
        String typeId = engine.nodeTypes().typeIdFor(node);
        Transform local = localTransform(node, typeId);
        Transform world = shouldInheritTransform(node) ? parentWorld.compose(local) : local;

        boolean isPhysicsNode = isPhysicsNode(typeId);

        if (!physicsAncestor && isStaticColliderNode(typeId, node)) {
            int id = JoltBodyFactory.createStaticBox(bodies, node, world);
            if (id != 0 && id != Jolt.cInvalidBodyId) {
                staticBodyIds.add(id);
                bodyToNode.put(id, node.nodeId());
            }

        } else if (!physicsAncestor && isStaticGeometryColliderNode(typeId, node)) {
            int id = JoltBodyFactory.createStaticGeometryBody(bodies, node, typeId, world, engine, collisionBakeService);
            if (id != 0 && id != Jolt.cInvalidBodyId) {
                staticBodyIds.add(id);
                bodyToNode.put(id, node.nodeId());
            }

        } else if ("RigidBody3D".equals(typeId) && propBool(node, "enabled", true)) {
            int id = JoltBodyFactory.createRigidBody(bodies, node, typeId, world, engine, collisionBakeService);
            if (id != 0 && id != Jolt.cInvalidBodyId) {
                long nodeId = node.nodeId();
                dynamicBodyIds.add(id);
                bodyToNode.put(id, nodeId);
                nodeToBody.put(nodeId, id);
            }
        } else if ("StaticBody3D".equals(typeId) && propBool(node, "enabled", true)) {
            int id = JoltBodyFactory.createStaticFromShape(bodies, node, typeId, world, engine, collisionBakeService);
            if (id != 0 && id != Jolt.cInvalidBodyId) {
                staticBodyIds.add(id);
                bodyToNode.put(id, node.nodeId());
            }
        }
        boolean nextPhysicsAncestor = physicsAncestor || isPhysicsNode;
        for (Node child : node.children()) collectBodies(child, world, engine, nextPhysicsAncestor);
    }

    private void tickRaycasts(Node node, Transform parentWorld, Engine engine) {
        if (node == null || engine == null) {
            return;
        }
        String typeId = engine.nodeTypes().typeIdFor(node);
        Transform local = localTransform(node, typeId);
        Transform world = shouldInheritTransform(node) ? parentWorld.compose(local) : local;

        if ("Raycast3D".equals(typeId) && propBool(node, "enabled", true)) {
            float tx = ParseUtils.parseFloat(node.getProperty("target_x"), 0f);
            float ty = ParseUtils.parseFloat(node.getProperty("target_y"), -1f);
            float tz = ParseUtils.parseFloat(node.getProperty("target_z"), 0f);
            float maxDistance = ParseUtils.parseFloat(node.getProperty("max_distance"), 100f);

            Vec3d localDir = new Vec3d(tx, ty, tz);
            double lx = localDir.x();
            double ly = localDir.y();
            double lz = localDir.z();
            double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
            if (!(Double.isFinite(len)) || len < 1e-8) {
                localDir = new Vec3d(0.0, -1.0, 0.0);
                len = 1.0;
            }
            double invLen = 1.0 / len;
            Vec3d unitLocal = new Vec3d(localDir.x() * invLen, localDir.y() * invLen, localDir.z() * invLen);
            Vec3d dir = world.rot().rotate(unitLocal);

            int layerBits = CollisionLayerMask.layer(node);
            int maskBits = CollisionLayerMask.mask(node);

            var hit = raycastFiltered(
                    world.pos().x(), world.pos().y(), world.pos().z(),
                    dir.x(), dir.y(), dir.z(),
                    Math.max(0.0, maxDistance),
                    layerBits, maskBits)
                    .orElse(null);
            if (hit != null) {
                long hitNode = 0L;
                BodyHandle body = hit.body();
                if (body != null && body.isValid()) {
                    Long mapped = bodyToNode.get(body.id());
                    hitNode = mapped == null ? 0L : mapped;
                }
                setPropIfChanged(node, "is_colliding", "true");
                setPropIfChanged(node, "hit_x", ParseUtils.trimFloat((float) hit.hitX()));
                setPropIfChanged(node, "hit_y", ParseUtils.trimFloat((float) hit.hitY()));
                setPropIfChanged(node, "hit_z", ParseUtils.trimFloat((float) hit.hitZ()));
                setPropIfChanged(node, "hit_node_id", Long.toString(hitNode));
            } else {
                setPropIfChanged(node, "is_colliding", "false");
                setPropIfChanged(node, "hit_x", "0");
                setPropIfChanged(node, "hit_y", "0");
                setPropIfChanged(node, "hit_z", "0");
                setPropIfChanged(node, "hit_node_id", "0");
            }
        }

        for (Node child : node.children()) {
            tickRaycasts(child, world, engine);
        }
    }

    private static void setPropIfChanged(Node node, String key, String value) {
        if (node == null || key == null || value == null) {
            return;
        }
        if (Objects.equals(node.getProperty(key), value)) {
            return;
        }
        node.setProperty(key, value);
    }

    private void destroyBodies(List<Integer> ids, boolean clearNodeMaps) {
        for (int id : ids) {
            try { bodies.removeBody(id);  } catch (Exception ignored) {}
            try { bodies.destroyBody(id); } catch (Exception ignored) {}
            if (clearNodeMaps) {
                Long nodeId = bodyToNode.remove(id);
                if (nodeId != null) nodeToBody.remove(nodeId);
            } else {
                bodyToNode.remove(id);
            }
        }
        ids.clear();
    }

    private void destroyStaticBodies() {
        destroyBodies(staticBodyIds, false);
    }

    private void destroyDynamicBodies() {
        destroyBodies(dynamicBodyIds, true);
        synchronized (pendingAdded) {
            activeContacts.clear();
            previousContacts.clear();
            collisionEvents.clear();
            pendingAdded.clear();
        }
    }

    private void syncCollisionFilters(SceneTree tree, List<Integer> bodyIds) {
        if (tree == null || bodyIds == null || bodyIds.isEmpty()) return;
        for (int bodyId : bodyIds) {
            Long nodeId = bodyToNode.get(bodyId);
            if (nodeId == null) continue;
            Node node = tree.getNode(nodeId);
            if (node == null) continue;
            long packed = CollisionLayerMask.packUserData(
                    CollisionLayerMask.layer(node),
                    CollisionLayerMask.mask(node));
            try {
                if (bodies.getUserData(bodyId) == packed) continue;
                bodies.setUserData(bodyId, packed);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isStaticColliderNode(String typeId, Node node) {
        if (typeId == null || node == null) return false;
        if (!"CSGBlock".equals(typeId) && !"CSGBox".equals(typeId)) return false;
        String solid = node.getProperty("solid");
        if (solid == null || solid.isBlank()) return true;
        String s = solid.trim().toLowerCase();
        return !"false".equals(s) && !"0".equals(s);
    }

    private static boolean isStaticGeometryColliderNode(String typeId, Node node) {
        if (typeId == null || node == null) {
            return false;
        }
        if (!"Model3D".equals(typeId)
                && !"MeshInstance3D".equals(typeId)
                && !"Sprite3D".equals(typeId)
                && !"AnimatedSprite3D".equals(typeId)) {
            return false;
        }
        String solid = node.getProperty("solid");
        if (solid == null || solid.isBlank()) {
            // matches CSGBox default
            return true;
        }
        String s = solid.trim().toLowerCase();
        return !"false".equals(s) && !"0".equals(s);
    }

    private static boolean isPhysicsNode(String typeId) {
        if (typeId == null) {
            return false;
        }
        return "RigidBody3D".equals(typeId)
                || "StaticBody3D".equals(typeId)
                || "CharacterBody3D".equals(typeId)
                || "Area3D".equals(typeId)
                || "Raycast3D".equals(typeId);
    }

    private static boolean shouldInheritTransform(Node node) {
        if (node == null) return true;
        String v = node.getProperty("@inherit_transform");
        if (v == null || v.isBlank()) return true;
        String s = v.trim().toLowerCase();
        return !"false".equals(s) && !"0".equals(s);
    }

    private static Transform localTransform(Node node, String typeId) {
        if (node == null) return Transform.IDENTITY;
        float x = ParseUtils.parseFloat(node.getProperty("x"), 0f);
        float y = ParseUtils.parseFloat(node.getProperty("y"), 0f);
        float z = ParseUtils.parseFloat(node.getProperty("z"), 0f);
        QuatD rot = QuatD.fromEulerDeg(
                ParseUtils.parseFloat(node.getProperty("rx"), 0f),
                ParseUtils.parseFloat(node.getProperty("ry"), 0f),
                ParseUtils.parseFloat(node.getProperty("rz"), 0f));

        boolean hasScale = node.getProperty("sx") != null
                || node.getProperty("sy") != null
                || node.getProperty("sz") != null;
        double sx = hasScale ? safeScale(ParseUtils.parseFloat(node.getProperty("sx"), 1f)) : 1.0;
        double sy = hasScale ? safeScale(ParseUtils.parseFloat(node.getProperty("sy"), 1f)) : 1.0;
        double sz = hasScale ? safeScale(ParseUtils.parseFloat(node.getProperty("sz"), 1f)) : 1.0;

        boolean pivotIsMinCorner = "CSGBox".equals(typeId) || "CSGBlock".equals(typeId);
        double px = pivotIsMinCorner && hasScale ? x + sx * 0.5 : x;
        double py = pivotIsMinCorner && hasScale ? y + sy * 0.5 : y;
        double pz = pivotIsMinCorner && hasScale ? z + sz * 0.5 : z;
        return new Transform(new Vec3d(px, py, pz), rot, new Vec3d(sx, sy, sz));
    }

    private static boolean propBool(Node node, String key, boolean fallback) {
        String v = node.getProperty(key);
        if (v == null || v.isBlank()) return fallback;
        String s = v.trim().toLowerCase();
        if ("false".equals(s) || "0".equals(s)) return false;
        if ("true".equals(s)  || "1".equals(s)) return true;
        return fallback;
    }

    private static double safeScale(double value) {
        if (!Double.isFinite(value)) return 1.0;
        return Math.abs(value) < 1e-6 ? 1e-6 : value;
    }

    private static void safeClose(AutoCloseable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignored) {}
    }

    record Transform(Vec3d pos, QuatD rot, Vec3d scale) {
        static final Transform IDENTITY = new Transform(new Vec3d(0, 0, 0), QuatD.IDENTITY, new Vec3d(1, 1, 1));

        Transform compose(Transform child) {
            Vec3d scaled   = new Vec3d(child.pos.x * scale.x, child.pos.y * scale.y, child.pos.z * scale.z);
            Vec3d worldPos = pos.add(rot.rotate(scaled));
            return new Transform(worldPos, rot.mul(child.rot).normalized(), scale.mul(child.scale));
        }
    }

    record Vec3d(double x, double y, double z) {
        Vec3d add(Vec3d o) { return new Vec3d(x + o.x, y + o.y, z + o.z); }
        Vec3d mul(Vec3d o) { return new Vec3d(x * o.x, y * o.y, z * o.z); }
    }

    record QuatD(double x, double y, double z, double w) {
        static final QuatD IDENTITY = new QuatD(0, 0, 0, 1);

        static QuatD fromEulerDeg(float rxDeg, float ryDeg, float rzDeg) {
            double hx = Math.toRadians(Float.isFinite(rxDeg) ? rxDeg : 0f) * 0.5;
            double hy = Math.toRadians(Float.isFinite(ryDeg) ? ryDeg : 0f) * 0.5;
            double hz = Math.toRadians(Float.isFinite(rzDeg) ? rzDeg : 0f) * 0.5;
            return new QuatD(Math.sin(hx), 0, 0, Math.cos(hx))
                    .mul(new QuatD(0, Math.sin(hy), 0, Math.cos(hy)))
                    .mul(new QuatD(0, 0, Math.sin(hz), Math.cos(hz)))
                    .normalized();
        }

        QuatD mul(QuatD b) {
            return new QuatD(
                    w*b.x + x*b.w + y*b.z - z*b.y,
                    w*b.y - x*b.z + y*b.w + z*b.x,
                    w*b.z + x*b.y - y*b.x + z*b.w,
                    w*b.w - x*b.x - y*b.y - z*b.z);
        }

        QuatD normalized() {
            double n = Math.sqrt(x*x + y*y + z*z + w*w);
            if (n <= 0.0) return IDENTITY;
            double inv = 1.0 / n;
            return new QuatD(x*inv, y*inv, z*inv, w*inv);
        }

        Vec3d rotate(Vec3d v) {
            double tx = 2.0 * (y*v.z - z*v.y);
            double ty = 2.0 * (z*v.x - x*v.z);
            double tz = 2.0 * (x*v.y - y*v.x);
            return new Vec3d(v.x + w*tx + (y*tz - z*ty),
                    v.y + w*ty + (z*tx - x*tz),
                    v.z + w*tz + (x*ty - y*tx));
        }
    }

    public record SweepHit(double fraction, double x, double y, double z,
                           double nx, double ny, double nz, long nodeId) {
    }
}
