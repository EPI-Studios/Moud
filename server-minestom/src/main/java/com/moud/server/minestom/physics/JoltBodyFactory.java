package com.moud.server.minestom.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EAllowedDofs;
import com.github.stephengold.joltjni.enumerate.*;
import com.moud.core.physics.CollisionShape;
import com.moud.core.scene.Node;

final class JoltBodyFactory {

    static final int LAYER_STATIC = 0;
    static final int LAYER_MOVING = 1;

    private JoltBodyFactory() {}

    static int createStaticBox(BodyInterface bodies, Node node, JoltPhysicsWorld.Transform world) {
        if (world == null) return 0;
        float sx = (float) Math.max(1e-6, world.scale().x());
        float sy = (float) Math.max(1e-6, world.scale().y());
        float sz = (float) Math.max(1e-6, world.scale().z());
        var shape = new BoxShape(sx * 0.5f, sy * 0.5f, sz * 0.5f);
        try {
            int layerBits = CollisionLayerMask.layer(node);
            int maskBits = CollisionLayerMask.mask(node);
            return addBody(bodies, shape, world, EMotionType.Static, LAYER_STATIC,
                    0f, 0f, 0f, 1f, EAllowedDofs.All, EActivation.DontActivate, layerBits, maskBits);
        } finally { shape.close(); }
    }

    static int createRigidBody(BodyInterface bodies, Node node, JoltPhysicsWorld.Transform world) {
        if (world == null) return 0;
        EMotionType motionType = propBool(node, "freeze", false)
                ? EMotionType.Kinematic : EMotionType.Dynamic;
        Shape shape = resolveShape(node);
        float mass = propFloat(node, "mass", 1f);
        float linearDamp = propFloat(node, "linear_damping", 0.1f);
        float angularDamp = propFloat(node, "angular_damping", 0.1f);
        float gravityScale = propFloat(node, "gravity_scale", 1f);
        boolean lockRotX = propBool(node, "lock_rotation_x", false);
        boolean lockRotY = propBool(node, "lock_rotation_y", false);
        boolean lockRotZ = propBool(node, "lock_rotation_z", false);
        int allowedDofs = EAllowedDofs.All;
        if (lockRotX) allowedDofs &= ~EAllowedDofs.RotationX;
        if (lockRotY) allowedDofs &= ~EAllowedDofs.RotationY;
        if (lockRotZ) allowedDofs &= ~EAllowedDofs.RotationZ;
        try {
            int layerBits = CollisionLayerMask.layer(node);
            int maskBits = CollisionLayerMask.mask(node);
            return addBody(bodies, shape, world, motionType, LAYER_MOVING,
                    mass, linearDamp, angularDamp, gravityScale, allowedDofs,
                    EActivation.Activate, layerBits, maskBits);
        } finally { shape.close(); }
    }

    static int createStaticFromShape(BodyInterface bodies, Node node, JoltPhysicsWorld.Transform world) {
        if (world == null) return 0;
        Shape shape = resolveShape(node);
        try {
            int layerBits = CollisionLayerMask.layer(node);
            int maskBits = CollisionLayerMask.mask(node);
            return addBody(bodies, shape, world, EMotionType.Static, LAYER_STATIC,
                    0f, 0f, 0f, 1f, EAllowedDofs.All, EActivation.DontActivate, layerBits, maskBits);
        } finally { shape.close(); }
    }

    static int createFromCollisionShape(BodyInterface bodies, CollisionShape shape,
                                        double x, double y, double z,
                                        float rxDeg, float ryDeg, float rzDeg,
                                        EMotionType motionType, int layer,
                                        float mass, float linearDamp, float angularDamp,
                                        float gravityScale,
                                        int layerBits,
                                        int maskBits) {
        Shape jolt = toJoltShape(shape);
        if (jolt == null) return Jolt.cInvalidBodyId;
        try {
            JoltPhysicsWorld.QuatD rot = JoltPhysicsWorld.QuatD.fromEulerDeg(rxDeg, ryDeg, rzDeg);
            var settings = new BodyCreationSettings(jolt, new RVec3(x, y, z),
                    new Quat((float) rot.x(), (float) rot.y(), (float) rot.z(), (float) rot.w()),
                    motionType, layer);
            settings.setUserData(CollisionLayerMask.packUserData(layerBits, maskBits));
            applyMassAndDamping(settings, motionType, mass, linearDamp, angularDamp, gravityScale, EAllowedDofs.All);
            EActivation activation = motionType == EMotionType.Static
                    ? EActivation.DontActivate : EActivation.Activate;
            try { return bodies.createAndAddBody(settings, activation); }
            finally { settings.close(); }
        } finally { jolt.close(); }
    }

    static Shape toJoltShape(CollisionShape shape) {
        if (shape == null) return null;
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
        } catch (Throwable ignored) { return null; }
    }

    private static int addBody(BodyInterface bodies, Shape shape, JoltPhysicsWorld.Transform world,
                               EMotionType motionType, int layer,
                               float mass, float linearDamp, float angularDamp, float gravityScale,
                               int allowedDofs,
                               EActivation activation,
                               int layerBits,
                               int maskBits) {
        JoltPhysicsWorld.QuatD rot = world.rot();
        var settings = new BodyCreationSettings(shape,
                new RVec3(world.pos().x(), world.pos().y(), world.pos().z()),
                new Quat((float) rot.x(), (float) rot.y(), (float) rot.z(), (float) rot.w()),
                motionType, layer);
        settings.setUserData(CollisionLayerMask.packUserData(layerBits, maskBits));
        applyMassAndDamping(settings, motionType, mass, linearDamp, angularDamp, gravityScale, allowedDofs);
        try { return bodies.createAndAddBody(settings, activation); }
        finally { settings.close(); }
    }

    private static void applyMassAndDamping(BodyCreationSettings settings, EMotionType motionType,
                                            float mass, float linearDamp, float angularDamp,
                                            float gravityScale, int allowedDofs) {
        if (motionType == EMotionType.Dynamic && mass > 0f) {
            settings.setMassPropertiesOverride(new MassProperties().setMass(mass));
            settings.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);
        }
        settings.setLinearDamping(linearDamp);
        settings.setAngularDamping(angularDamp);
        settings.setGravityFactor(gravityScale);
        if (allowedDofs != EAllowedDofs.All) {
            settings.setAllowedDofs(allowedDofs);
        }
    }

    private static Shape resolveShape(Node node) {
        String type = node.getProperty("shape");
        if (type == null) type = "box";
        type = type.trim().toLowerCase();
        if ("sphere".equals(type)) {
            return new SphereShape(Math.max(0.01f, propFloat(node, "radius", 0.5f)));
        }
        float hx = propFloat(node, "sx", 1f) * 0.5f;
        float hy = propFloat(node, "sy", 1f) * 0.5f;
        float hz = propFloat(node, "sz", 1f) * 0.5f;
        return new BoxShape(Math.max(0.01f, hx), Math.max(0.01f, hy), Math.max(0.01f, hz));
    }

    private static boolean propBool(Node node, String key, boolean fallback) {
        String v = node.getProperty(key);
        if (v == null || v.isBlank()) return fallback;
        String s = v.trim().toLowerCase();
        if ("false".equals(s) || "0".equals(s)) return false;
        if ("true".equals(s) || "1".equals(s)) return true;
        return fallback;
    }

    private static float propFloat(Node node, String key, float fallback) {
        String v = node.getProperty(key);
        if (v == null || v.isBlank()) return fallback;
        try { return Float.parseFloat(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }
}
