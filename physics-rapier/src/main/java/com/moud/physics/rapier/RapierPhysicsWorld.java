package com.moud.physics.rapier;

import com.moud.physics.api.AreaEvent;
import com.moud.physics.api.BodyHandle;
import com.moud.physics.api.CollisionGroups;
import com.moud.physics.api.ContactEvent;
import com.moud.physics.api.DynamicProps;
import com.moud.physics.api.JointHandle;
import com.moud.physics.api.PhysicsWorld;
import com.moud.physics.api.QueryFilter;
import com.moud.physics.api.Quat;
import com.moud.physics.api.RaycastHit;
import com.moud.physics.api.ShapeCastHit;
import com.moud.physics.api.ShapeDesc;
import com.moud.physics.api.Transform;
import com.moud.physics.api.Vec3;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RapierPhysicsWorld implements PhysicsWorld {

    private final MemorySegment world;
    private final Map<ShapeDesc, Long> shapeCache = new HashMap<>();

    public RapierPhysicsWorld() {
        this.world = Rapier3D.worldNew();
    }

    // exposed for things like the character controller that need the raw handle
    public MemorySegment nativeHandle() {
        return world;
    }

    // package-private - lets RapierCharacterController share this world's shape
    // cache instead of keeping its own
    long internShape(ShapeDesc desc) {
        return shapeHandleFor(desc);
    }

    // ---- body lifecycle ----------------------------------------------------

    @Override
    public BodyHandle addStatic(ShapeDesc shape, Transform x, CollisionGroups g) {
        long sh = shapeHandleFor(shape);
        return new BodyHandle(Rapier3D.bodyAddStatic(world, sh,
                x.pos().x(), x.pos().y(), x.pos().z(),
                x.rot().x(), x.rot().y(), x.rot().z(), x.rot().w(),
                g.layer(), g.mask()));
    }

    @Override
    public BodyHandle addDynamic(ShapeDesc shape, Transform x, DynamicProps props, CollisionGroups g) {
        // trimeshes can be static colliders but rapier wont let them be dynamic
        if (shape instanceof ShapeDesc.Trimesh) {
            throw new IllegalArgumentException("Trimesh cannot be a dynamic body");
        }
        long sh = shapeHandleFor(shape);
        return new BodyHandle(Rapier3D.bodyAddDynamic(world, sh,
                x.pos().x(), x.pos().y(), x.pos().z(),
                x.rot().x(), x.rot().y(), x.rot().z(), x.rot().w(),
                g.layer(), g.mask(),
                props.mass(), props.gravityScale(),
                props.linearDamping(), props.angularDamping(),
                props.ccd() ? 1 : 0));
    }

    @Override
    public BodyHandle addKinematic(ShapeDesc shape, Transform x, CollisionGroups g) {
        long sh = shapeHandleFor(shape);
        return new BodyHandle(Rapier3D.bodyAddKinematic(world, sh,
                x.pos().x(), x.pos().y(), x.pos().z(),
                x.rot().x(), x.rot().y(), x.rot().z(), x.rot().w(),
                g.layer(), g.mask()));
    }

    @Override
    public BodyHandle addArea(ShapeDesc shape, Transform x, CollisionGroups g) {
        long sh = shapeHandleFor(shape);
        return new BodyHandle(Rapier3D.bodyAddArea(world, sh,
                x.pos().x(), x.pos().y(), x.pos().z(),
                x.rot().x(), x.rot().y(), x.rot().z(), x.rot().w(),
                g.layer(), g.mask()));
    }

    @Override public void remove(BodyHandle h) { Rapier3D.bodyRemove(world, h.id()); }

    // ---- body manipulation -------------------------------------------------

    @Override
    public void setTransform(BodyHandle h, Transform x) {
        Rapier3D.bodySetXform(world, h.id(),
                x.pos().x(), x.pos().y(), x.pos().z(),
                x.rot().x(), x.rot().y(), x.rot().z(), x.rot().w());
    }

    @Override
    public Transform getTransform(BodyHandle h) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment out = a.allocate(ValueLayout.JAVA_FLOAT, 7);
            Rapier3D.bodyGetXform(world, h.id(), out);
            return new Transform(
                    new Vec3(out.getAtIndex(ValueLayout.JAVA_FLOAT, 0),
                             out.getAtIndex(ValueLayout.JAVA_FLOAT, 1),
                             out.getAtIndex(ValueLayout.JAVA_FLOAT, 2)),
                    new Quat(out.getAtIndex(ValueLayout.JAVA_FLOAT, 3),
                             out.getAtIndex(ValueLayout.JAVA_FLOAT, 4),
                             out.getAtIndex(ValueLayout.JAVA_FLOAT, 5),
                             out.getAtIndex(ValueLayout.JAVA_FLOAT, 6)));
        }
    }

    @Override public void applyForce(BodyHandle h, Vec3 f)        { Rapier3D.bodyApplyForce  (world, h.id(), f.x(), f.y(), f.z()); }
    @Override public void applyImpulse(BodyHandle h, Vec3 j)      { Rapier3D.bodyApplyImpulse(world, h.id(), j.x(), j.y(), j.z()); }
    @Override public void setLinearVelocity(BodyHandle h, Vec3 v) { Rapier3D.bodySetLinvel  (world, h.id(), v.x(), v.y(), v.z()); }
    @Override public void sleep(BodyHandle h)                     { Rapier3D.bodySleep      (world, h.id()); }
    @Override public void wake(BodyHandle h)                      { Rapier3D.bodyWake       (world, h.id()); }

    @Override
    public JointHandle addFixedJoint(BodyHandle a, BodyHandle b, Transform localA, Transform localB, boolean contactsEnabled) {
        return new JointHandle(Rapier3D.jointAddFixed(world, a.id(), b.id(),
                localA.pos().x(), localA.pos().y(), localA.pos().z(),
                localA.rot().x(), localA.rot().y(), localA.rot().z(), localA.rot().w(),
                localB.pos().x(), localB.pos().y(), localB.pos().z(),
                localB.rot().x(), localB.rot().y(), localB.rot().z(), localB.rot().w(),
                contactsEnabled ? 1 : 0));
    }

    @Override
    public JointHandle addSphericalJoint(BodyHandle a, BodyHandle b, Vec3 localAnchorA, Vec3 localAnchorB, boolean contactsEnabled) {
        return new JointHandle(Rapier3D.jointAddSpherical(world, a.id(), b.id(),
                localAnchorA.x(), localAnchorA.y(), localAnchorA.z(),
                localAnchorB.x(), localAnchorB.y(), localAnchorB.z(),
                contactsEnabled ? 1 : 0));
    }

    @Override public void removeJoint(JointHandle h) { Rapier3D.jointRemove(world, h.id()); }

    @Override
    public Vec3 getLinearVelocity(BodyHandle h) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment out = a.allocate(ValueLayout.JAVA_FLOAT, 3);
            Rapier3D.bodyGetLinvel(world, h.id(), out);
            return new Vec3(out.getAtIndex(ValueLayout.JAVA_FLOAT, 0),
                            out.getAtIndex(ValueLayout.JAVA_FLOAT, 1),
                            out.getAtIndex(ValueLayout.JAVA_FLOAT, 2));
        }
    }

    // ---- queries -----------------------------------------------------------

    @Override
    public Optional<RaycastHit> raycast(Vec3 o, Vec3 d, float maxD, QueryFilter f) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment out = a.allocate(36);
            boolean hit = Rapier3D.queryRaycast(world,
                    o.x(), o.y(), o.z(), d.x(), d.y(), d.z(),
                    maxD, f.mask(), out);
            return hit ? Optional.of(decodeRaycastHit(out)) : Optional.empty();
        }
    }

    @Override
    public Optional<ShapeCastHit> shapeCast(ShapeDesc s, Transform from, Vec3 dir, float maxD, QueryFilter f) {
        long sh = shapeHandleFor(s);
        try (Arena a = Arena.ofConfined()) {
            MemorySegment out = a.allocate(36);
            boolean hit = Rapier3D.queryShapeCast(world, sh,
                    from.pos().x(), from.pos().y(), from.pos().z(),
                    from.rot().x(), from.rot().y(), from.rot().z(), from.rot().w(),
                    dir.x(), dir.y(), dir.z(),
                    maxD, f.mask(), out);
            if (!hit) return Optional.empty();
            float px = out.getAtIndex(ValueLayout.JAVA_FLOAT, 0);
            float py = out.getAtIndex(ValueLayout.JAVA_FLOAT, 1);
            float pz = out.getAtIndex(ValueLayout.JAVA_FLOAT, 2);
            float nx = out.getAtIndex(ValueLayout.JAVA_FLOAT, 3);
            float ny = out.getAtIndex(ValueLayout.JAVA_FLOAT, 4);
            float nz = out.getAtIndex(ValueLayout.JAVA_FLOAT, 5);
            float toi = out.getAtIndex(ValueLayout.JAVA_FLOAT, 6);
            // 28 isnt 8-aligned so the body id read has to be UNALIGNED on jdk 25
            long bid = out.get(ValueLayout.JAVA_LONG_UNALIGNED, 28);
            return Optional.of(new ShapeCastHit(new BodyHandle(bid),
                    new Vec3(px, py, pz), new Vec3(nx, ny, nz), toi));
        }
    }

    @Override
    public long[] overlap(ShapeDesc s, Transform x, QueryFilter f) {
        long sh = shapeHandleFor(s);
        int max = 256;
        try (Arena a = Arena.ofConfined()) {
            MemorySegment out = a.allocate(ValueLayout.JAVA_LONG, max);
            int n = Rapier3D.queryOverlap(world, sh,
                    x.pos().x(), x.pos().y(), x.pos().z(),
                    x.rot().x(), x.rot().y(), x.rot().z(), x.rot().w(),
                    f.mask(), out, max);
            long[] r = new long[n];
            for (int i = 0; i < n; i++) r[i] = out.getAtIndex(ValueLayout.JAVA_LONG, i);
            return r;
        }
    }

    // ---- events ------------------------------------------------------------

    @Override
    public List<ContactEvent> drainContactEvents() {
        int max = 1024;
        try (Arena a = Arena.ofConfined()) {
            MemorySegment out = a.allocate(44L * max);
            int n = Rapier3D.eventsDrainContacts(world, out, max);
            List<ContactEvent> events = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                // stride 44 bytes, longs read UNALIGNED on jdk 25
                long base = (long) i * 44L;
                long ai = out.get(ValueLayout.JAVA_LONG_UNALIGNED, base);
                long bi = out.get(ValueLayout.JAVA_LONG_UNALIGNED, base + 8);
                float px = out.get(ValueLayout.JAVA_FLOAT, base + 16);
                float py = out.get(ValueLayout.JAVA_FLOAT, base + 20);
                float pz = out.get(ValueLayout.JAVA_FLOAT, base + 24);
                float nx = out.get(ValueLayout.JAVA_FLOAT, base + 28);
                float ny = out.get(ValueLayout.JAVA_FLOAT, base + 32);
                float nz = out.get(ValueLayout.JAVA_FLOAT, base + 36);
                float impulse = out.get(ValueLayout.JAVA_FLOAT, base + 40);
                events.add(new ContactEvent(new BodyHandle(ai), new BodyHandle(bi),
                        new Vec3(px, py, pz), new Vec3(nx, ny, nz), impulse));
            }
            return events;
        }
    }

    @Override
    public List<AreaEvent> drainAreaEvents() {
        int max = 1024;
        try (Arena a = Arena.ofConfined()) {
            MemorySegment out = a.allocate(24L * max);
            int n = Rapier3D.eventsDrainAreas(world, out, max);
            List<AreaEvent> events = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                // stride 24 bytes, same UNALIGNED note as above
                long base = (long) i * 24L;
                long area  = out.get(ValueLayout.JAVA_LONG_UNALIGNED, base);
                long other = out.get(ValueLayout.JAVA_LONG_UNALIGNED, base + 8);
                int  enter = out.get(ValueLayout.JAVA_INT, base + 16);
                events.add(new AreaEvent(new BodyHandle(area), new BodyHandle(other), enter != 0));
            }
            return events;
        }
    }

    // ---- step --------------------------------------------------------------

    @Override public void setGravity(Vec3 gravity) { Rapier3D.worldSetGravity(world, gravity.x(), gravity.y(), gravity.z()); }
    @Override public void step(float dt) { Rapier3D.worldStep(world, dt); }

    @Override
    public void close() {
        for (long sh : shapeCache.values()) Rapier3D.shapeDrop(sh);
        shapeCache.clear();
        Rapier3D.worldDrop(world);
    }

    // ---- helpers -----------------------------------------------------------

    // intern primitives, upload trimeshes fresh each call
    private long shapeHandleFor(ShapeDesc desc) {
        return switch (desc) {
            case ShapeDesc.Box b -> shapeCache.computeIfAbsent(b, k ->
                    Rapier3D.shapeBox(b.halfExtents().x(), b.halfExtents().y(), b.halfExtents().z()));
            case ShapeDesc.Sphere s -> shapeCache.computeIfAbsent(s, k ->
                    Rapier3D.shapeSphere(s.radius()));
            case ShapeDesc.Capsule c -> shapeCache.computeIfAbsent(c, k ->
                    Rapier3D.shapeCapsule(c.radius(), c.halfHeight()));
            case ShapeDesc.Trimesh t -> uploadTrimesh(t);
        };
    }

    private static long uploadTrimesh(ShapeDesc.Trimesh t) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment vSeg = a.allocateFrom(ValueLayout.JAVA_FLOAT, t.vertices());
            MemorySegment iSeg = a.allocateFrom(ValueLayout.JAVA_INT,   t.indices());
            return Rapier3D.shapeTrimesh(vSeg, t.vertices().length, iSeg, t.indices().length);
        }
    }

    private static RaycastHit decodeRaycastHit(MemorySegment out) {
        float px = out.getAtIndex(ValueLayout.JAVA_FLOAT, 0);
        float py = out.getAtIndex(ValueLayout.JAVA_FLOAT, 1);
        float pz = out.getAtIndex(ValueLayout.JAVA_FLOAT, 2);
        float nx = out.getAtIndex(ValueLayout.JAVA_FLOAT, 3);
        float ny = out.getAtIndex(ValueLayout.JAVA_FLOAT, 4);
        float nz = out.getAtIndex(ValueLayout.JAVA_FLOAT, 5);
        float dist = out.getAtIndex(ValueLayout.JAVA_FLOAT, 6);
        long bid = out.get(ValueLayout.JAVA_LONG_UNALIGNED, 28);
        return new RaycastHit(new BodyHandle(bid), new Vec3(px, py, pz), new Vec3(nx, ny, nz), dist);
    }
}
