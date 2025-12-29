package com.moud.api.collision;

import java.util.List;

public final class BVHNode {
    public AABB bounds;
    public BVHNode left;
    public BVHNode right;
    public int[] triangleIndices;

    public BVHNode() {
    }

    public static AABB merge(AABB a, AABB b) {
        if (a == null) return b;
        if (b == null) return a;
        return new AABB(
                Math.min(a.minX(), b.minX()),
                Math.min(a.minY(), b.minY()),
                Math.min(a.minZ(), b.minZ()),
                Math.max(a.maxX(), b.maxX()),
                Math.max(a.maxY(), b.maxY()),
                Math.max(a.maxZ(), b.maxZ())
        );
    }

    public boolean isLeaf() {
        return triangleIndices != null;
    }

    public void query(AABB region, List<Integer> results) {
        if (bounds == null || !bounds.intersects(region)) {
            return;
        }
        if (isLeaf()) {
            for (int idx : triangleIndices) {
                results.add(idx);
            }
            return;
        }
        if (left != null) {
            left.query(region, results);
        }
        if (right != null) {
            right.query(region, results);
        }
    }
}
