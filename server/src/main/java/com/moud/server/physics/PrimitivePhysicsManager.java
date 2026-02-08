package com.moud.server.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.CapsuleShape;
import com.github.stephengold.joltjni.CollisionGroup;
import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.MeshShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.Triangle;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.primitives.PrimitiveType;
import com.moud.server.instance.InstanceManager;
import com.moud.server.logging.MoudLogger;
import com.moud.server.primitives.PrimitiveInstance;
import com.moud.server.primitives.PrimitiveServiceImpl;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PrimitivePhysicsManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(PrimitivePhysicsManager.class);
    private static final PrimitivePhysicsManager INSTANCE = new PrimitivePhysicsManager();
    private static final float MIN_HALF_EXTENT = 0.051f;
    private static final long DYNAMIC_BROADCAST_INTERVAL_MS = 50;
    private static final double POSITION_EPSILON_SQ = 1.0e-6;
    private static final double ROTATION_DOT_EPSILON = 0.99999;
    private final Map<Long, Body> bodies = new ConcurrentHashMap<>();
    private final Set<Long> dynamicBodies = ConcurrentHashMap.newKeySet();
    private final Map<Long, LastTransform> lastBroadcast = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastBroadcastMs = new ConcurrentHashMap<>();

    private PrimitivePhysicsManager() {
    }

    public static PrimitivePhysicsManager getInstance() {
        return INSTANCE;
    }

    public void onCreate(PrimitiveInstance prim, Instance instance) {
        if (prim == null || instance == null) return;
        ConstShape shape = createShapeFor(prim);
        if (shape == null) {
            LOGGER.debug("No shape created for primitive {}", prim.getId());
            return;
        }
        boolean wantsDynamic = prim.isPhysicsDynamic()
                && prim.getType() != PrimitiveType.MESH
                && prim.getType() != PrimitiveType.LINE
                && prim.getType() != PrimitiveType.LINE_STRIP;
        EMotionType motionType;
        if (prim.getType() == com.moud.plugin.api.services.primitives.PrimitiveType.MESH) {
            motionType = EMotionType.Static;
        } else if (wantsDynamic) {
            motionType = EMotionType.Dynamic;
        } else {
            motionType = EMotionType.Kinematic;
        }
        if (prim.getType() == PrimitiveType.MESH) {
            int vertexCount = prim.getVertices() != null ? prim.getVertices().size() : 0;
            int indexCount = prim.getIndices() != null ? prim.getIndices().size() : 0;
            int triangleCount = indexCount >= 3 ? (indexCount / 3) : (vertexCount / 3);
            LOGGER.info("Creating MESH primitive physics body id={} motion={} verts={} indices={} tris={}",
                    prim.getId(), motionType, vertexCount, indexCount, triangleCount);
        }
        BodyCreationSettings settings = new BodyCreationSettings()
                .setMotionType(motionType)
                .setObjectLayer(motionType == EMotionType.Dynamic ? PhysicsService.LAYER_DYNAMIC : PhysicsService.LAYER_STATIC)
                .setShape(shape)
                .setPosition(new RVec3(prim.getPosition().x, prim.getPosition().y, prim.getPosition().z));
        settings.setCollisionGroup(PhysicsService.getInstance().collisionGroupForInstance(instance));
        Quaternion rot = prim.getRotation();
        if (rot != null) {
            Quaternion norm = rot.normalize();
            settings.setRotation(new Quat(norm.x, norm.y, norm.z, norm.w));
        }
        if (motionType == EMotionType.Dynamic) {
            settings.setLinearDamping(0.08f).setAngularDamping(0.08f);
            Vector3 scale = prim.getScale() != null ? prim.getScale() : Vector3.one();
            float sx = Math.max((float) Math.abs(scale.x), MIN_HALF_EXTENT * 2);
            float sy = Math.max((float) Math.abs(scale.y), MIN_HALF_EXTENT * 2);
            float sz = Math.max((float) Math.abs(scale.z), MIN_HALF_EXTENT * 2);
            float mass = prim.getPhysicsMass();
            MassProperties massProperties = new MassProperties();
            massProperties.setMassAndInertiaOfSolidBox(new Vec3(sx, sy, sz), mass);
            settings.setOverrideMassProperties(EOverrideMassProperties.MassAndInertiaProvided);
            settings.setMassPropertiesOverride(massProperties);
        }
        PhysicsService physics = PhysicsService.getInstance();
        physics.executeOnPhysicsThread(() -> {
            Body body = physics.getBodyInterface().createBody(settings);
            EActivation activation = motionType == EMotionType.Static ? EActivation.DontActivate : EActivation.Activate;
            physics.getBodyInterface().addBody(body, activation);
            bodies.put(prim.getId(), body);
            if (motionType == EMotionType.Dynamic) {
                dynamicBodies.add(prim.getId());
            } else {
                dynamicBodies.remove(prim.getId());
            }
            if (prim.getType() == PrimitiveType.MESH) {
                CollisionGroup group = body.getCollisionGroup();
                LOGGER.info("Created MESH primitive physics body id={} body_id={} motion={} layer={} subgroup={}",
                        prim.getId(),
                        body.getId(),
                        body.getMotionType(),
                        body.getObjectLayer(),
                        group != null ? group.getSubGroupId() : -1);
            }
        });
    }

    public void onTransform(PrimitiveInstance prim) {
        if (prim == null) {
            return;
        }
        if (prim.getType() == PrimitiveType.MESH) {
            onGeometryChanged(prim, InstanceManager.getInstance().getDefaultInstance());
            return;
        }
        PhysicsService physics = PhysicsService.getInstance();
        physics.executeOnPhysicsThread(() -> {
            Body body = bodies.get(prim.getId());
            if (body == null) {
                return;
            }
            var bi = physics.getBodyInterface();
            Vector3 pos = prim.getPosition();
            Quaternion rot = prim.getRotation();
            Quat joltRot;
            if (rot != null) {
                Quaternion norm = rot.normalize();
                joltRot = new Quat(norm.x, norm.y, norm.z, norm.w);
            } else {
                joltRot = body.getRotation();
            }
            bi.setPositionAndRotation(
                    body.getId(),
                    new RVec3(pos.x, pos.y, pos.z),
                    joltRot,
                    EActivation.Activate
            );
        });
    }

    public void onGeometryChanged(PrimitiveInstance prim, Instance instance) {
        if (prim == null || instance == null) return;
        ConstShape shape = createShapeFor(prim);
        if (shape == null) {
            LOGGER.debug("No shape created for primitive {}", prim.getId());
            return;
        }

        EMotionType motionType = prim.getType() == com.moud.plugin.api.services.primitives.PrimitiveType.MESH
                ? EMotionType.Static
                : EMotionType.Kinematic;
        BodyCreationSettings settings = new BodyCreationSettings()
                .setMotionType(motionType)
                .setObjectLayer(PhysicsService.LAYER_STATIC)
                .setShape(shape)
                .setPosition(new RVec3(prim.getPosition().x, prim.getPosition().y, prim.getPosition().z));
        settings.setCollisionGroup(PhysicsService.getInstance().collisionGroupForInstance(instance));
        Quaternion rot = prim.getRotation();
        if (rot != null) {
            Quaternion norm = rot.normalize();
            settings.setRotation(new Quat(norm.x, norm.y, norm.z, norm.w));
        }

        PhysicsService physics = PhysicsService.getInstance();
        physics.executeOnPhysicsThread(() -> {
            Body body = bodies.remove(prim.getId());
            if (body != null) {
                var bi = physics.getBodyInterface();
                bi.removeBody(body.getId());
                bi.destroyBody(body.getId());
            }

            Body newBody = physics.getBodyInterface().createBody(settings);
            EActivation activation = motionType == EMotionType.Static ? EActivation.DontActivate : EActivation.Activate;
            physics.getBodyInterface().addBody(newBody, activation);
            bodies.put(prim.getId(), newBody);

            if (prim.getType() == com.moud.plugin.api.services.primitives.PrimitiveType.MESH) {
                CollisionGroup group = newBody.getCollisionGroup();
                LOGGER.info("Created MESH primitive physics body id={} body_id={} motion={} layer={} subgroup={}",
                        prim.getId(),
                        newBody.getId(),
                        newBody.getMotionType(),
                        newBody.getObjectLayer(),
                        group != null ? group.getSubGroupId() : -1);
            }
        });
    }

    public void onRemove(long primitiveId) {
        PhysicsService physics = PhysicsService.getInstance();
        physics.executeOnPhysicsThread(() -> {
            Body body = bodies.remove(primitiveId);
            if (body == null) {
                return;
            }
            dynamicBodies.remove(primitiveId);
            lastBroadcast.remove(primitiveId);
            lastBroadcastMs.remove(primitiveId);
            var bi = physics.getBodyInterface();
            bi.removeBody(body.getId());
            bi.destroyBody(body.getId());
        });
    }

    public void clear() {
        PhysicsService physics = PhysicsService.getInstance();
        physics.executeOnPhysicsThread(() -> {
            var bi = physics.getBodyInterface();
            bodies.values().forEach(b -> {
                bi.removeBody(b.getId());
                bi.destroyBody(b.getId());
            });
            bodies.clear();
            dynamicBodies.clear();
            lastBroadcast.clear();
            lastBroadcastMs.clear();
        });
    }

    public void syncDynamicPrimitives() {
        if (dynamicBodies.isEmpty()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        PrimitiveServiceImpl primitives = PrimitiveServiceImpl.getInstance();

        for (Long id : dynamicBodies) {
            Body body = bodies.get(id);
            if (body == null) {
                continue;
            }
            Long lastMs = lastBroadcastMs.get(id);
            if (lastMs != null && nowMs - lastMs < DYNAMIC_BROADCAST_INTERVAL_MS) {
                continue;
            }

            Vector3 pos = toVector3(body.getPosition());
            Quaternion rot = toQuaternion(body.getRotation());
            LastTransform previous = lastBroadcast.get(id);
            if (previous != null) {
                double dx = pos.x - previous.position.x;
                double dy = pos.y - previous.position.y;
                double dz = pos.z - previous.position.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                double dot = Math.abs(rot.x * previous.rotation.x + rot.y * previous.rotation.y + rot.z * previous.rotation.z + rot.w * previous.rotation.w);
                if (distSq < POSITION_EPSILON_SQ && dot > ROTATION_DOT_EPSILON) {
                    continue;
                }
            }

            lastBroadcast.put(id, new LastTransform(pos, rot));
            lastBroadcastMs.put(id, nowMs);
            MinecraftServer.getSchedulerManager().scheduleNextTick(() -> primitives.applyPhysicsTransform(id, pos, rot));
        }
    }

    private record LastTransform(Vector3 position, Quaternion rotation) {
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

    private ConstShape createShapeFor(PrimitiveInstance prim) {
        Vector3 scale = prim.getScale() != null ? prim.getScale() : Vector3.one();
        switch (prim.getType()) {
            case CUBE, PLANE -> {
                float hx = Math.max(Math.abs(scale.x) * 0.5f, MIN_HALF_EXTENT);
                float hy = Math.max(Math.abs(scale.y) * 0.5f, MIN_HALF_EXTENT);
                float hz = Math.max(Math.abs(scale.z) * 0.5f, MIN_HALF_EXTENT);
                return new BoxShape(hx, hy, hz);
            }
            case SPHERE -> {
                float r = (float) (Math.max(Math.max(Math.abs(scale.x), Math.abs(scale.y)), Math.abs(scale.z)) * 0.5);
                r = Math.max(r, MIN_HALF_EXTENT);
                return new BoxShape(r, r, r);
            }
            case CAPSULE -> {
                float radius = Math.max(Math.max(Math.abs(scale.x), Math.abs(scale.z)) * 0.5f, MIN_HALF_EXTENT);
                float height = Math.max(Math.abs(scale.y) - radius * 2, 0.01f);
                return new CapsuleShape(height * 0.5f, radius);
            }
            case CYLINDER -> {
                ConstShape hull = buildCylinderHull(scale);
                if (hull != null) {
                    return hull;
                }
                float hx = Math.max(Math.abs(scale.x) * 0.5f, MIN_HALF_EXTENT);
                float hy = Math.max(Math.abs(scale.y) * 0.5f, MIN_HALF_EXTENT);
                float hz = Math.max(Math.abs(scale.z) * 0.5f, MIN_HALF_EXTENT);
                return new BoxShape(hx, hy, hz);
            }
            case CONE -> {
                return buildConeHull(scale);
            }
            case LINE, LINE_STRIP -> {
                return null;
            }
            case MESH -> {
                return buildMeshShape(prim, scale);
            }
            default -> {
                return null;
            }
        }
    }

    private ConstShape buildConeHull(Vector3 scale) {
        float radius = Math.max(Math.max(Math.abs(scale.x), Math.abs(scale.z)) * 0.5f, MIN_HALF_EXTENT);
        float height = Math.max(Math.abs(scale.y), 0.05f);
        int segments = 12;
        java.util.List<Vec3> verts = new java.util.ArrayList<>();
        verts.add(new Vec3(0, height * 0.5f, 0));
        float baseY = -height * 0.5f;
        for (int i = 0; i < segments; i++) {
            double ang = (Math.PI * 2 * i) / segments;
            verts.add(new Vec3(
                    (float) (Math.cos(ang) * radius),
                    baseY,
                    (float) (Math.sin(ang) * radius)
            ));
        }
        ConvexHullShapeSettings hull = new ConvexHullShapeSettings(verts);
        ShapeResult result = hull.create();
        return result.hasError() ? null : result.get();
    }

    private ConstShape buildCylinderHull(Vector3 scale) {
        float hx = Math.max(Math.abs(scale.x) * 0.5f, MIN_HALF_EXTENT);
        float hy = Math.max(Math.abs(scale.y) * 0.5f, MIN_HALF_EXTENT);
        float hz = Math.max(Math.abs(scale.z) * 0.5f, MIN_HALF_EXTENT);

        int segments = 16;
        java.util.List<Vec3> verts = new java.util.ArrayList<>(segments * 2);
        for (int i = 0; i < segments; i++) {
            double ang = (Math.PI * 2 * i) / segments;
            float x = (float) (Math.cos(ang) * hx);
            float z = (float) (Math.sin(ang) * hz);
            verts.add(new Vec3(x, hy, z));
            verts.add(new Vec3(x, -hy, z));
        }

        ConvexHullShapeSettings hull = new ConvexHullShapeSettings(verts);
        ShapeResult result = hull.create();
        return result.hasError() ? null : result.get();
    }

    private ConstShape buildMeshShape(PrimitiveInstance prim, Vector3 scale) {
        List<Vector3> vertices = prim.getVertices();
        if (vertices == null || vertices.size() < 3) return null;

        float sx = (float) Math.max(Math.abs(scale.x), 1e-3);
        float sy = (float) Math.max(Math.abs(scale.y), 1e-3);
        float sz = (float) Math.max(Math.abs(scale.z), 1e-3);

        List<Triangle> triangles = new ArrayList<>();
        List<Integer> indices = prim.getIndices();
        if (indices != null && indices.size() >= 3) {
            for (int i = 0; i + 2 < indices.size(); i += 3) {
                Integer ia = indices.get(i);
                Integer ib = indices.get(i + 1);
                Integer ic = indices.get(i + 2);
                if (ia == null || ib == null || ic == null) continue;
                if (ia < 0 || ib < 0 || ic < 0) continue;
                if (ia >= vertices.size() || ib >= vertices.size() || ic >= vertices.size()) continue;
                Vector3 a0 = vertices.get(ia);
                Vector3 b0 = vertices.get(ib);
                Vector3 c0 = vertices.get(ic);
                if (a0 == null || b0 == null || c0 == null) continue;
                triangles.add(new Triangle(
                        new Vec3(a0.x * sx, a0.y * sy, a0.z * sz),
                        new Vec3(b0.x * sx, b0.y * sy, b0.z * sz),
                        new Vec3(c0.x * sx, c0.y * sy, c0.z * sz)
                ));
            }
        } else {
            for (int i = 0; i + 2 < vertices.size(); i += 3) {
                Vector3 a0 = vertices.get(i);
                Vector3 b0 = vertices.get(i + 1);
                Vector3 c0 = vertices.get(i + 2);
                if (a0 == null || b0 == null || c0 == null) continue;
                triangles.add(new Triangle(
                        new Vec3(a0.x * sx, a0.y * sy, a0.z * sz),
                        new Vec3(b0.x * sx, b0.y * sy, b0.z * sz),
                        new Vec3(c0.x * sx, c0.y * sy, c0.z * sz)
                ));
            }
        }

        if (triangles.isEmpty()) {
            return null;
        }

        try {
            MeshShapeSettings meshSettings = new MeshShapeSettings(triangles);
            ShapeResult result = meshSettings.create();
            return result.hasError() ? null : result.get();
        } catch (Exception e) {
            LOGGER.debug("Failed to build mesh shape for primitive {}", prim.getId(), e);
            return null;
        }
    }
}
