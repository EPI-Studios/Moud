package com.moud.client.collision;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class CollisionMesh {
    private final float[] vertices;
    private final int[] indices;
    private final List<Triangle> triangles;
    private final BVHNode bvh;
    private final Box bounds;
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

    public record RayHit(Vec3d position, Vec3d normal, double distance) {
    }

    private List<Triangle> buildTriangles(float[] verts, int[] idx) {
        List<Triangle> tris = new ArrayList<>(idx.length / 3);
        for (int i = 0; i + 2 < idx.length; i += 3) {
            int a = idx[i] * 3;
            int b = idx[i + 1] * 3;
            int c = idx[i + 2] * 3;
            if (a < 0 || b < 0 || c < 0 || a + 2 >= verts.length || b + 2 >= verts.length || c + 2 >= verts.length) {
                continue;
            }
            Vec3d v0 = new Vec3d(verts[a], verts[a + 1], verts[a + 2]);
            Vec3d v1 = new Vec3d(verts[b], verts[b + 1], verts[b + 2]);
            Vec3d v2 = new Vec3d(verts[c], verts[c + 1], verts[c + 2]);
            tris.add(new Triangle(v0, v1, v2));
        }
        return tris;
    }

    @Nullable
    public RayHit raycast(Vec3d origin, Vec3d direction, double maxDistance) {
        if (origin == null || direction == null || maxDistance <= 0) {
            return null;
        }
        if (bvh == null || triangles == null || triangles.isEmpty()) {
            return null;
        }

        double lenSq = direction.lengthSquared();
        if (lenSq < 1.0e-12) {
            return null;
        }
        Vec3d dir = Math.abs(lenSq - 1.0) < 1.0e-6 ? direction : direction.multiply(1.0 / Math.sqrt(lenSq));

        Vec3d offsetOrigin = (offsetX == 0 && offsetY == 0 && offsetZ == 0)
                ? origin
                : origin.subtract(offsetX, offsetY, offsetZ);

        double bestT = maxDistance;
        Triangle bestTriangle = null;

        ArrayDeque<BVHNode> stack = new ArrayDeque<>();
        stack.push(bvh);
        while (!stack.isEmpty()) {
            BVHNode node = stack.pop();
            if (node == null || node.bounds == null) {
                continue;
            }
            if (!rayIntersectsAabb(node.bounds, offsetOrigin, dir, bestT)) {
                continue;
            }
            if (node.isLeaf()) {
                for (int triId : node.triangleIndices) {
                    if (triId < 0 || triId >= triangles.size()) {
                        continue;
                    }
                    Triangle tri = triangles.get(triId);
                    double t = intersectTriangle(offsetOrigin, dir, tri.v0, tri.v1, tri.v2);
                    if (t >= 0.0 && t < bestT) {
                        bestT = t;
                        bestTriangle = tri;
                    }
                }
                continue;
            }
            if (node.left != null) {
                stack.push(node.left);
            }
            if (node.right != null) {
                stack.push(node.right);
            }
        }

        if (bestTriangle == null) {
            return null;
        }

        Vec3d hitLocal = offsetOrigin.add(dir.multiply(bestT));
        Vec3d hitWorld = (offsetX == 0 && offsetY == 0 && offsetZ == 0)
                ? hitLocal
                : hitLocal.add(offsetX, offsetY, offsetZ);

        return new RayHit(hitWorld, bestTriangle.normal, bestT);
    }

    private static boolean rayIntersectsAabb(Box box, Vec3d origin, Vec3d direction, double maxDistance) {
        double invX = 1.0 / (Math.abs(direction.x) < 1e-12 ? 1e-12 : direction.x);
        double invY = 1.0 / (Math.abs(direction.y) < 1e-12 ? 1e-12 : direction.y);
        double invZ = 1.0 / (Math.abs(direction.z) < 1e-12 ? 1e-12 : direction.z);

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

        if (tmax < 0.0 || tmin > tmax) {
            return false;
        }
        double t = tmin >= 0.0 ? tmin : tmax;
        return t >= 0.0 && t <= maxDistance;
    }

    private static double intersectTriangle(Vec3d origin, Vec3d direction, Vec3d v0, Vec3d v1, Vec3d v2) {
        final double epsilon = 1.0e-9;

        double e1x = v1.x - v0.x;
        double e1y = v1.y - v0.y;
        double e1z = v1.z - v0.z;

        double e2x = v2.x - v0.x;
        double e2y = v2.y - v0.y;
        double e2z = v2.z - v0.z;

        double hx = direction.y * e2z - direction.z * e2y;
        double hy = direction.z * e2x - direction.x * e2z;
        double hz = direction.x * e2y - direction.y * e2x;
        double a = e1x * hx + e1y * hy + e1z * hz;
        if (a > -epsilon && a < epsilon) {
            return -1.0;
        }

        double f = 1.0 / a;
        double sx = origin.x - v0.x;
        double sy = origin.y - v0.y;
        double sz = origin.z - v0.z;
        double u = f * (sx * hx + sy * hy + sz * hz);
        if (u < 0.0 || u > 1.0) {
            return -1.0;
        }

        double qx = sy * e1z - sz * e1y;
        double qy = sz * e1x - sx * e1z;
        double qz = sx * e1y - sy * e1x;
        double v = f * (direction.x * qx + direction.y * qy + direction.z * qz);
        if (v < 0.0 || u + v > 1.0) {
            return -1.0;
        }

        double t = f * (e2x * qx + e2y * qy + e2z * qz);
        return t > epsilon ? t : -1.0;
    }

    public Box getBounds() {
        Box local = bounds;
        if (local == null) {
            return null;
        }
        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
            return local;
        }
        return local.offset(offsetX, offsetY, offsetZ);
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

    public List<Triangle> queryTriangles(Box region) {
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
}
