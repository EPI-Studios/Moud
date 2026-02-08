package com.moud.client.collision;

import net.minecraft.util.math.Box;

import java.util.List;

public class BVHNode {
    public Box bounds;
    public BVHNode left;
    public BVHNode right;
    public int[] triangleIndices;

    public static Box merge(Box a, Box b) {
        if (a == null) return b;
        if (b == null) return a;
        double minX = Math.min(a.minX, b.minX);
        double minY = Math.min(a.minY, b.minY);
        double minZ = Math.min(a.minZ, b.minZ);
        double maxX = Math.max(a.maxX, b.maxX);
        double maxY = Math.max(a.maxY, b.maxY);
        double maxZ = Math.max(a.maxZ, b.maxZ);
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean isLeaf() {
        return triangleIndices != null;
    }

    public void query(Box region, List<Integer> results) {
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
