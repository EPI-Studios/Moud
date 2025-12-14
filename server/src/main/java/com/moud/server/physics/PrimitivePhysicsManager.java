package com.moud.server.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.server.logging.MoudLogger;
import com.moud.server.primitives.PrimitiveInstance;
import net.minestom.server.instance.Instance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PrimitivePhysicsManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(PrimitivePhysicsManager.class);
    private static final PrimitivePhysicsManager INSTANCE = new PrimitivePhysicsManager();
    private static final float MIN_HALF_EXTENT = 0.051f;
    private final Map<Long, Body> bodies = new ConcurrentHashMap<>();

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
        BodyCreationSettings settings = new BodyCreationSettings()
                .setMotionType(EMotionType.Kinematic)
                .setObjectLayer(PhysicsService.LAYER_STATIC)
                .setShape(shape)
                .setPosition(new com.github.stephengold.joltjni.RVec3(prim.getPosition().x, prim.getPosition().y, prim.getPosition().z));
        Quaternion rot = prim.getRotation();
        if (rot != null) {
            Quaternion norm = rot.normalize();
            settings.setRotation(new com.github.stephengold.joltjni.Quat(norm.x, norm.y, norm.z, norm.w));
        }
        Body body = PhysicsService.getInstance().getBodyInterface().createBody(settings);
        PhysicsService.getInstance().getBodyInterface().addBody(body, EActivation.Activate);
        bodies.put(prim.getId(), body);
    }

    public void onTransform(PrimitiveInstance prim) {
        Body body = bodies.get(prim.getId());
        if (body == null) {
            return;
        }
        var bi = PhysicsService.getInstance().getBodyInterface();
        Vector3 pos = prim.getPosition();
        Quaternion rot = prim.getRotation();
        com.github.stephengold.joltjni.Quat joltRot;
        if (rot != null) {
            Quaternion norm = rot.normalize();
            joltRot = new com.github.stephengold.joltjni.Quat(norm.x, norm.y, norm.z, norm.w);
        } else {
            joltRot = body.getRotation();
        }
        bi.setPositionAndRotation(body.getId(),
                new com.github.stephengold.joltjni.RVec3(pos.x, pos.y, pos.z),
                joltRot,
                EActivation.Activate);
    }

    public void onRemove(long primitiveId) {
        Body body = bodies.remove(primitiveId);
        if (body != null) {
            var bi = PhysicsService.getInstance().getBodyInterface();
            bi.removeBody(body.getId());
            bi.destroyBody(body.getId());
        }
    }

    public void clear() {
        var bi = PhysicsService.getInstance().getBodyInterface();
        bodies.values().forEach(b -> {
            bi.removeBody(b.getId());
            bi.destroyBody(b.getId());
        });
        bodies.clear();
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
                return new CapsuleShape(radius, height * 0.5f);
            }
            case CYLINDER -> {
                float radius = Math.max(Math.max(Math.abs(scale.x), Math.abs(scale.z)) * 0.5f, MIN_HALF_EXTENT);
                float height = Math.max(Math.abs(scale.y), 0.05f);
                return new CapsuleShape(radius, (height - radius * 2) * 0.5f);
            }
            case CONE -> {
                return buildConeHull(scale);
            }
            case LINE, LINE_STRIP -> {
                return null;
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
}