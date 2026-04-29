package com.moud.physics.rapier;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public final class Rapier3D {

    static {
        RapierNatives.load();
    }

    private static final Linker        LINKER = Linker.nativeLinker();
    private static final SymbolLookup  LOOKUP = SymbolLookup.loaderLookup();

    private Rapier3D() {}

    private static MethodHandle bind(String name, FunctionDescriptor desc) {
        return LOOKUP.find(name)
                .map(seg -> LINKER.downcallHandle(seg, desc))
                .orElseThrow(() -> new IllegalStateException("Missing native symbol: " + name));
    }

    private static final MethodHandle MH_VERSION =
            bind("rapier_version", FunctionDescriptor.of(JAVA_INT));

    public static int version() {
        try {
            return (int) MH_VERSION.invokeExact();
        } catch (Throwable t) {
            throw new IllegalStateException("rapier_version failed", t);
        }
    }

    private static final MethodHandle MH_WORLD_NEW =
            bind("rapier_world_new", FunctionDescriptor.of(ADDRESS));

    private static final MethodHandle MH_WORLD_DROP =
            bind("rapier_world_drop", FunctionDescriptor.ofVoid(ADDRESS));

    private static final MethodHandle MH_WORLD_STEP =
            bind("rapier_world_step", FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));

    public static MemorySegment worldNew() {
        try { return (MemorySegment) MH_WORLD_NEW.invokeExact(); }
        catch (Throwable t) { throw new IllegalStateException("rapier_world_new failed", t); }
    }

    public static void worldDrop(MemorySegment world) {
        try { MH_WORLD_DROP.invokeExact(world); }
        catch (Throwable t) { throw new IllegalStateException("rapier_world_drop failed", t); }
    }

    public static void worldStep(MemorySegment world, float dt) {
        try { MH_WORLD_STEP.invokeExact(world, dt); }
        catch (Throwable t) { throw new IllegalStateException("rapier_world_step failed", t); }
    }

    private static final MethodHandle MH_BODY_ADD_STATIC = bind("rapier_body_add_static",
            FunctionDescriptor.of(JAVA_LONG,
                    ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_INT, JAVA_INT));

    private static final MethodHandle MH_BODY_ADD_DYNAMIC = bind("rapier_body_add_dynamic",
            FunctionDescriptor.of(JAVA_LONG,
                    ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_INT, JAVA_INT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_INT));

    private static final MethodHandle MH_BODY_ADD_KINEMATIC = bind("rapier_body_add_kinematic",
            FunctionDescriptor.of(JAVA_LONG,
                    ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_INT, JAVA_INT));

    private static final MethodHandle MH_BODY_ADD_AREA = bind("rapier_body_add_area",
            FunctionDescriptor.of(JAVA_LONG,
                    ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_INT, JAVA_INT));

    private static final MethodHandle MH_BODY_REMOVE = bind("rapier_body_remove",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));

    public static long bodyAddStatic(MemorySegment world, long shape,
                                     float x, float y, float z,
                                     float qx, float qy, float qz, float qw,
                                     int group, int mask) {
        try { return (long) MH_BODY_ADD_STATIC.invokeExact(world, shape, x, y, z, qx, qy, qz, qw, group, mask); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_add_static failed", t); }
    }

    public static long bodyAddDynamic(MemorySegment world, long shape,
                                      float x, float y, float z,
                                      float qx, float qy, float qz, float qw,
                                      int group, int mask,
                                      float mass, float gscale,
                                      float linDamp, float angDamp,
                                      int ccd) {
        try {
            return (long) MH_BODY_ADD_DYNAMIC.invokeExact(world, shape, x, y, z, qx, qy, qz, qw,
                    group, mask, mass, gscale, linDamp, angDamp, ccd);
        } catch (Throwable t) {
            throw new IllegalStateException("rapier_body_add_dynamic failed", t);
        }
    }

    public static long bodyAddKinematic(MemorySegment world, long shape,
                                        float x, float y, float z,
                                        float qx, float qy, float qz, float qw,
                                        int group, int mask) {
        try { return (long) MH_BODY_ADD_KINEMATIC.invokeExact(world, shape, x, y, z, qx, qy, qz, qw, group, mask); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_add_kinematic failed", t); }
    }

    public static long bodyAddArea(MemorySegment world, long shape,
                                   float x, float y, float z,
                                   float qx, float qy, float qz, float qw,
                                   int group, int mask) {
        try { return (long) MH_BODY_ADD_AREA.invokeExact(world, shape, x, y, z, qx, qy, qz, qw, group, mask); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_add_area failed", t); }
    }

    public static void bodyRemove(MemorySegment world, long body) {
        try { MH_BODY_REMOVE.invokeExact(world, body); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_remove failed", t); }
    }

    private static final MethodHandle MH_BODY_SET_XFORM = bind("rapier_body_set_xform",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));

    private static final MethodHandle MH_BODY_GET_XFORM = bind("rapier_body_get_xform",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS));

    private static final MethodHandle MH_BODY_APPLY_FORCE = bind("rapier_body_apply_force",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));

    private static final MethodHandle MH_BODY_APPLY_IMPULSE = bind("rapier_body_apply_impulse",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));

    private static final MethodHandle MH_BODY_SET_LINVEL = bind("rapier_body_set_linvel",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));

    private static final MethodHandle MH_BODY_GET_LINVEL = bind("rapier_body_get_linvel",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS));

    private static final MethodHandle MH_BODY_SLEEP = bind("rapier_body_sleep",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));

    private static final MethodHandle MH_BODY_WAKE = bind("rapier_body_wake",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));

    public static void bodySetXform(MemorySegment world, long body,
                                    float x, float y, float z,
                                    float qx, float qy, float qz, float qw) {
        try { MH_BODY_SET_XFORM.invokeExact(world, body, x, y, z, qx, qy, qz, qw); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_set_xform failed", t); }
    }

    public static void bodyGetXform(MemorySegment world, long body, MemorySegment out) {
        try { MH_BODY_GET_XFORM.invokeExact(world, body, out); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_get_xform failed", t); }
    }

    public static void bodyApplyForce(MemorySegment world, long body, float fx, float fy, float fz) {
        try { MH_BODY_APPLY_FORCE.invokeExact(world, body, fx, fy, fz); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_apply_force failed", t); }
    }

    public static void bodyApplyImpulse(MemorySegment world, long body, float jx, float jy, float jz) {
        try { MH_BODY_APPLY_IMPULSE.invokeExact(world, body, jx, jy, jz); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_apply_impulse failed", t); }
    }

    public static void bodySetLinvel(MemorySegment world, long body, float vx, float vy, float vz) {
        try { MH_BODY_SET_LINVEL.invokeExact(world, body, vx, vy, vz); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_set_linvel failed", t); }
    }

    public static void bodyGetLinvel(MemorySegment world, long body, MemorySegment out) {
        try { MH_BODY_GET_LINVEL.invokeExact(world, body, out); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_get_linvel failed", t); }
    }

    public static void bodySleep(MemorySegment world, long body) {
        try { MH_BODY_SLEEP.invokeExact(world, body); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_sleep failed", t); }
    }

    public static void bodyWake(MemorySegment world, long body) {
        try { MH_BODY_WAKE.invokeExact(world, body); }
        catch (Throwable t) { throw new IllegalStateException("rapier_body_wake failed", t); }
    }

    private static final MethodHandle MH_CHARCTL_NEW = bind("rapier_charctl_new",
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    private static final MethodHandle MH_CHARCTL_DROP = bind("rapier_charctl_drop",
            FunctionDescriptor.ofVoid(ADDRESS));

    private static final MethodHandle MH_CHARCTL_MOVE = bind("rapier_charctl_move",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, ADDRESS));

    public static MemorySegment charCtlNew(MemorySegment world) {
        try { return (MemorySegment) MH_CHARCTL_NEW.invokeExact(world); }
        catch (Throwable t) { throw new IllegalStateException("rapier_charctl_new failed", t); }
    }

    public static void charCtlDrop(MemorySegment ctl) {
        try { MH_CHARCTL_DROP.invokeExact(ctl); }
        catch (Throwable t) { throw new IllegalStateException("rapier_charctl_drop failed", t); }
    }

    public static void charCtlMove(MemorySegment ctl, MemorySegment world, long body,
                                   float dx, float dy, float dz, float dt,
                                   MemorySegment out) {
        try { MH_CHARCTL_MOVE.invokeExact(ctl, world, body, dx, dy, dz, dt, out); }
        catch (Throwable t) { throw new IllegalStateException("rapier_charctl_move failed", t); }
    }

    private static final MethodHandle MH_CHARCTL_MOVE_SHAPE = bind("rapier_charctl_move_shape",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, ADDRESS));

    public static void charCtlMoveShape(MemorySegment ctl, MemorySegment world, long shape,
                                        float px, float py, float pz,
                                        float qx, float qy, float qz, float qw,
                                        float dx, float dy, float dz,
                                        float dt, MemorySegment out) {
        try {
            MH_CHARCTL_MOVE_SHAPE.invokeExact(ctl, world, shape,
                    px, py, pz, qx, qy, qz, qw, dx, dy, dz, dt, out);
        } catch (Throwable t) {
            throw new IllegalStateException("rapier_charctl_move_shape failed", t);
        }
    }

    private static final MethodHandle MH_EVENTS_DRAIN_CONTACTS = bind("rapier_events_drain_contacts",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_EVENTS_DRAIN_AREAS = bind("rapier_events_drain_areas",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    public static int eventsDrainContacts(MemorySegment world, MemorySegment out, int maxEvents) {
        try { return (int) MH_EVENTS_DRAIN_CONTACTS.invokeExact(world, out, maxEvents); }
        catch (Throwable t) { throw new IllegalStateException("rapier_events_drain_contacts failed", t); }
    }

    public static int eventsDrainAreas(MemorySegment world, MemorySegment out, int maxEvents) {
        try { return (int) MH_EVENTS_DRAIN_AREAS.invokeExact(world, out, maxEvents); }
        catch (Throwable t) { throw new IllegalStateException("rapier_events_drain_areas failed", t); }
    }

    private static final MethodHandle MH_QUERY_RAYCAST = bind("rapier_query_raycast",
            FunctionDescriptor.of(JAVA_BOOLEAN,
                    ADDRESS,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_INT, ADDRESS));

    private static final MethodHandle MH_QUERY_SHAPE_CAST = bind("rapier_query_shape_cast",
            FunctionDescriptor.of(JAVA_BOOLEAN,
                    ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_INT, ADDRESS));

    private static final MethodHandle MH_QUERY_OVERLAP = bind("rapier_query_overlap",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_INT, ADDRESS, JAVA_INT));

    public static boolean queryRaycast(MemorySegment world,
                                       float ox, float oy, float oz,
                                       float dx, float dy, float dz,
                                       float maxDist, int mask,
                                       MemorySegment out) {
        try { return (boolean) MH_QUERY_RAYCAST.invokeExact(world, ox, oy, oz, dx, dy, dz, maxDist, mask, out); }
        catch (Throwable t) { throw new IllegalStateException("rapier_query_raycast failed", t); }
    }

    public static boolean queryShapeCast(MemorySegment world, long shape,
                                         float x, float y, float z,
                                         float qx, float qy, float qz, float qw,
                                         float dx, float dy, float dz,
                                         float maxDist, int mask,
                                         MemorySegment out) {
        try {
            return (boolean) MH_QUERY_SHAPE_CAST.invokeExact(world, shape, x, y, z, qx, qy, qz, qw,
                    dx, dy, dz, maxDist, mask, out);
        } catch (Throwable t) {
            throw new IllegalStateException("rapier_query_shape_cast failed", t);
        }
    }

    public static int queryOverlap(MemorySegment world, long shape,
                                   float x, float y, float z,
                                   float qx, float qy, float qz, float qw,
                                   int mask, MemorySegment out, int maxResults) {
        try {
            return (int) MH_QUERY_OVERLAP.invokeExact(world, shape, x, y, z, qx, qy, qz, qw,
                    mask, out, maxResults);
        } catch (Throwable t) {
            throw new IllegalStateException("rapier_query_overlap failed", t);
        }
    }

    private static final MethodHandle MH_SHAPE_BOX = bind("rapier_shape_box",
            FunctionDescriptor.of(JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));

    private static final MethodHandle MH_SHAPE_SPHERE = bind("rapier_shape_sphere",
            FunctionDescriptor.of(JAVA_LONG, JAVA_FLOAT));

    private static final MethodHandle MH_SHAPE_CAPSULE = bind("rapier_shape_capsule",
            FunctionDescriptor.of(JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT));

    private static final MethodHandle MH_SHAPE_TRIMESH = bind("rapier_shape_trimesh",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_SHAPE_DROP = bind("rapier_shape_drop",
            FunctionDescriptor.ofVoid(JAVA_LONG));

    public static long shapeBox(float hx, float hy, float hz) {
        try { return (long) MH_SHAPE_BOX.invokeExact(hx, hy, hz); }
        catch (Throwable t) { throw new IllegalStateException("rapier_shape_box failed", t); }
    }

    public static long shapeSphere(float r) {
        try { return (long) MH_SHAPE_SPHERE.invokeExact(r); }
        catch (Throwable t) { throw new IllegalStateException("rapier_shape_sphere failed", t); }
    }

    public static long shapeCapsule(float r, float halfHeight) {
        try { return (long) MH_SHAPE_CAPSULE.invokeExact(r, halfHeight); }
        catch (Throwable t) { throw new IllegalStateException("rapier_shape_capsule failed", t); }
    }

    public static long shapeTrimesh(MemorySegment vertsSeg, int vlen, MemorySegment idxSeg, int ilen) {
        try { return (long) MH_SHAPE_TRIMESH.invokeExact(vertsSeg, vlen, idxSeg, ilen); }
        catch (Throwable t) { throw new IllegalStateException("rapier_shape_trimesh failed", t); }
    }

    public static void shapeDrop(long shape) {
        try { MH_SHAPE_DROP.invokeExact(shape); }
        catch (Throwable t) { throw new IllegalStateException("rapier_shape_drop failed", t); }
    }
}
