package com.moud.client.fabric.physics.rapier;

import com.moud.core.physics.CollisionGeometry;
import com.moud.physics.api.BodyHandle;
import com.moud.physics.api.CollisionGroups;
import com.moud.physics.api.Quat;
import com.moud.physics.api.QueryFilter;
import com.moud.physics.api.ShapeCastHit;
import com.moud.physics.api.ShapeDesc;
import com.moud.physics.api.Transform;
import com.moud.physics.api.Vec3;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.net.protocol.CollisionGeometrySnapshot;
import com.moud.net.protocol.RigidBodyEntry;
import com.moud.net.protocol.RigidBodySnapshot;
import com.moud.net.protocol.SceneSnapshot;
import net.minecraft.client.world.ClientWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientRapierPhysics {
    private static final ClientRapierPhysics INSTANCE = new ClientRapierPhysics();
    private static final CollisionGroups STATIC_GROUPS = new CollisionGroups(1, 1);

    private final ClientStaticWorld staticWorld = new ClientStaticWorld();
    private final BodyInterpolator interpolator = new BodyInterpolator();
    private final ClientBodyVisuals visuals = new ClientBodyVisuals(interpolator);
    private final Map<Long, List<BodyHandle>> geometryBodies = new HashMap<>();
    private final List<BodyHandle> csgBodies = new ArrayList<>();
    private final Map<Long, BodyHandle> dynamicMirrors = new ConcurrentHashMap<>();
    private long cachedSceneVersion = Long.MIN_VALUE;
    private long cachedCsgFingerprint = Long.MIN_VALUE;

    public record SweepHit(double fraction, double x, double y, double z,
                           double nx, double ny, double nz) {}

    public record RayHit(double x, double y, double z,
                         double nx, double ny, double nz,
                         double distance,
                         double fraction) {}

    public record CharacterMove(double dx, double dy, double dz, boolean grounded) {}

    private ClientRapierPhysics() {
    }

    public static ClientRapierPhysics get() {
        return INSTANCE;
    }

    public static ClientBodyVisuals visuals() {
        return INSTANCE.visuals;
    }

    public static void onCollisionGeometry(CollisionGeometrySnapshot snap) {
        if (snap == null) return;
        INSTANCE.submit(new PhysicsMutation.ApplyCollisionGeometry(snap));
    }

    public static void clearCollisionGeometry() {
        INSTANCE.submit(new PhysicsMutation.ClearCollisionGeometry());
    }

    /** Cross-thread entrypoint: post a typed mutation. The render thread drains
     *  it on its next access to {@link #staticWorld}. */
    private void submit(PhysicsMutation mutation) {
        staticWorld.post(() -> apply(mutation));
    }

    /** Render-thread dispatcher. Exhaustive switch - adding a variant to
     *  {@link PhysicsMutation} forces a compile error here until handled. */
    private void apply(PhysicsMutation mutation) {
        switch (mutation) {
            case PhysicsMutation.ApplyCollisionGeometry m -> replaceCollisionGeometry(m.snapshot());
            case PhysicsMutation.ClearCollisionGeometry  __ -> clearGeometry();
            case PhysicsMutation.EnsureKinematicMirror m -> ensureDynamicMirror(m.nodeId(), m.pose());
        }
    }

    public boolean isAvailable() {
        return true;
    }

    public void syncSceneIfNeeded() {
        // staticWorld pumps internally before any of our addStatic/remove calls,
        // so no manual drain needed here.
        long version = ClientSceneBus.version();
        if (version == cachedSceneVersion) return;
        cachedSceneVersion = version;

        List<SceneSnapshot.NodeSnapshot> nodes = ClientSceneBus.copyNodes();
        long fp = computeCsgFingerprint(nodes);
        if (fp == cachedCsgFingerprint) return;
        cachedCsgFingerprint = fp;
        rebuildCsgBoxes(nodes);
    }

    public void onBlockChanged(int x, int y, int z) {
    }

    public void updateTerrainWindow(ClientWorld world, double x, double y, double z) {
        // handled by server
    }

    public void applyServerSnapshot(RigidBodySnapshot snapshot) {
        if (snapshot == null) return;
        long now = System.currentTimeMillis();
        for (RigidBodyEntry e : snapshot.entries()) {
            long id = e.nodeId();
            visuals.register(e.nodeId(), id);
            Transform pose = new Transform(
                    new Vec3(e.px(), e.py(), e.pz()),
                    new Quat(e.qx(), e.qy(), e.qz(), e.qw()));
            interpolator.onSnapshot(id, pose, new Vec3(e.vx(), e.vy(), e.vz()), now, false);
            if (!dynamicMirrors.containsKey(id)) {
                submit(new PhysicsMutation.EnsureKinematicMirror(id, pose));
            }
        }
    }

    /** Render-thread per-frame entry. {@link ClientStaticWorld#setTransform}
     *  pumps the queue internally, so any pending mutations land before the
     *  per-frame mirror→interpolator pose sync runs. */
    public void tickRenderFrame() {
        long now = System.currentTimeMillis();
        for (var entry : dynamicMirrors.entrySet()) {
            interpolator.sample(entry.getKey(), now)
                    .ifPresent(t -> staticWorld.setTransform(entry.getValue(), t));
        }
    }

    private void ensureDynamicMirror(long nodeId, Transform pose) {
        if (dynamicMirrors.containsKey(nodeId)) return;
        ShapeDesc shape = deriveDynamicShape(nodeId);
        if (shape == null) return; // try again next snapshot when scene info is in
        BodyHandle h = staticWorld.addKinematic(shape, pose, STATIC_GROUPS);
        dynamicMirrors.put(nodeId, h);
        staticWorld.refreshQueries();
    }

    public void applyPredictedImpulse(long nodeId, double jx, double jy, double jz) {
        // TODO : Client-side dynamic body prediction with thread-safe Rapier mirror
    }

    private ShapeDesc deriveDynamicShape(long nodeId) {
        for (SceneSnapshot.NodeSnapshot node : ClientSceneBus.copyNodes()) {
            if (node == null || node.nodeId() != nodeId) continue;
            String shape = NodePropertyUtils.stringProp(node, "shape");
            if ("sphere".equalsIgnoreCase(shape)) {
                float r = (float) Math.max(0.01, NodePropertyUtils.parseFloat(
                        NodePropertyUtils.stringProp(node, "radius"), 0.5f));
                return new ShapeDesc.Sphere(r);
            }
            if ("capsule".equalsIgnoreCase(shape)) {
                float r = (float) Math.max(0.01, NodePropertyUtils.parseFloat(
                        NodePropertyUtils.stringProp(node, "radius"), 0.3f));
                float h = (float) Math.max(2 * r, NodePropertyUtils.parseFloat(
                        NodePropertyUtils.stringProp(node, "height"), 1.8f));
                return new ShapeDesc.Capsule(r, (float) Math.max(1e-6, h * 0.5 - r));
            }
            float sx = (float) Math.max(0.01, NodePropertyUtils.parseFloat(
                    NodePropertyUtils.stringProp(node, "sx"), 1f) * 0.5f);
            float sy = (float) Math.max(0.01, NodePropertyUtils.parseFloat(
                    NodePropertyUtils.stringProp(node, "sy"), 1f) * 0.5f);
            float sz = (float) Math.max(0.01, NodePropertyUtils.parseFloat(
                    NodePropertyUtils.stringProp(node, "sz"), 1f) * 0.5f);
            return new ShapeDesc.Box(new Vec3(sx, sy, sz));
        }
        return null;
    }

    private void clearDynamicMirrors() {
        for (BodyHandle h : dynamicMirrors.values()) staticWorld.remove(h);
        dynamicMirrors.clear();
        staticWorld.refreshQueries();
    }

    public CharacterMove moveCharacter(double x, double y, double z,
                                       double radius, double fullHeight,
                                       double dx, double dy, double dz,
                                       double dt) {
        float r = (float) Math.max(1.0e-4, radius);
        float halfHeight = (float) Math.max(1.0e-6, fullHeight * 0.5 - radius);
        // capsule sweep wants the center, mixin gives us the feet - bump up
        double centerY = y + fullHeight * 0.5;
        var result = staticWorld.moveCharacter(
                new ShapeDesc.Capsule(r, halfHeight),
                new Transform(new Vec3((float) x, (float) centerY, (float) z), Quat.IDENTITY),
                new Vec3((float) dx, (float) dy, (float) dz),
                (float) dt);
        Vec3 c = result.corrected();
        return new CharacterMove(c.x(), c.y(), c.z(), result.grounded());
    }

    public Optional<SweepHit> sweepCapsule(double x, double y, double z,
                                           double radius, double fullHeight,
                                           double dx, double dy, double dz) {
        double lenSq = dx * dx + dy * dy + dz * dz;
        if (lenSq < 1.0e-12) return Optional.empty();
        double len = Math.sqrt(lenSq);
        float halfHeight = (float) Math.max(1.0e-6, fullHeight * 0.5 - radius);
        double centerY = y + fullHeight * 0.5;
        Optional<ShapeCastHit> hit = staticWorld.shapeCast(
                new ShapeDesc.Capsule((float) radius, halfHeight),
                new Transform(new Vec3((float) x, (float) centerY, (float) z), Quat.IDENTITY),
                new Vec3((float) (dx / len), (float) (dy / len), (float) (dz / len)),
                (float) len,
                QueryFilter.ALL);
        if (hit.isEmpty()) return Optional.empty();
        ShapeCastHit h = hit.get();
        double fraction = len <= 1.0e-8 ? 0.0 : Math.max(0.0, Math.min(1.0, h.toi() / len));
        return Optional.of(new SweepHit(fraction,
                h.point().x(), h.point().y(), h.point().z(),
                h.normal().x(), h.normal().y(), h.normal().z()));
    }

    public Optional<RayHit> raycastAny(double x, double y, double z,
                                       double dx, double dy, double dz,
                                       double maxDistance) {
        return staticWorld.raycast(
                new Vec3((float) x, (float) y, (float) z),
                new Vec3((float) dx, (float) dy, (float) dz),
                (float) maxDistance,
                QueryFilter.ALL
        ).map(h -> new RayHit(h.point().x(), h.point().y(), h.point().z(),
                h.normal().x(), h.normal().y(), h.normal().z(), h.distance(),
                maxDistance <= 1.0e-8 ? 0.0 : Math.max(0.0, Math.min(1.0, h.distance() / maxDistance))));
    }

    public Optional<RayHit> raycastFloor(double x, double startY, double z, double maxDistance) {
        return raycastAny(x, startY, z, 0.0, -1.0, 0.0, maxDistance);
    }

    public boolean overlapSphere(double x, double y, double z, double radius) {
        // ClientStaticWorld only exposes ray/shape-cast today; overlap is not needed for the cutover path.
        return false;
    }

    private void replaceCollisionGeometry(CollisionGeometrySnapshot snap) {
        if (snap == null || snap.nodeId() <= 0L) return;
        removeGeometry(snap.nodeId());
        if (snap.hulls() == null || snap.hulls().isEmpty()) return;
        ArrayList<BodyHandle> handles = new ArrayList<>();
        for (CollisionGeometry hull : snap.hulls()) {
            if (hull == null || hull.isEmpty() || hull.indices() == null || hull.indices().length < 3) continue;
            handles.add(staticWorld.addStatic(
                    new ShapeDesc.Trimesh(hull.vertices(), hull.indices()),
                    new Transform(new Vec3(0f, 0f, 0f), Quat.IDENTITY),
                    STATIC_GROUPS));
        }
        if (!handles.isEmpty()) {
            geometryBodies.put(snap.nodeId(), List.copyOf(handles));
            staticWorld.refreshQueries();
        }
    }

    private void removeGeometry(long nodeId) {
        List<BodyHandle> old = geometryBodies.remove(nodeId);
        if (old == null) return;
        for (BodyHandle h : old) {
            staticWorld.remove(h);
        }
        staticWorld.refreshQueries();
    }

    private void clearGeometry() {
        for (List<BodyHandle> handles : geometryBodies.values()) {
            for (BodyHandle h : handles) {
                staticWorld.remove(h);
            }
        }
        geometryBodies.clear();
        clearCsgBodies();
        visuals.clear();
        staticWorld.refreshQueries();
    }

    private void rebuildCsgBoxes(List<SceneSnapshot.NodeSnapshot> nodes) {
        clearCsgBodies();
        if (nodes == null || nodes.isEmpty()) return;

        Map<Long, float[]> locals = new HashMap<>(nodes.size() * 2);
        Map<Long, long[]> parents = new HashMap<>(nodes.size() * 2);
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null || node.nodeId() <= 0L) continue;
            locals.put(node.nodeId(), parseLocalTransform(node));
            parents.put(node.nodeId(), new long[]{node.parentId(), parseInherit(node) ? 1L : 0L});
        }

        Map<Long, float[]> cache = new HashMap<>();
        HashSet<Long> visiting = new HashSet<>();
        Quaternionf scratchQuat = new Quaternionf();
        Vector3f scratchVec = new Vector3f();

        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null || node.nodeId() <= 0L || !collisionEnabled(node) || !isCsgBox(node)) continue;
            computeWorld(node.nodeId(), locals, parents, cache, visiting, scratchQuat, scratchVec);
            float[] w = cache.get(node.nodeId());
            if (w == null) continue;
            csgBodies.add(staticWorld.addStatic(
                    new ShapeDesc.Box(new Vec3(
                            Math.max(1.0e-4f, w[7] * 0.5f),
                            Math.max(1.0e-4f, w[8] * 0.5f),
                            Math.max(1.0e-4f, w[9] * 0.5f))),
                    new Transform(new Vec3(w[0], w[1], w[2]), new Quat(w[3], w[4], w[5], w[6])),
                    STATIC_GROUPS));
        }
        staticWorld.refreshQueries();
    }

    private void clearCsgBodies() {
        for (BodyHandle h : csgBodies) {
            staticWorld.remove(h);
        }
        csgBodies.clear();
        staticWorld.refreshQueries();
    }

    private long computeCsgFingerprint(List<SceneSnapshot.NodeSnapshot> nodes) {
        long h = 1125899906842597L;
        if (nodes == null) return h;
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null || node.nodeId() <= 0L || !isCsgBox(node)) continue;
            h = 31 * h + node.nodeId();
            h = 31 * h + node.parentId();
            h = 31 * h + (node.type() == null ? 0 : node.type().hashCode());
            if (node.properties() == null) continue;
            for (SceneSnapshot.Property p : node.properties()) {
                if (p == null || p.key() == null || !CSG_PROPS.contains(p.key())) continue;
                h = 31 * h + p.key().hashCode();
                h = 31 * h + (p.value() == null ? 0 : p.value().hashCode());
            }
        }
        return h;
    }

    private static final Set<String> CSG_PROPS = Set.of(
            "x", "y", "z", "rx", "ry", "rz", "sx", "sy", "sz",
            "solid", "collision", "@inherit_transform"
    );

    private static boolean isCsgBox(SceneSnapshot.NodeSnapshot node) {
        return "CSGBox".equals(node.type()) || "CSGBlock".equals(node.type());
    }

    private static final float[] IDENTITY_LOCAL = {0, 0, 0, 0, 0, 0, 1, 1, 1, 1};

    private static void computeWorld(long id, Map<Long, float[]> locals, Map<Long, long[]> parents,
                                     Map<Long, float[]> cache, HashSet<Long> visiting,
                                     Quaternionf tQ, Vector3f tV) {
        if (cache.containsKey(id)) return;

        float[] loc = locals.getOrDefault(id, IDENTITY_LOCAL);
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
                tQ.set(pw[3], pw[4], pw[5], pw[6]).mul(qx, qy, qz, qw).normalize();
                qx = tQ.x; qy = tQ.y; qz = tQ.z; qw = tQ.w;
                sx *= pw[7]; sy *= pw[8]; sz *= pw[9];
            }
        }
        cache.put(id, new float[]{px, py, pz, qx, qy, qz, qw, sx, sy, sz});
    }

    private static float[] parseLocalTransform(SceneSnapshot.NodeSnapshot node) {
        float x = 0, y = 0, z = 0, rx = 0, ry = 0, rz = 0, sx = 1, sy = 1, sz = 1;
        if (node.properties() != null) {
            for (SceneSnapshot.Property p : node.properties()) {
                if (p == null || p.key() == null) continue;
                switch (p.key()) {
                    case "x" -> x = parseFloat(p.value(), 0f);
                    case "y" -> y = parseFloat(p.value(), 0f);
                    case "z" -> z = parseFloat(p.value(), 0f);
                    case "rx" -> rx = parseFloat(p.value(), 0f);
                    case "ry" -> ry = parseFloat(p.value(), 0f);
                    case "rz" -> rz = parseFloat(p.value(), 0f);
                    case "sx" -> sx = parseFloat(p.value(), 1f);
                    case "sy" -> sy = parseFloat(p.value(), 1f);
                    case "sz" -> sz = parseFloat(p.value(), 1f);
                }
            }
        }
        sx = Math.max(1e-6f, Math.abs(sx) < 1e-6f ? 0f : sx);
        sy = Math.max(1e-6f, Math.abs(sy) < 1e-6f ? 0f : sy);
        sz = Math.max(1e-6f, Math.abs(sz) < 1e-6f ? 0f : sz);

        if (isCsgBox(node)) {
            x += sx * 0.5f;
            y += sy * 0.5f;
            z += sz * 0.5f;
        }

        Quaternionf q = new Quaternionf().rotationZYX(
                (float) Math.toRadians(rz),
                (float) Math.toRadians(ry),
                (float) Math.toRadians(rx)
        ).normalize();
        return new float[]{x, y, z, q.x, q.y, q.z, q.w, sx, sy, sz};
    }

    private static boolean collisionEnabled(SceneSnapshot.NodeSnapshot node) {
        if (node.properties() == null) return true;
        for (SceneSnapshot.Property p : node.properties()) {
            if (p != null && p.value() != null && ("solid".equals(p.key()) || "collision".equals(p.key()))) {
                String s = p.value().trim().toLowerCase();
                return !"false".equals(s) && !"0".equals(s);
            }
        }
        return true;
    }

    private static boolean parseInherit(SceneSnapshot.NodeSnapshot node) {
        if (node.properties() == null) return true;
        for (SceneSnapshot.Property p : node.properties()) {
            if (p != null && p.value() != null && "@inherit_transform".equals(p.key())) {
                String s = p.value().trim().toLowerCase();
                return !"false".equals(s) && !"0".equals(s);
            }
        }
        return true;
    }

    private static float parseFloat(String value, float fallback) {
        try {
            if (value == null || value.isBlank()) return fallback;
            float f = Float.parseFloat(value.trim());
            return Float.isFinite(f) ? f : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
