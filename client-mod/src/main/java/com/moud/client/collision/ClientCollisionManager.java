package com.moud.client.collision;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.physics.ClientPhysicsWorld;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public final class ClientCollisionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCollisionManager.class);
    private static final Map<Long, CollisionEntry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> LOAD_SEQUENCE = new ConcurrentHashMap<>();
    private static final ExecutorService COLLISION_LOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Moud-CollisionLoader");
        t.setDaemon(true);
        return t;
    });
    private static final float SCALE_EPSILON = 1.0e-6f;

    private ClientCollisionManager() {
    }

    public static void registerModel(long modelId, MoudPackets.CollisionMode mode, byte[] compressedVerts, byte[] compressedIdx,
                                     Vector3 position, Quaternion rotation, Vector3 scale) {
        if (mode == null || mode == MoudPackets.CollisionMode.BOX) {
            LOGGER.debug("Skipping collision mesh registration for model {}: mode={}", modelId, mode);
            return;
        }
        if (compressedVerts == null || compressedVerts.length == 0 || compressedIdx == null || compressedIdx.length == 0) {
            LOGGER.warn("Failed to register collision mesh for model {}: mode={} but missing vertices/indices (verts={}, idx={})",
                    modelId, mode,
                    compressedVerts != null ? compressedVerts.length : 0,
                    compressedIdx != null ? compressedIdx.length : 0);
            return;
        }

        LOGGER.info("Registering collision mesh for model {}: mode={}, compressedVerts={}, compressedIdx={}",
                modelId, mode, compressedVerts.length, compressedIdx.length);

        int seq = LOAD_SEQUENCE.merge(modelId, 1, Integer::sum);
        Vector3 posCopy = position != null ? new Vector3(position) : Vector3.zero();
        Quaternion rotCopy = rotation != null ? new Quaternion(rotation) : Quaternion.identity();
        Vector3 scaleCopy = scale != null ? new Vector3(scale) : Vector3.one();

        CompletableFuture
                .supplyAsync(() -> prepareCollision(modelId, seq, compressedVerts, compressedIdx, posCopy, rotCopy, scaleCopy), COLLISION_LOAD_EXECUTOR)
                .whenComplete((prepared, err) -> {
                    if (err != null) {
                        LOGGER.error("Failed to build collision mesh for model {}: {}", modelId, err.getMessage(), err);
                        return;
                    }
                    MinecraftClient.getInstance().execute(() -> applyPreparedCollision(prepared));
                });
    }

    private static PreparedCollision prepareCollision(long modelId,
                                                      int seq,
                                                      byte[] compressedVerts,
                                                      byte[] compressedIdx,
                                                      Vector3 position,
                                                      Quaternion rotation,
                                                      Vector3 scale) {
        float[] verts = decompressFloats(compressedVerts);
        int[] indices = decompressInts(compressedIdx);
        if (verts == null || indices == null) {
            throw new IllegalStateException("Missing vertices/indices after decompression");
        }

        CollisionMesh cpuMesh = buildCpuMesh(verts, indices, rotation, scale, position);

        ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
        if (physics.isInitialized()) {
            Vector3f joltPos = new Vector3f((float) position.x, (float) position.y, (float) position.z);
            Quaternionf joltRot = new Quaternionf((float) rotation.x, (float) rotation.y, (float) rotation.z, (float) rotation.w);
            Vector3f joltScale = new Vector3f((float) scale.x, (float) scale.y, (float) scale.z);
            physics.addStaticMesh(modelId, verts, indices, joltPos, joltRot, joltScale);
        }

        return new PreparedCollision(modelId, seq, verts, indices, cpuMesh, position, rotation, scale);
    }

    private static void applyPreparedCollision(PreparedCollision prepared) {
        if (prepared == null) {
            return;
        }
        int current = LOAD_SEQUENCE.getOrDefault(prepared.modelId, 0);
        if (current != prepared.seq) {
            return;
        }
        CollisionEntry entry = new CollisionEntry(
                prepared.localVertices,
                prepared.indices,
                prepared.cpuMesh,
                prepared.position,
                prepared.rotation,
                prepared.scale
        );
        ENTRIES.put(prepared.modelId, entry);
        LOGGER.info("Registered collision mesh for model {} (verts={}, indices={})",
                prepared.modelId, prepared.localVertices.length, prepared.indices.length);
    }

    public static void syncAllToJoltPhysics() {
        ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
        if (!physics.isInitialized() || ENTRIES.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, CollisionEntry> entry : ENTRIES.entrySet()) {
            CollisionEntry collisionEntry = entry.getValue();
            if (collisionEntry == null) {
                continue;
            }
            Vector3 pos = collisionEntry.position != null ? collisionEntry.position : Vector3.zero();
            Quaternion rot = collisionEntry.rotation != null ? collisionEntry.rotation : Quaternion.identity();
            Vector3 sc = collisionEntry.scale != null ? collisionEntry.scale : Vector3.one();
            float[] verts = collisionEntry.localVertices;
            int[] idx = collisionEntry.indices;
            long id = entry.getKey();
            COLLISION_LOAD_EXECUTOR.execute(() -> physics.addStaticMesh(
                    id,
                    verts,
                    idx,
                    new Vector3f((float) pos.x, (float) pos.y, (float) pos.z),
                    new Quaternionf((float) rot.x, (float) rot.y, (float) rot.z, (float) rot.w),
                    new Vector3f((float) sc.x, (float) sc.y, (float) sc.z)
            ));
        }
    }

    public static void unregisterModel(long modelId) {
        ENTRIES.remove(modelId);
        LOAD_SEQUENCE.remove(modelId);

        ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
        if (physics.isInitialized()) {
            physics.removeStaticMesh(modelId);
        }
    }

    public static void clear() {
        ENTRIES.clear();
        LOAD_SEQUENCE.clear();

        ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
        if (physics.isInitialized()) {
            physics.clearAllMeshes();
        }
    }

    public static boolean hasMesh(long modelId) {
        return ENTRIES.containsKey(modelId);
    }

    public static boolean hasDebugData(long modelId) {
        CollisionEntry entry = ENTRIES.get(modelId);
        return entry != null && entry.worldMesh != null && entry.worldMesh.getTriangles() != null && !entry.worldMesh.getTriangles().isEmpty();
    }

    public static void updateTransform(long modelId, Vector3 position, Quaternion rotation, Vector3 scale) {
        CollisionEntry entry = ENTRIES.get(modelId);
        if (entry != null) {
            Vector3 oldScale = entry.scale;
            Quaternion oldRotation = entry.rotation;
            Vector3 newPos = position != null ? position : Vector3.zero();
            Quaternion newRot = rotation != null ? rotation : Quaternion.identity();
            Vector3 newScale = scale != null ? scale : Vector3.one();

            boolean scaleChanged = !approxEquals(oldScale, newScale, SCALE_EPSILON);
            boolean rotationChanged = oldRotation == null
                    || Math.abs(oldRotation.x - newRot.x) > 1.0e-6
                    || Math.abs(oldRotation.y - newRot.y) > 1.0e-6
                    || Math.abs(oldRotation.z - newRot.z) > 1.0e-6
                    || Math.abs(oldRotation.w - newRot.w) > 1.0e-6;

            if (!rotationChanged && !scaleChanged) {
                entry.updatePosition(newPos);
            } else {
                entry.updateTransform(newPos, newRot, newScale);
            }

            ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
            if (physics.isInitialized()) {
                Vector3f joltPos = new Vector3f(newPos.x, newPos.y, newPos.z);
                Quaternionf joltRot = new Quaternionf(newRot.x, newRot.y, newRot.z, newRot.w);
                Vector3f joltScale = new Vector3f(newScale.x, newScale.y, newScale.z);
                if (scaleChanged || !physics.hasStaticMesh(modelId)) {
                    float[] verts = entry.localVertices;
                    int[] idx = entry.indices;
                    COLLISION_LOAD_EXECUTOR.execute(() -> physics.addStaticMesh(modelId, verts, idx, joltPos, joltRot, joltScale));
                } else {
                    physics.updateMeshTransform(modelId, joltPos, joltRot);
                }
            }
        }
    }

    public static void updatePosition(long modelId, Vector3 position) {
        CollisionEntry entry = ENTRIES.get(modelId);
        if (entry != null) {
            entry.updatePosition(position);

            ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
            if (physics.isInitialized()) {
                if (!physics.hasStaticMesh(modelId)) {
                    Vector3 pos = entry.position != null ? entry.position : Vector3.zero();
                    Quaternion rot = entry.rotation != null ? entry.rotation : Quaternion.identity();
                    Vector3 sc = entry.scale != null ? entry.scale : Vector3.one();
                    float[] verts = entry.localVertices;
                    int[] idx = entry.indices;
                    COLLISION_LOAD_EXECUTOR.execute(() -> physics.addStaticMesh(
                            modelId,
                            verts,
                            idx,
                            new Vector3f(pos.x, pos.y, pos.z),
                            new Quaternionf(rot.x, rot.y, rot.z, rot.w),
                            new Vector3f(sc.x, sc.y, sc.z)
                    ));
                    return;
                }

                Vector3f joltPos = entry.position != null
                        ? new Vector3f(entry.position.x, entry.position.y, entry.position.z)
                        : new Vector3f(0, 0, 0);
                physics.updateMeshTransform(modelId, joltPos, null);
            }
        }
    }

    public static List<CollisionMesh> getMeshesNear(Box region) {
        List<MeshWithId> matches = new ArrayList<>();
        for (Map.Entry<Long, CollisionEntry> entry : ENTRIES.entrySet()) {
            CollisionMesh mesh = entry.getValue().worldMesh;
            if (mesh != null && mesh.getBounds() != null && mesh.getBounds().intersects(region)) {
                matches.add(new MeshWithId(entry.getKey(), mesh));
            }
        }

        if (matches.isEmpty()) {
            return List.of();
        }
        if (matches.size() == 1) {
            return List.of(matches.getFirst().mesh());
        }

        matches.sort(Comparator.comparingLong(MeshWithId::id));
        List<CollisionMesh> result = new ArrayList<>(matches.size());
        for (MeshWithId match : matches) {
            result.add(match.mesh());
        }
        return result;
    }

    public record RaycastHit(long modelId, Vec3d position, Vec3d normal, double distance) {
    }

    @Nullable
    public static RaycastHit raycastAny(Vec3d origin, Vec3d direction, double maxDistance) {
        if (origin == null || direction == null || maxDistance <= 0 || ENTRIES.isEmpty()) {
            return null;
        }
        double best = maxDistance;
        long bestId = -1L;
        Vec3d bestPos = null;
        Vec3d bestNormal = null;

        for (Map.Entry<Long, CollisionEntry> entry : ENTRIES.entrySet()) {
            CollisionMesh mesh = entry.getValue().worldMesh;
            if (mesh == null) {
                continue;
            }
            Box bounds = mesh.getBounds();
            if (bounds == null) {
                continue;
            }
            if (!rayIntersectsAabb(bounds, origin, direction, best)) {
                continue;
            }
            CollisionMesh.RayHit hit = mesh.raycast(origin, direction, best);
            if (hit != null && hit.distance() < best) {
                best = hit.distance();
                bestId = entry.getKey();
                bestPos = hit.position();
                bestNormal = hit.normal();
            }
        }

        if (bestId < 0 || bestPos == null || bestNormal == null) {
            return null;
        }
        return new RaycastHit(bestId, bestPos, bestNormal, best);
    }

    @Nullable
    public static RaycastHit raycastModel(long modelId, Vec3d origin, Vec3d direction, double maxDistance) {
        if (origin == null || direction == null || maxDistance <= 0) {
            return null;
        }
        CollisionEntry entry = ENTRIES.get(modelId);
        if (entry == null || entry.worldMesh == null) {
            return null;
        }
        CollisionMesh mesh = entry.worldMesh;
        Box bounds = mesh.getBounds();
        if (bounds == null || !rayIntersectsAabb(bounds, origin, direction, maxDistance)) {
            return null;
        }
        CollisionMesh.RayHit hit = mesh.raycast(origin, direction, maxDistance);
        if (hit == null) {
            return null;
        }
        return new RaycastHit(modelId, hit.position(), hit.normal(), hit.distance());
    }

    private static boolean rayIntersectsAabb(Box box, Vec3d origin, Vec3d direction, double maxDistance) {
        double lenSq = direction.lengthSquared();
        if (lenSq < 1.0e-12) {
            return false;
        }
        Vec3d dir = Math.abs(lenSq - 1.0) < 1.0e-6 ? direction : direction.multiply(1.0 / Math.sqrt(lenSq));

        double invX = 1.0 / (dir.x == 0 ? 1e-9 : dir.x);
        double invY = 1.0 / (dir.y == 0 ? 1e-9 : dir.y);
        double invZ = 1.0 / (dir.z == 0 ? 1e-9 : dir.z);

        double t1 = (box.minX - origin.x) * invX;
        double t2 = (box.maxX - origin.x) * invX;
        double tmin = Math.min(t1, t2);
        double tmax = Math.max(t1, t2);

        double ty1 = (box.minY - origin.y) * invY;
        double ty2 = (box.maxY - origin.y) * invY;
        tmin = Math.max(tmin, Math.min(ty1, ty2));
        tmax = Math.min(tmax, Math.max(ty1, ty2));

        double tz1 = (box.minZ - origin.z) * invZ;
        double tz2 = (box.maxZ - origin.z) * invZ;
        tmin = Math.max(tmin, Math.min(tz1, tz2));
        tmax = Math.min(tmax, Math.max(tz1, tz2));

        if (tmax < 0 || tmin > tmax) {
            return false;
        }
        double t = tmin >= 0 ? tmin : tmax;
        return t >= 0 && t <= maxDistance;
    }

    private record MeshWithId(long id, CollisionMesh mesh) {
    }

    public static List<Box> getDebugMeshBounds() {
        if (ENTRIES.isEmpty()) return Collections.emptyList();
        List<Box> boxes = new ArrayList<>();
        for (CollisionEntry entry : ENTRIES.values()) {
            CollisionMesh mesh = entry.worldMesh;
            if (mesh != null && mesh.getBounds() != null) {
                boxes.add(mesh.getBounds());
            }
        }
        return boxes;
    }

    public static List<Triangle> getDebugTriangles() {
        if (ENTRIES.isEmpty()) return Collections.emptyList();
        List<Triangle> tris = new ArrayList<>();
        for (CollisionEntry entry : ENTRIES.values()) {
            CollisionMesh mesh = entry.worldMesh;
            if (mesh != null && (mesh.getOffsetX() != 0 || mesh.getOffsetY() != 0 || mesh.getOffsetZ() != 0)) {
                continue;
            }
            if (mesh != null && mesh.getTriangles() != null) {
                tris.addAll(mesh.getTriangles());
            }
        }
        return tris;
    }

    public static List<CollisionMesh> getAllMeshes() {
        if (ENTRIES.isEmpty()) return Collections.emptyList();
        List<CollisionMesh> meshes = new ArrayList<>();
        for (CollisionEntry entry : ENTRIES.values()) {
            if (entry.worldMesh != null) {
                meshes.add(entry.worldMesh);
            }
        }
        return meshes;
    }

    private static float[] decompressFloats(byte[] data) {
        if (data == null || data.length == 0) return null;
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] buf = gis.readAllBytes();
            if (buf.length % 4 != 0) {
                return null;
            }
            float[] arr = new float[buf.length / 4];
            int out = 0;
            for (int i = 0; i < buf.length; i += 4) {
                int bits = ((buf[i] & 0xFF))
                        | ((buf[i + 1] & 0xFF) << 8)
                        | ((buf[i + 2] & 0xFF) << 16)
                        | ((buf[i + 3] & 0xFF) << 24);
                arr[out++] = Float.intBitsToFloat(bits);
            }
            return arr;
        } catch (IOException e) {
            return null;
        }
    }

    private static int[] decompressInts(byte[] data) {
        if (data == null || data.length == 0) return null;
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] buf = gis.readAllBytes();
            if (buf.length % 4 != 0) {
                return null;
            }
            int[] arr = new int[buf.length / 4];
            int idx = 0;
            for (int i = 0; i < buf.length; i += 4) {
                int v = ((buf[i] & 0xFF)) | ((buf[i + 1] & 0xFF) << 8) | ((buf[i + 2] & 0xFF) << 16) | ((buf[i + 3] & 0xFF) << 24);
                arr[idx++] = v;
            }
            return arr;
        } catch (IOException e) {
            return null;
        }
    }

    private static class CollisionEntry {
        private final float[] localVertices;
        private final int[] indices;
        private CollisionMesh worldMesh;
        private Vector3 position = Vector3.zero();
        private Quaternion rotation = Quaternion.identity();
        private Vector3 scale = Vector3.one();

        CollisionEntry(float[] localVertices, int[] indices, CollisionMesh mesh, Vector3 pos, Quaternion rot, Vector3 scale) {
            this.localVertices = localVertices;
            this.indices = indices;
            this.worldMesh = mesh;
            this.position = pos != null ? new Vector3(pos) : Vector3.zero();
            this.rotation = rot != null ? new Quaternion(rot) : Quaternion.identity();
            this.scale = scale != null ? new Vector3(scale) : Vector3.one();
            if (this.worldMesh != null) {
                this.worldMesh.setOffset(this.position.x, this.position.y, this.position.z);
            }
        }

        void updatePosition(Vector3 pos) {
            if (worldMesh == null) {
                return;
            }
            Vector3 p = pos != null ? new Vector3(pos) : Vector3.zero();
            this.position = p;
            worldMesh.setOffset(p.x, p.y, p.z);
        }

        void updateTransform(Vector3 pos, Quaternion rot, Vector3 scale) {
            rebuild(pos, rot, scale);
        }

        private void rebuild(Vector3 pos, Quaternion rot, Vector3 scale) {
            this.position = pos != null ? new Vector3(pos) : Vector3.zero();
            this.rotation = rot != null ? new Quaternion(rot) : Quaternion.identity();
            this.scale = scale != null ? new Vector3(scale) : Vector3.one();
            float[] transformed = applyTransform(localVertices, rot, scale);
            this.worldMesh = new CollisionMesh(transformed, indices);
            this.worldMesh.setOffset(this.position.x, this.position.y, this.position.z);
        }

        private float[] applyTransform(float[] verts, Quaternion rot, Vector3 scale) {
            float[] out = new float[verts.length];
            Quaternion q = rot != null ? rot : Quaternion.identity();
            Vector3 s = scale != null ? scale : Vector3.one();
            for (int i = 0; i < verts.length; i += 3) {
                Vector3 v = new Vector3(verts[i], verts[i + 1], verts[i + 2]);
                v = new Vector3(v.x * s.x, v.y * s.y, v.z * s.z);
                v = rotate(v, q);
                out[i] = v.x;
                out[i + 1] = v.y;
                out[i + 2] = v.z;
            }
            return out;
        }

        private Vector3 rotate(Vector3 v, Quaternion q) {
            // q * v * q^{-1}
            double qx = q.x;
            double qy = q.y;
            double qz = q.z;
            double qw = q.w;
            double ix = qw * v.x + qy * v.z - qz * v.y;
            double iy = qw * v.y + qz * v.x - qx * v.z;
            double iz = qw * v.z + qx * v.y - qy * v.x;
            double iw = -qx * v.x - qy * v.y - qz * v.z;
            double rx = ix * qw + iw * -qx + iy * -qz - iz * -qy;
            double ry = iy * qw + iw * -qy + iz * -qx - ix * -qz;
            double rz = iz * qw + iw * -qz + ix * -qy - iy * -qx;
            return new Vector3(rx, ry, rz);
        }
    }

    private record PreparedCollision(
            long modelId,
            int seq,
            float[] localVertices,
            int[] indices,
            CollisionMesh cpuMesh,
            Vector3 position,
            Quaternion rotation,
            Vector3 scale
    ) {
    }

    private static CollisionMesh buildCpuMesh(float[] localVertices,
                                              int[] indices,
                                              Quaternion rotation,
                                              Vector3 scale,
                                              Vector3 position) {
        Quaternion q = rotation != null ? rotation : Quaternion.identity();
        Vector3 s = scale != null ? scale : Vector3.one();

        float[] out = new float[localVertices.length];
        for (int i = 0; i < localVertices.length; i += 3) {
            Vector3 v = new Vector3(localVertices[i], localVertices[i + 1], localVertices[i + 2]);
            v = new Vector3(v.x * s.x, v.y * s.y, v.z * s.z);
            v = rotateStatic(v, q);
            out[i] = v.x;
            out[i + 1] = v.y;
            out[i + 2] = v.z;
        }

        CollisionMesh mesh = new CollisionMesh(out, indices);
        Vector3 p = position != null ? position : Vector3.zero();
        mesh.setOffset(p.x, p.y, p.z);
        return mesh;
    }

    private static Vector3 rotateStatic(Vector3 v, Quaternion q) {
        // q * v * q^{-1}
        double qx = q.x;
        double qy = q.y;
        double qz = q.z;
        double qw = q.w;
        double ix = qw * v.x + qy * v.z - qz * v.y;
        double iy = qw * v.y + qz * v.x - qx * v.z;
        double iz = qw * v.z + qx * v.y - qy * v.x;
        double iw = -qx * v.x - qy * v.y - qz * v.z;
        double rx = ix * qw + iw * -qx + iy * -qz - iz * -qy;
        double ry = iy * qw + iw * -qy + iz * -qx - ix * -qz;
        double rz = iz * qw + iw * -qz + ix * -qy - iy * -qx;
        return new Vector3(rx, ry, rz);
    }

    private static boolean approxEquals(Vector3 a, Vector3 b, float epsilon) {
        Vector3 va = a != null ? a : Vector3.one();
        Vector3 vb = b != null ? b : Vector3.one();
        return Math.abs(va.x - vb.x) <= epsilon
                && Math.abs(va.y - vb.y) <= epsilon
                && Math.abs(va.z - vb.z) <= epsilon;
    }
}
