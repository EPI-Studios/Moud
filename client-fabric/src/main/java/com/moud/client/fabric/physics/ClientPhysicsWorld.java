package com.moud.client.fabric.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.core.physics.CollisionGeometry;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.*;
import net.minecraft.client.world.ClientWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPhysicsWorld {
    private static final ClientPhysicsWorld INSTANCE = new ClientPhysicsWorld();
    public static ClientPhysicsWorld get() { return INSTANCE; }

    private static final float MIN_CAPSULE_HALF_HEIGHT = 1.0e-6f;
    static final int LAYER_STATIC = 0;
    static final int LAYER_MOVING = 1;

    private static final Set<String> PHYSICS_PROPS = Set.of(
            "x", "y", "z", "rx", "ry", "rz", "sx", "sy", "sz",
            "solid", "collision", "collision_layer", "collision_mask", "@inherit_transform"
    );

    private PhysicsSystem system;
    private BodyInterface bodies;
    private ClientTerrainCollider terrain;

    private long cachedSceneVersion = Long.MIN_VALUE;
    private long cachedCsgFingerprint = Long.MIN_VALUE;
    private final List<Integer> csgBodyIds = new ArrayList<>();
    private final Map<Long, List<CollisionGeometry>> hullsByNode = new ConcurrentHashMap<>();

    public record SweepHit(double fraction, double nx, double ny, double nz) {}
    public record RayHit(float fraction, float nx, float ny, float nz) {}

    private ClientPhysicsWorld() {
        if (!ClientJoltBootstrap.isAvailable()) return;
        try {
            var bpLayers = new BroadPhaseLayerInterfaceTable(2, 2)
                    .mapObjectToBroadPhaseLayer(LAYER_STATIC, 0)
                    .mapObjectToBroadPhaseLayer(LAYER_MOVING, 1);

            var layerPairs = new ObjectLayerPairFilterTable(2);
            layerPairs.enableCollision(LAYER_STATIC, LAYER_MOVING);
            layerPairs.enableCollision(LAYER_MOVING, LAYER_MOVING);

            var objVsBp = new ObjectVsBroadPhaseLayerFilterTable(bpLayers, 2, layerPairs, 2);

            system = new PhysicsSystem();
            system.init(65536, 0, 65536, 10240, bpLayers, objVsBp, layerPairs);
            bodies = system.getBodyInterface();
            terrain = new ClientTerrainCollider(system, LAYER_STATIC);
        } catch (Throwable t) {
            ClientDebugLog.warn("physics", "Jolt init failed: " + t.getMessage());
            system = null; bodies = null; terrain = null;
        }
    }

    public boolean isAvailable() { return system != null; }

    private static boolean onRenderThread(String op) {
        if (RenderSystem.isOnRenderThread()) return true;
        ClientDebugLog.warn("physics-jolt", "Skipping " + op + " - not on render thread");
        return false;
    }

    public static void onCollisionGeometry(CollisionGeometrySnapshot snap) {
        if (snap == null || snap.nodeId() <= 0L || snap.hulls() == null || snap.hulls().isEmpty()) return;
        INSTANCE.hullsByNode.put(snap.nodeId(), List.copyOf(snap.hulls()));
        INSTANCE.cachedSceneVersion = INSTANCE.cachedCsgFingerprint = Long.MIN_VALUE;
    }

    public static void clearCollisionGeometry() {
        INSTANCE.hullsByNode.clear();
        INSTANCE.cachedSceneVersion = INSTANCE.cachedCsgFingerprint = Long.MIN_VALUE;
    }

    public void syncSceneIfNeeded() {
        if (system == null || !onRenderThread("syncSceneIfNeeded")) return;

        long v = ClientSceneBus.version();
        if (v == cachedSceneVersion) return;
        cachedSceneVersion = v;

        var nodes = ClientSceneBus.copyNodes();
        long fp = computeCsgFingerprint(nodes);
        if (fp == cachedCsgFingerprint) return;

        cachedCsgFingerprint = fp;
        rebuildCsgBodies(nodes);
    }

    private long computeCsgFingerprint(List<SceneSnapshot.NodeSnapshot> nodes) {
        long h = 1125899906842597L;
        if (nodes == null) return h;

        for (var node : nodes) {
            if (node == null || node.nodeId() <= 0) continue;
            boolean isCsg = "CSGBox".equals(node.type()) || "CSGBlock".equals(node.type());
            boolean hasHulls = hullsByNode.containsKey(node.nodeId());

            if (!isCsg && !hasHulls) continue;

            h = 31 * h + node.nodeId();
            h = 31 * h + (node.type() == null ? 0 : node.type().hashCode());
            h = 31 * h + node.parentId();

            if (node.properties() != null) {
                for (var p : node.properties()) {
                    if (p != null && p.key() != null && PHYSICS_PROPS.contains(p.key())) {
                        h = 31 * h + p.key().hashCode();
                        h = 31 * h + (p.value() == null ? 0 : p.value().hashCode());
                    }
                }
            }
            if (hasHulls) h = 31 * h + hullsByNode.get(node.nodeId()).hashCode();
        }
        return h;
    }

    public void updateTerrainWindow(ClientWorld world, double x, double y, double z) {
        if (system != null && terrain != null && onRenderThread("updateTerrainWindow")) {
            terrain.update(world, x, y, z);
        }
    }

    public void onBlockChanged(int x, int y, int z) {
        if (terrain != null) terrain.invalidateBlock(x, y, z);
    }

    public void clearTerrain() {
        if (terrain != null && onRenderThread("clearTerrain")) terrain.clear();
    }

    public Optional<SweepHit> sweepCapsuleHorizontal(double x, double y, double z, double r, double h, double dx, double dz) {
        if (system == null || !onRenderThread("sweepCapsuleHorizontal") || dx * dx + dz * dz <= 1e-10) return Optional.empty();

        float halfH = (float) Math.max(MIN_CAPSULE_HALF_HEIGHT, h * 0.5 - r);
        if (!JoltSafety.checkExtents("CapsuleShape", halfH + 1e-6f, (float) r) || !JoltSafety.checkVector("sweepCapsuleHorizontal", x, y, z, dx, dz)) return Optional.empty();

        JoltSafety.breadcrumb("sweepCapsuleHorizontal", "pos=" + x + "," + y + "," + z, "r=" + r, "h=" + h, "d=" + dx + "," + dz);

        try (var shape = new CapsuleShape(halfH, (float) r);
             var start = RMat44.sTranslation(new RVec3(x, y + h * 0.5, z));
             var cast = new RShapeCast(shape, new Vec3(1f, 1f, 1f), start, new Vec3((float) dx, 0f, (float) dz));
             var settings = new ShapeCastSettings();
             var collector = new AllHitCastShapeCollector()) {

            settings.setUseShrunkenShapeAndConvexRadius(false);
            settings.setReturnDeepestPoint(true);
            system.getNarrowPhaseQuery().castShape(cast, settings, RVec3.sZero(), collector);

            if (collector.countHits() == 0) return Optional.empty();
            collector.sort();

            for (int i = 0; i < collector.countHits(); i++) {
                try (var hit = collector.get(i)) {
                    var axis = hit.getPenetrationAxis();
                    double nx = axis != null ? axis.getX() : 0;
                    double ny = axis != null ? axis.getY() : 0;
                    double nz = axis != null ? axis.getZ() : 0;

                    if (nx * nx + nz * nz < 0.0025) continue;

                    double lenXz = Math.sqrt(nx * nx + nz * nz);
                    return Optional.of(new SweepHit(hit.getFraction(), nx / lenXz, ny, nz / lenXz));
                }
            }
            return Optional.empty();
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public Optional<Double> raycastFloor(double x, double startY, double z, double maxDist) {
        if (system == null || maxDist <= 0 || !onRenderThread("raycastFloor") || !JoltSafety.checkVector("raycastFloor", x, startY, z, maxDist)) return Optional.empty();

        JoltSafety.breadcrumb("raycastFloor", "pos=" + x + "," + startY + "," + z, "max=" + maxDist);

        try (var ray = new RRayCast(new RVec3(x, startY, z), new Vec3(0f, (float) -maxDist, 0f));
             var hit = new RayCastResult()) {
            return system.getNarrowPhaseQuery().castRay(ray, hit) ? Optional.of(startY - hit.getFraction() * maxDist) : Optional.empty();
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public boolean overlapSphere(double x, double y, double z, double r) {
        if (system == null || r <= 0 || !onRenderThread("overlapSphere") || !JoltSafety.checkVector("overlapSphere", x, y, z, r)) return false;

        JoltSafety.breadcrumb("overlapSphere", "pos=" + x + "," + y + "," + z, "r=" + r);

        try (var collector = new AllHitCollideShapeBodyCollector()) {
            system.getBroadPhaseQuery().collideSphere(new Vec3((float) x, (float) y, (float) z), (float) r, collector);
            return collector.countHits() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public Optional<RayHit> raycastAny(double x, double y, double z, double dx, double dy, double dz, float r) {
        if (system == null || !onRenderThread("raycastAny") || dx * dx + dy * dy + dz * dz < 1e-12 || !JoltSafety.checkVector("raycastAny", x, y, z, dx, dy, dz)) return Optional.empty();

        try (var shape = new SphereShape(Math.max(1e-3f, r));
             var start = RMat44.sTranslation(new RVec3(x, y, z));
             var cast = new RShapeCast(shape, new Vec3(1f, 1f, 1f), start, new Vec3((float) dx, (float) dy, (float) dz));
             var settings = new ShapeCastSettings();
             var collector = new AllHitCastShapeCollector()) {

            settings.setUseShrunkenShapeAndConvexRadius(false);
            settings.setReturnDeepestPoint(true);
            system.getNarrowPhaseQuery().castShape(cast, settings, RVec3.sZero(), collector);

            if (collector.countHits() == 0) return Optional.empty();
            collector.sort();

            try (var hit = collector.get(0)) {
                var axis = hit.getPenetrationAxis();
                float nx = axis != null ? axis.getX() : 0f;
                float ny = axis != null ? axis.getY() : 1f;
                float nz = axis != null ? axis.getZ() : 0f;
                float nlen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

                if (nlen > 1e-6f) { nx /= nlen; ny /= nlen; nz /= nlen; }
                else { nx = 0f; ny = 1f; nz = 0f; }

                return Optional.of(new RayHit(hit.getFraction(), nx, ny, nz));
            }
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private void rebuildCsgBodies(List<SceneSnapshot.NodeSnapshot> nodes) {
        destroyCsgBodies();
        if (nodes == null || nodes.isEmpty()) return;

        Map<Long, float[]> locals = new HashMap<>(nodes.size() * 2);
        Map<Long, long[]> parents = new HashMap<>(nodes.size() * 2);

        for (var node : nodes) {
            if (node == null || node.nodeId() <= 0) continue;
            locals.put(node.nodeId(), parseLocalTransform(node));
            parents.put(node.nodeId(), new long[]{node.parentId(), parseInherit(node) ? 1L : 0L});
        }

        Map<Long, float[]> cache = new HashMap<>();
        Set<Long> visiting = new HashSet<>();
        var tQ = new Quaternionf();
        var tV = new Vector3f();

        for (var node : nodes) {
            if (node == null || node.nodeId() <= 0 || !collisionEnabled(node)) continue;

            boolean isBox = "CSGBox".equals(node.type()) || "CSGBlock".equals(node.type());
            var hulls = hullsByNode.get(node.nodeId());
            boolean hasHulls = hulls != null && !hulls.isEmpty();

            if (!isBox && !hasHulls) continue;

            computeWorld(node.nodeId(), locals, parents, cache, visiting, tQ, tV);
            float[] w = cache.get(node.nodeId());
            if (w == null) continue;

            if (isBox) addCsgBox(w);
            if (hasHulls) addConvexHulls(w, hulls);
        }
        system.optimizeBroadPhase();
    }

    private void addCsgBox(float[] w) {
        float hx = Math.max(1e-4f, w[7] * 0.5f), hy = Math.max(1e-4f, w[8] * 0.5f), hz = Math.max(1e-4f, w[9] * 0.5f);
        var settings = new BoxShapeSettings(hx, hy, hz);

        try (var bcs = new BodyCreationSettings(settings, new RVec3(w[0], w[1], w[2]), new Quat(w[3], w[4], w[5], w[6]), EMotionType.Static, LAYER_STATIC)) {
            int id = bodies.createAndAddBody(bcs, EActivation.DontActivate);
            if (id != Jolt.cInvalidBodyId) csgBodyIds.add(id);
            else ClientDebugLog.warn("physics-jolt", "CSG box body create failed");
        } catch (Throwable ignored) {
        } finally {
            settings.close();
        }
    }

    private void addConvexHulls(float[] w, List<CollisionGeometry> hulls) {
        float qx = w[3], qy = w[4], qz = w[5], qw = w[6], sx = w[7], sy = w[8], sz = w[9];

        double m00 = 1 - 2 * (qy * qy + qz * qz), m10 = 2 * (qx * qy + qz * qw), m20 = 2 * (qx * qz - qy * qw);
        double m01 = 2 * (qx * qy - qz * qw), m11 = 1 - 2 * (qx * qx + qz * qz), m21 = 2 * (qy * qz + qx * qw);
        double m02 = 2 * (qx * qz + qy * qw), m12 = 2 * (qy * qz - qx * qw), m22 = 1 - 2 * (qx * qx + qy * qy);

        for (var hull : hulls) {
            if (hull == null || hull.vertices() == null || hull.vertices().length < 9) continue;
            float[] v = hull.vertices();
            var pts = new Float3[v.length / 3];

            for (int i = 0; i < v.length - 2; i += 3) {
                double lx = v[i] * sx, ly = v[i + 1] * sy, lz = v[i + 2] * sz;
                pts[i / 3] = new Float3(
                        (float) (m00 * lx + m01 * ly + m02 * lz),
                        (float) (m10 * lx + m11 * ly + m12 * lz),
                        (float) (m20 * lx + m21 * ly + m22 * lz)
                );
            }

            try (var shs = new ConvexHullShapeSettings(Arrays.asList(pts));
                 var bcs = new BodyCreationSettings(shs, new RVec3(w[0], w[1], w[2]), Quat.sIdentity(), EMotionType.Static, LAYER_STATIC)) {
                int id = bodies.createAndAddBody(bcs, EActivation.DontActivate);
                if (id != Jolt.cInvalidBodyId) csgBodyIds.add(id);
                else ClientDebugLog.warn("physics-jolt", "CSG hull body create failed");
            } catch (Throwable ignored) {}
        }
    }

    private void destroyCsgBodies() {
        if (csgBodyIds.isEmpty()) return;
        JoltSafety.breadcrumb("destroyCsgBodies", "count=" + csgBodyIds.size());

        for (int id : csgBodyIds) {
            if (id == Jolt.cInvalidBodyId) continue;
            try { bodies.removeBody(id); bodies.destroyBody(id); } catch (Throwable ignored) {}
        }
        csgBodyIds.clear();
    }

    private static void computeWorld(long id, Map<Long, float[]> locals, Map<Long, long[]> parents, Map<Long, float[]> cache, Set<Long> visiting, Quaternionf tQ, Vector3f tV) {
        if (cache.containsKey(id)) return;

        float[] loc = locals.getOrDefault(id, new float[]{0, 0, 0, 0, 0, 0, 1, 1, 1, 1});
        long[] pInfo = parents.get(id);
        long pId = pInfo != null ? pInfo[0] : 0L;
        boolean inherit = pInfo != null && pInfo[1] != 0L;

        float px = loc[0], py = loc[1], pz = loc[2], qx = loc[3], qy = loc[4], qz = loc[5], qw = loc[6], sx = loc[7], sy = loc[8], sz = loc[9];

        if (inherit && pId > 0L) {
            if (!visiting.add(id)) {
                cache.put(id, loc);
                return;
            }
            computeWorld(pId, locals, parents, cache, visiting, tQ, tV);
            visiting.remove(id);

            float[] pw = cache.get(pId);
            if (pw != null) {
                tV.set(px * pw[7], py * pw[8], pz * pw[9]);
                tQ.set(pw[3], pw[4], pw[5], pw[6]).transform(tV);
                px = pw[0] + tV.x; py = pw[1] + tV.y; pz = pw[2] + tV.z;
                tQ.mul(qx, qy, qz, qw).normalize();
                qx = tQ.x; qy = tQ.y; qz = tQ.z; qw = tQ.w;
                sx *= pw[7]; sy *= pw[8]; sz *= pw[9];
            }
        }
        cache.put(id, new float[]{px, py, pz, qx, qy, qz, qw, sx, sy, sz});
    }

    private static float[] parseLocalTransform(SceneSnapshot.NodeSnapshot node) {
        float x = 0, y = 0, z = 0, rx = 0, ry = 0, rz = 0, sx = 1, sy = 1, sz = 1;
        if (node.properties() != null) {
            for (var p : node.properties()) {
                if (p == null || p.key() == null) continue;
                switch (p.key()) {
                    case "x" -> x = ParseUtils.parseFloat(p.value(), 0);
                    case "y" -> y = ParseUtils.parseFloat(p.value(), 0);
                    case "z" -> z = ParseUtils.parseFloat(p.value(), 0);
                    case "rx" -> rx = ParseUtils.parseFloat(p.value(), 0);
                    case "ry" -> ry = ParseUtils.parseFloat(p.value(), 0);
                    case "rz" -> rz = ParseUtils.parseFloat(p.value(), 0);
                    case "sx" -> sx = ParseUtils.parseFloat(p.value(), 1);
                    case "sy" -> sy = ParseUtils.parseFloat(p.value(), 1);
                    case "sz" -> sz = ParseUtils.parseFloat(p.value(), 1);
                }
            }
        }

        sx = Math.max(1e-6f, Math.abs(sx) < 1e-6f ? 0f : sx);
        sy = Math.max(1e-6f, Math.abs(sy) < 1e-6f ? 0f : sy);
        sz = Math.max(1e-6f, Math.abs(sz) < 1e-6f ? 0f : sz);

        if ("CSGBox".equals(node.type()) || "CSGBlock".equals(node.type())) {
            x += sx * 0.5f; y += sy * 0.5f; z += sz * 0.5f;
        }

        var q = new Quaternionf()
                .rotationZ((float) Math.toRadians(rz))
                .mul(new Quaternionf().rotationY((float) Math.toRadians(ry)))
                .mul(new Quaternionf().rotationX((float) Math.toRadians(rx)))
                .normalize();

        return new float[]{x, y, z, q.x, q.y, q.z, q.w, sx, sy, sz};
    }

    private static boolean collisionEnabled(SceneSnapshot.NodeSnapshot node) {
        if (node.properties() == null) return true;
        for (var p : node.properties()) {
            if (p != null && p.value() != null && ("solid".equals(p.key()) || "collision".equals(p.key()))) {
                return !Set.of("false", "0").contains(p.value().trim().toLowerCase());
            }
        }
        return true;
    }

    private static boolean parseInherit(SceneSnapshot.NodeSnapshot node) {
        if (node.properties() == null) return true;
        for (var p : node.properties()) {
            if (p != null && p.value() != null && "@inherit_transform".equals(p.key())) {
                return !Set.of("false", "0").contains(p.value().trim().toLowerCase());
            }
        }
        return true;
    }
}