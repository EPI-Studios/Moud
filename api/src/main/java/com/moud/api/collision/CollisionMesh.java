package com.moud.api.collision;

import com.moud.api.math.Vector3;

import java.util.ArrayList;
import java.util.List;

public final class CollisionMesh {
    private final float[] vertices;
    private final int[] indices;
    private final List<Triangle> triangles;
    private final BVHNode bvh;
    private final AABB bounds;
    private volatile double offsetX;
    private volatile double offsetY;
    private volatile double offsetZ;

    public CollisionMesh(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
        this.triangles = buildTriangles(vertices, indices);
        this.bvh = BVHBuilder.build(triangles);
        this.bounds = bvh != null ? bvh.bounds : null;
    }

    private static List<Triangle> buildTriangles(float[] verts, int[] idx) {
        List<Triangle> tris = new ArrayList<>(idx.length / 3);
        for (int i = 0; i + 2 < idx.length; i += 3) {
            int a = idx[i] * 3;
            int b = idx[i + 1] * 3;
            int c = idx[i + 2] * 3;
            if (a < 0 || b < 0 || c < 0 || a + 2 >= verts.length || b + 2 >= verts.length || c + 2 >= verts.length) {
                continue;
            }
            Vector3 v0 = new Vector3(verts[a], verts[a + 1], verts[a + 2]);
            Vector3 v1 = new Vector3(verts[b], verts[b + 1], verts[b + 2]);
            Vector3 v2 = new Vector3(verts[c], verts[c + 1], verts[c + 2]);
            tris.add(new Triangle(v0, v1, v2));
        }
        return tris;
    }

    public AABB getBounds() {
        AABB local = bounds;
        if (local == null) {
            return null;
        }
        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
            return local;
        }
        return local.moved(offsetX, offsetY, offsetZ);
    }

    public void setOffset(double x, double y, double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public double getOffsetZ() {
        return offsetZ;
    }


    public List<Triangle> queryTriangles(AABB region) {
        List<Triangle> result = new ArrayList<>();
        if (bvh == null || region == null) {
            return result;
        }
        List<Integer> ids = new ArrayList<>();
        bvh.query(region, ids);
        for (int id : ids) {
            if (id >= 0 && id < triangles.size()) {
                result.add(triangles.get(id));
            }
        }
        return result;
    }

    public float[] getVertices() {
        return vertices;
    }

    public int[] getIndices() {
        return indices;
    }

    public List<Triangle> getTriangles() {
        return triangles;
    }

    public int getTriangleCount() {
        return triangles.size();
    }
}
