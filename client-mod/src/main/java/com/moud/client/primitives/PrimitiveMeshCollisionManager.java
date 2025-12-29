package com.moud.client.primitives;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.collision.CollisionMesh;
import com.moud.network.MoudPackets;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PrimitiveMeshCollisionManager {
    private static final PrimitiveMeshCollisionManager INSTANCE = new PrimitiveMeshCollisionManager();

    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

    private PrimitiveMeshCollisionManager() {
    }

    public static PrimitiveMeshCollisionManager getInstance() {
        return INSTANCE;
    }

    public boolean hasMesh(long primitiveId) {
        return entries.containsKey(primitiveId);
    }

    public void registerPrimitive(ClientPrimitive primitive) {
        updatePrimitive(primitive);
    }

    public void updatePrimitive(ClientPrimitive primitive) {
        if (primitive == null || !primitive.hasCollision() || primitive.isLineType()) {
            return;
        }

        Vector3 position = primitive.getInterpolatedPosition(0.0f);
        Quaternion rotation = primitive.getInterpolatedRotation(0.0f);
        Vector3 scale = primitive.getInterpolatedScale(0.0f);

        Vector3 resolvedPosition = position != null ? position : Vector3.zero();
        Quaternion resolvedRotation = rotation != null ? rotation : Quaternion.identity();
        Vector3 resolvedScale = scale != null ? scale : Vector3.one();

        long id = primitive.getId();
        entries.compute(id, (ignored, existing) -> {
            if (existing == null) {
                Entry created = buildEntry(primitive, resolvedRotation, resolvedScale);
                if (created != null) {
                    created.mesh.setOffset(resolvedPosition.x, resolvedPosition.y, resolvedPosition.z);
                }
                return created;
            }

            if (!rotationEquals(existing.rotation, resolvedRotation) || !scaleEquals(existing.scale, resolvedScale)
                    || existing.type != primitive.getType() || existing.meshSourceHash != hashMeshSource(primitive)) {
                Entry rebuilt = buildEntry(primitive, resolvedRotation, resolvedScale);
                if (rebuilt != null) {
                    rebuilt.mesh.setOffset(resolvedPosition.x, resolvedPosition.y, resolvedPosition.z);
                }
                return rebuilt;
            }

            existing.mesh.setOffset(resolvedPosition.x, resolvedPosition.y, resolvedPosition.z);
            return existing;
        });
    }

    public void unregisterPrimitive(long primitiveId) {
        entries.remove(primitiveId);
    }

    public void clear() {
        entries.clear();
    }

    public List<CollisionMesh> getMeshesNear(Box region) {
        if (region == null || entries.isEmpty()) {
            return List.of();
        }

        List<MeshWithId> matches = null;
        for (Map.Entry<Long, Entry> entry : entries.entrySet()) {
            CollisionMesh mesh = entry.getValue().mesh;
            if (mesh == null) {
                continue;
            }
            Box bounds = mesh.getBounds();
            if (bounds == null || !bounds.intersects(region)) {
                continue;
            }
            if (matches == null) {
                matches = new ArrayList<>();
            }
            matches.add(new MeshWithId(entry.getKey(), mesh));
        }

        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        matches.sort(Comparator.comparingLong(MeshWithId::id));
        List<CollisionMesh> out = new ArrayList<>(matches.size());
        for (MeshWithId match : matches) {
            out.add(match.mesh());
        }
        return out;
    }

    private record MeshWithId(long id, CollisionMesh mesh) {
    }

    private static Entry buildEntry(ClientPrimitive primitive, Quaternion rotation, Vector3 scale) {
        if (primitive == null) {
            return null;
        }
        MoudPackets.PrimitiveType type = primitive.getType();
        if (type == null || primitive.isLineType()) {
            return null;
        }

        List<PrimitiveGeometry.Triangle> triangles = PrimitiveGeometry.generateMesh(
                type,
                Vector3.zero(),
                rotation,
                scale,
                primitive.getVertices(),
                primitive.getIndices()
        );
        if (triangles == null || triangles.isEmpty()) {
            return null;
        }

        int triCount = triangles.size();
        float[] vertices = new float[triCount * 9];
        int[] indices = new int[triCount * 3];

        int vCursor = 0;
        int iCursor = 0;
        int vertexIndex = 0;
        for (PrimitiveGeometry.Triangle tri : triangles) {
            if (tri == null) {
                continue;
            }
            Vector3 v0 = tri.v0();
            Vector3 v1 = tri.v1();
            Vector3 v2 = tri.v2();
            if (v0 == null || v1 == null || v2 == null) {
                continue;
            }

            vertices[vCursor++] = (float) v0.x;
            vertices[vCursor++] = (float) v0.y;
            vertices[vCursor++] = (float) v0.z;
            vertices[vCursor++] = (float) v1.x;
            vertices[vCursor++] = (float) v1.y;
            vertices[vCursor++] = (float) v1.z;
            vertices[vCursor++] = (float) v2.x;
            vertices[vCursor++] = (float) v2.y;
            vertices[vCursor++] = (float) v2.z;

            indices[iCursor++] = vertexIndex++;
            indices[iCursor++] = vertexIndex++;
            indices[iCursor++] = vertexIndex++;
        }

        if (vCursor != vertices.length) {
            // Some triangles were skipped due to nulls; trim arrays.
            float[] trimmedVerts = new float[vCursor];
            System.arraycopy(vertices, 0, trimmedVerts, 0, vCursor);
            vertices = trimmedVerts;
        }
        if (iCursor != indices.length) {
            int[] trimmedIdx = new int[iCursor];
            System.arraycopy(indices, 0, trimmedIdx, 0, iCursor);
            indices = trimmedIdx;
        }
        if (indices.length < 3) {
            return null;
        }

        CollisionMesh mesh = new CollisionMesh(vertices, indices);
        return new Entry(type, new Quaternion(rotation), new Vector3(scale), hashMeshSource(primitive), mesh);
    }

    private static boolean rotationEquals(Quaternion a, Quaternion b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        // Account for q and -q representing the same rotation.
        double dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        dot = Math.abs(dot);
        return dot > 0.999999;
    }

    private static boolean scaleEquals(Vector3 a, Vector3 b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a.x - b.x) < 1.0e-6 && Math.abs(a.y - b.y) < 1.0e-6 && Math.abs(a.z - b.z) < 1.0e-6;
    }

    private static int hashMeshSource(ClientPrimitive primitive) {
        if (primitive == null) {
            return 0;
        }
        if (primitive.getType() != MoudPackets.PrimitiveType.MESH) {
            return 0;
        }
        List<Vector3> verts = primitive.getVertices();
        List<Integer> inds = primitive.getIndices();
        int h = 1;
        if (verts != null) {
            h = h * 31 + verts.size();
            h = h * 31 + sampleVertexHash(verts, 0);
            h = h * 31 + sampleVertexHash(verts, verts.size() / 2);
            h = h * 31 + sampleVertexHash(verts, verts.size() - 1);
        }
        if (inds != null) {
            h = h * 31 + inds.size();
            h = h * 31 + sampleIndexHash(inds, 0);
            h = h * 31 + sampleIndexHash(inds, inds.size() / 2);
            h = h * 31 + sampleIndexHash(inds, inds.size() - 1);
        }
        return h;
    }

    private static int sampleVertexHash(List<Vector3> vertices, int idx) {
        if (vertices == null || vertices.isEmpty()) {
            return 0;
        }
        if (idx < 0) {
            idx = 0;
        } else if (idx >= vertices.size()) {
            idx = vertices.size() - 1;
        }
        Vector3 v = vertices.get(idx);
        if (v == null) {
            return 0;
        }
        int h = 1;
        h = h * 31 + Float.floatToIntBits((float) v.x);
        h = h * 31 + Float.floatToIntBits((float) v.y);
        h = h * 31 + Float.floatToIntBits((float) v.z);
        return h;
    }

    private static int sampleIndexHash(List<Integer> indices, int idx) {
        if (indices == null || indices.isEmpty()) {
            return 0;
        }
        if (idx < 0) {
            idx = 0;
        } else if (idx >= indices.size()) {
            idx = indices.size() - 1;
        }
        Integer v = indices.get(idx);
        return v != null ? v : 0;
    }

    private record Entry(MoudPackets.PrimitiveType type, Quaternion rotation, Vector3 scale, int meshSourceHash, CollisionMesh mesh) {
    }
}
