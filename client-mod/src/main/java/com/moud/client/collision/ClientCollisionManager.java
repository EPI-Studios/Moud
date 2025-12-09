package com.moud.client.collision;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

public final class ClientCollisionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCollisionManager.class);
    private static final Map<Long, CollisionEntry> ENTRIES = new ConcurrentHashMap<>();

    private ClientCollisionManager() {
    }

    public static void registerModel(long modelId, MoudPackets.CollisionMode mode, byte[] compressedVerts, byte[] compressedIdx,
                                     Vector3 position, Quaternion rotation, Vector3 scale) {
        if (mode == null || mode == MoudPackets.CollisionMode.BOX) {
            LOGGER.debug("Skipping collision mesh registration for model {}: mode={}", modelId, mode);
            return;
        }
        float[] verts = decompressFloats(compressedVerts);
        int[] indices = decompressInts(compressedIdx);
        if (verts == null || indices == null) {
            LOGGER.warn("Failed to register collision mesh for model {}: missing vertices/indices", modelId);
            return;
        }
        CollisionEntry entry = new CollisionEntry(verts, indices, position, rotation, scale);
        ENTRIES.put(modelId, entry);
        LOGGER.info("Registered collision mesh for model {} (verts={}, indices={})", modelId, verts.length, indices.length);
    }

    public static void unregisterModel(long modelId) {
        ENTRIES.remove(modelId);
    }

    public static void clear() {
        ENTRIES.clear();
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
            entry.updateTransform(position, rotation, scale);
        }
    }

    public static List<CollisionMesh> getMeshesNear(Box region) {
        List<CollisionMesh> result = new ArrayList<>();
        for (CollisionEntry entry : ENTRIES.values()) {
            CollisionMesh mesh = entry.worldMesh;
            if (mesh != null && mesh.getBounds() != null && mesh.getBounds().intersects(region)) {
                result.add(mesh);
            }
        }
        return result;
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
            List<Float> values = new ArrayList<>();
            byte[] buf = gis.readAllBytes();
            if (buf.length % 4 != 0) {
                return null;
            }
            for (int i = 0; i < buf.length; i += 4) {
                int bits = ((buf[i] & 0xFF)) | ((buf[i + 1] & 0xFF) << 8) | ((buf[i + 2] & 0xFF) << 16) | ((buf[i + 3] & 0xFF) << 24);
                values.add(Float.intBitsToFloat(bits));
            }
            float[] arr = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                arr[i] = values.get(i);
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

        CollisionEntry(float[] localVertices, int[] indices, Vector3 pos, Quaternion rot, Vector3 scale) {
            this.localVertices = localVertices;
            this.indices = indices;
            rebuild(pos, rot, scale);
        }

        void updateTransform(Vector3 pos, Quaternion rot, Vector3 scale) {
            rebuild(pos, rot, scale);
        }

        private void rebuild(Vector3 pos, Quaternion rot, Vector3 scale) {
            float[] transformed = applyTransform(localVertices, pos, rot, scale);
            this.worldMesh = new CollisionMesh(transformed, indices);
        }

        private float[] applyTransform(float[] verts, Vector3 pos, Quaternion rot, Vector3 scale) {
            float[] out = new float[verts.length];
            Quaternion q = rot != null ? rot : Quaternion.identity();
            Vector3 s = scale != null ? scale : Vector3.one();
            Vector3 p = pos != null ? pos : Vector3.zero();
            for (int i = 0; i < verts.length; i += 3) {
                Vector3 v = new Vector3(verts[i], verts[i + 1], verts[i + 2]);
                v = new Vector3(v.x * s.x, v.y * s.y, v.z * s.z);
                v = rotate(v, q);
                v = new Vector3(v.x + p.x, v.y + p.y, v.z + p.z);
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
}
