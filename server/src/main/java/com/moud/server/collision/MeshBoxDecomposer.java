package com.moud.server.collision;

import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

import java.util.ArrayList;
import java.util.List;

public class MeshBoxDecomposer {

    public static List<OBB> decompose(float[] vertices, int stride) {
        if (vertices == null || vertices.length < stride) {
            return new ArrayList<>();
        }

        Vector3 meshMin = new Vector3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3 meshMax = new Vector3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

        for (int i = 0; i < vertices.length; i += stride) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            meshMin = new Vector3(
                Math.min(meshMin.x, x),
                Math.min(meshMin.y, y),
                Math.min(meshMin.z, z)
            );
            meshMax = new Vector3(
                Math.max(meshMax.x, x),
                Math.max(meshMax.y, y),
                Math.max(meshMax.z, z)
            );
        }

        List<OBB> boxes = new ArrayList<>();

        Vector3 size = meshMax.subtract(meshMin);
        int divisions = calculateDivisions(size);

        float stepX = size.x / divisions;
        float stepY = size.y / divisions;
        float stepZ = size.z / divisions;

        for (int x = 0; x < divisions; x++) {
            for (int y = 0; y < divisions; y++) {
                for (int z = 0; z < divisions; z++) {
                    Vector3 boxMin = new Vector3(
                        meshMin.x + x * stepX,
                        meshMin.y + y * stepY,
                        meshMin.z + z * stepZ
                    );
                    Vector3 boxMax = new Vector3(
                        boxMin.x + stepX,
                        boxMin.y + stepY,
                        boxMin.z + stepZ
                    );

                    if (containsGeometry(vertices, stride, boxMin, boxMax)) {
                        Vector3 center = boxMin.add(boxMax).multiply(0.5f);
                        Vector3 halfExtents = boxMax.subtract(boxMin).multiply(0.5f);
                        boxes.add(new OBB(center, halfExtents, Quaternion.identity()));
                    }
                }
            }
        }

        return boxes.isEmpty() ? createFallbackBox(meshMin, meshMax) : boxes;
    }

    private static int calculateDivisions(Vector3 size) {
        float maxDim = Math.max(Math.max(size.x, size.y), size.z);
        if (maxDim < 1.0f) return 2;
        if (maxDim < 2.0f) return 3;
        if (maxDim < 4.0f) return 4;
        return 5;
    }

    private static boolean containsGeometry(float[] vertices, int stride, Vector3 boxMin, Vector3 boxMax) {
        for (int i = 0; i < vertices.length; i += stride) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            if (x >= boxMin.x && x <= boxMax.x &&
                y >= boxMin.y && y <= boxMax.y &&
                z >= boxMin.z && z <= boxMax.z) {
                return true;
            }
        }
        return false;
    }

    private static List<OBB> createFallbackBox(Vector3 meshMin, Vector3 meshMax) {
        List<OBB> boxes = new ArrayList<>();
        Vector3 center = meshMin.add(meshMax).multiply(0.5f);
        Vector3 halfExtents = meshMax.subtract(meshMin).multiply(0.5f);
        boxes.add(new OBB(center, halfExtents, Quaternion.identity()));
        return boxes;
    }
}
