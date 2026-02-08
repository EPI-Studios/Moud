package com.moud.server.movement;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.primitives.PrimitiveType;

import java.util.ArrayList;
import java.util.List;

final class PredictionPrimitiveGeometry {

    private static final int SPHERE_SEGMENTS = 12;
    private static final int CYLINDER_SEGMENTS = 16;

    record Triangle(Vector3 v0, Vector3 v1, Vector3 v2) {
    }

    private PredictionPrimitiveGeometry() {
    }

    static List<Triangle> generateMesh(
            PrimitiveType type,
            Vector3 pos,
            Quaternion rot,
            Vector3 scale,
            List<Vector3> customVerts,
            List<Integer> customInds
    ) {
        List<Triangle> triangles = new ArrayList<>();

        Vector3 resolvedScale = scale != null ? scale : Vector3.one();
        Vector3 resolvedPos = pos != null ? pos : Vector3.zero();

        if (type == null) {
            return triangles;
        }

        switch (type) {
            case CUBE -> generateCube(triangles, resolvedPos, rot, resolvedScale);
            case SPHERE -> generateSphere(triangles, resolvedPos, rot, resolvedScale);
            case CYLINDER -> generateCylinder(triangles, resolvedPos, rot, resolvedScale);
            case CAPSULE -> generateCapsule(triangles, resolvedPos, rot, resolvedScale);
            case CONE -> generateCone(triangles, resolvedPos, rot, resolvedScale);
            case PLANE -> generatePlane(triangles, resolvedPos, rot, resolvedScale);
            case MESH -> {
                if (customVerts != null && !customVerts.isEmpty() && customInds != null && !customInds.isEmpty()) {
                    generateCustomMesh(triangles, resolvedPos, rot, resolvedScale, customVerts, customInds);
                }
            }
            default -> {
            }
        }

        return triangles;
    }

    private static void generateCube(List<Triangle> triangles, Vector3 pos, Quaternion rot, Vector3 scale) {
        float hx = scale.x / 2.0f;
        float hy = scale.y / 2.0f;
        float hz = scale.z / 2.0f;

        Vector3[] corners = {
                transform(new Vector3(-hx, -hy, -hz), pos, rot), transform(new Vector3(hx, -hy, -hz), pos, rot),
                transform(new Vector3(hx, hy, -hz), pos, rot), transform(new Vector3(-hx, hy, -hz), pos, rot),
                transform(new Vector3(-hx, -hy, hz), pos, rot), transform(new Vector3(hx, -hy, hz), pos, rot),
                transform(new Vector3(hx, hy, hz), pos, rot), transform(new Vector3(-hx, hy, hz), pos, rot)
        };

        int[][] faces = {
                {0, 1, 2, 3}, {5, 4, 7, 6}, {1, 5, 6, 2},
                {4, 0, 3, 7}, {3, 2, 6, 7}, {4, 5, 1, 0}
        };

        for (int[] f : faces) {
            triangles.add(new Triangle(corners[f[0]], corners[f[1]], corners[f[2]]));
            triangles.add(new Triangle(corners[f[0]], corners[f[2]], corners[f[3]]));
        }
    }

    private static void generateSphere(List<Triangle> triangles, Vector3 pos, Quaternion rot, Vector3 scale) {
        float radius = Math.max(Math.max(scale.x, scale.y), scale.z) / 2.0f;
        int rings = SPHERE_SEGMENTS / 2;
        int segments = SPHERE_SEGMENTS;

        Vector3[][] grid = new Vector3[rings + 1][segments];

        for (int r = 0; r <= rings; r++) {
            float phi = (float) (Math.PI * r / rings);
            float y = (float) Math.cos(phi) * radius;
            float rRad = (float) Math.sin(phi) * radius;

            for (int s = 0; s < segments; s++) {
                float theta = (float) (2 * Math.PI * s / segments);
                grid[r][s] = transform(
                        new Vector3(
                                (float) Math.cos(theta) * rRad,
                                y,
                                (float) Math.sin(theta) * rRad
                        ),
                        pos,
                        rot
                );
            }
        }

        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < segments; s++) {
                int nextS = (s + 1) % segments;
                Vector3 p0 = grid[r][s];
                Vector3 p1 = grid[r][nextS];
                Vector3 p2 = grid[r + 1][nextS];
                Vector3 p3 = grid[r + 1][s];

                if (r > 0) triangles.add(new Triangle(p0, p1, p2));
                if (r < rings - 1) triangles.add(new Triangle(p0, p2, p3));
            }
        }
    }

    private static void generateCylinder(List<Triangle> triangles, Vector3 pos, Quaternion rot, Vector3 scale) {
        float radius = Math.max(scale.x, scale.z) / 2.0f;
        float hh = scale.y / 2.0f;

        Vector3 topC = transform(new Vector3(0, hh, 0), pos, rot);
        Vector3 botC = transform(new Vector3(0, -hh, 0), pos, rot);

        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            double angle = 2 * Math.PI * i / CYLINDER_SEGMENTS;
            double nextAngle = 2 * Math.PI * ((i + 1) % CYLINDER_SEGMENTS) / CYLINDER_SEGMENTS;

            float x1 = (float) Math.cos(angle) * radius;
            float z1 = (float) Math.sin(angle) * radius;
            float x2 = (float) Math.cos(nextAngle) * radius;
            float z2 = (float) Math.sin(nextAngle) * radius;

            Vector3 t1 = transform(new Vector3(x1, hh, z1), pos, rot);
            Vector3 b1 = transform(new Vector3(x1, -hh, z1), pos, rot);
            Vector3 t2 = transform(new Vector3(x2, hh, z2), pos, rot);
            Vector3 b2 = transform(new Vector3(x2, -hh, z2), pos, rot);

            triangles.add(new Triangle(topC, t2, t1));
            triangles.add(new Triangle(botC, b1, b2));
            triangles.add(new Triangle(t1, b1, b2));
            triangles.add(new Triangle(t1, b2, t2));
        }
    }

    private static void generateCapsule(List<Triangle> triangles, Vector3 pos, Quaternion rot, Vector3 scale) {
        float radius = Math.max(scale.x, scale.z) / 2.0f;
        float hh = Math.max(0, scale.y / 2.0f - radius);
        int hemiRings = CYLINDER_SEGMENTS / 4;

        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            double a1 = 2 * Math.PI * i / CYLINDER_SEGMENTS;
            double a2 = 2 * Math.PI * ((i + 1) % CYLINDER_SEGMENTS) / CYLINDER_SEGMENTS;

            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;
            float x2 = (float) Math.cos(a2) * radius;
            float z2 = (float) Math.sin(a2) * radius;

            Vector3 t1 = transform(new Vector3(x1, hh, z1), pos, rot);
            Vector3 t2 = transform(new Vector3(x2, hh, z2), pos, rot);
            Vector3 b1 = transform(new Vector3(x1, -hh, z1), pos, rot);
            Vector3 b2 = transform(new Vector3(x2, -hh, z2), pos, rot);

            triangles.add(new Triangle(t1, b1, b2));
            triangles.add(new Triangle(t1, b2, t2));
        }

        generateHemisphere(triangles, pos, rot, radius, hh, hemiRings, true);
        generateHemisphere(triangles, pos, rot, radius, -hh, hemiRings, false);
    }

    private static void generateHemisphere(
            List<Triangle> triangles,
            Vector3 pos,
            Quaternion rot,
            float radius,
            float yOffset,
            int rings,
            boolean isTop
    ) {
        for (int r = 0; r < rings; r++) {
            float phi1 = (float) (Math.PI / 2 * r / rings);
            float phi2 = (float) (Math.PI / 2 * (r + 1) / rings);

            float y1 = yOffset + (isTop ? 1 : -1) * (float) Math.sin(phi1) * radius;
            float y2 = yOffset + (isTop ? 1 : -1) * (float) Math.sin(phi2) * radius;
            float r1 = (float) Math.cos(phi1) * radius;
            float r2 = (float) Math.cos(phi2) * radius;

            for (int s = 0; s < CYLINDER_SEGMENTS; s++) {
                double theta1 = 2 * Math.PI * s / CYLINDER_SEGMENTS;
                double theta2 = 2 * Math.PI * ((s + 1) % CYLINDER_SEGMENTS) / CYLINDER_SEGMENTS;

                Vector3 p0 = transform(new Vector3((float) Math.cos(theta1) * r1, y1, (float) Math.sin(theta1) * r1), pos, rot);
                Vector3 p1 = transform(new Vector3((float) Math.cos(theta2) * r1, y1, (float) Math.sin(theta2) * r1), pos, rot);
                Vector3 p2 = transform(new Vector3((float) Math.cos(theta2) * r2, y2, (float) Math.sin(theta2) * r2), pos, rot);
                Vector3 p3 = transform(new Vector3((float) Math.cos(theta1) * r2, y2, (float) Math.sin(theta1) * r2), pos, rot);

                if (isTop) {
                    triangles.add(new Triangle(p0, p1, p2));
                    triangles.add(new Triangle(p0, p2, p3));
                } else {
                    triangles.add(new Triangle(p0, p2, p1));
                    triangles.add(new Triangle(p0, p3, p2));
                }
            }
        }
    }

    private static void generateCone(List<Triangle> triangles, Vector3 pos, Quaternion rot, Vector3 scale) {
        float radius = Math.max(scale.x, scale.z) / 2.0f;
        float height = scale.y;
        Vector3 apex = transform(new Vector3(0, height, 0), pos, rot);
        Vector3 center = transform(new Vector3(0, 0, 0), pos, rot);

        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            double a1 = 2 * Math.PI * i / CYLINDER_SEGMENTS;
            double a2 = 2 * Math.PI * ((i + 1) % CYLINDER_SEGMENTS) / CYLINDER_SEGMENTS;

            Vector3 p1 = transform(new Vector3((float) Math.cos(a1) * radius, 0, (float) Math.sin(a1) * radius), pos, rot);
            Vector3 p2 = transform(new Vector3((float) Math.cos(a2) * radius, 0, (float) Math.sin(a2) * radius), pos, rot);

            triangles.add(new Triangle(apex, p2, p1));
            triangles.add(new Triangle(center, p1, p2));
        }
    }

    private static void generatePlane(List<Triangle> triangles, Vector3 pos, Quaternion rot, Vector3 scale) {
        float hx = scale.x / 2.0f;
        float hz = scale.z / 2.0f;
        Vector3 p0 = transform(new Vector3(-hx, 0, -hz), pos, rot);
        Vector3 p1 = transform(new Vector3(hx, 0, -hz), pos, rot);
        Vector3 p2 = transform(new Vector3(hx, 0, hz), pos, rot);
        Vector3 p3 = transform(new Vector3(-hx, 0, hz), pos, rot);

        triangles.add(new Triangle(p0, p1, p2));
        triangles.add(new Triangle(p0, p2, p3));
        triangles.add(new Triangle(p2, p1, p0));
        triangles.add(new Triangle(p3, p2, p0));
    }

    private static void generateCustomMesh(
            List<Triangle> triangles,
            Vector3 pos,
            Quaternion rot,
            Vector3 scale,
            List<Vector3> verts,
            List<Integer> inds
    ) {
        for (int i = 0; i + 2 < inds.size(); i += 3) {
            int i0 = inds.get(i);
            int i1 = inds.get(i + 1);
            int i2 = inds.get(i + 2);
            if (i0 < verts.size() && i1 < verts.size() && i2 < verts.size()) {
                Vector3 v0 = scale(verts.get(i0), scale);
                Vector3 v1 = scale(verts.get(i1), scale);
                Vector3 v2 = scale(verts.get(i2), scale);
                triangles.add(new Triangle(transform(v0, pos, rot), transform(v1, pos, rot), transform(v2, pos, rot)));
            }
        }
    }

    private static Vector3 scale(Vector3 v, Vector3 s) {
        return new Vector3(v.x * s.x, v.y * s.y, v.z * s.z);
    }

    private static Vector3 transform(Vector3 local, Vector3 pos, Quaternion rot) {
        Vector3 r = (rot != null) ? rot.rotate(local) : local;
        return new Vector3(r.x + pos.x, r.y + pos.y, r.z + pos.z);
    }
}

