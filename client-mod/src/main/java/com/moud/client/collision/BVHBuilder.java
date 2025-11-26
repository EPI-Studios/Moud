package com.moud.client.collision;

import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BVHBuilder {
    private static final int MAX_TRIANGLES_PER_LEAF = 4;
    private static final int MAX_DEPTH = 20;

    private BVHBuilder() {
    }

    public static BVHNode build(List<Triangle> triangles) {
        int[] all = new int[triangles.size()];
        for (int i = 0; i < triangles.size(); i++) {
            all[i] = i;
        }
        return buildRecursive(triangles, all, 0);
    }

    private static BVHNode buildRecursive(List<Triangle> tris, int[] triIndices, int depth) {
        BVHNode node = new BVHNode();
        node.bounds = computeBounds(tris, triIndices);

        if (triIndices.length <= MAX_TRIANGLES_PER_LEAF || depth >= MAX_DEPTH) {
            node.triangleIndices = triIndices;
            return node;
        }

        int axis = largestAxis(node.bounds);
        triIndices = Arrays.stream(triIndices)
                .boxed()
                .sorted((a, b) -> {
                    double ca = center(tris.get(a).bounds, axis);
                    double cb = center(tris.get(b).bounds, axis);
                    return Double.compare(ca, cb);
                })
                .mapToInt(Integer::intValue)
                .toArray();

        int mid = triIndices.length / 2;
        int[] leftIdx = Arrays.copyOfRange(triIndices, 0, mid);
        int[] rightIdx = Arrays.copyOfRange(triIndices, mid, triIndices.length);

        node.left = buildRecursive(tris, leftIdx, depth + 1);
        node.right = buildRecursive(tris, rightIdx, depth + 1);
        return node;
    }

    private static Box computeBounds(List<Triangle> tris, int[] ids) {
        if (ids == null || ids.length == 0) return null;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int id : ids) {
            Box b = tris.get(id).bounds;
            minX = Math.min(minX, b.minX);
            minY = Math.min(minY, b.minY);
            minZ = Math.min(minZ, b.minZ);
            maxX = Math.max(maxX, b.maxX);
            maxY = Math.max(maxY, b.maxY);
            maxZ = Math.max(maxZ, b.maxZ);
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static int largestAxis(Box b) {
        double x = b.maxX - b.minX;
        double y = b.maxY - b.minY;
        double z = b.maxZ - b.minZ;
        if (x >= y && x >= z) return 0;
        if (y >= z) return 1;
        return 2;
    }

    private static double center(Box b, int axis) {
        return switch (axis) {
            case 0 -> (b.minX + b.maxX) * 0.5;
            case 1 -> (b.minY + b.maxY) * 0.5;
            default -> (b.minZ + b.maxZ) * 0.5;
        };
    }
}
