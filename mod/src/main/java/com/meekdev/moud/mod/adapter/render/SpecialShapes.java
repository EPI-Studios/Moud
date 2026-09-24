package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshType;
import com.meekdev.moud.core.part.PartShape;
import com.meekdev.moud.core.part.Shapes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SpecialShapes {

    private static final int SIDES = 32;
    private static final int RINGS = 16;
    private static final int ROUNDING = 6;
    private static final double HEAD_CORNER = 0.22;
    private static final Vector3 DOWN = new Vector3(0, -1, 0);
    private static final Vector3 WEDGE_INSIDE = new Vector3(0, -0.2, 0.2);
    private static final double[][] QUAD_UVS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    private static final Map<MeshType, Model> MADE = new EnumMap<>(MeshType.class);

    private SpecialShapes() {}

    static Model of(MeshType type) {
        return MADE.computeIfAbsent(type, SpecialShapes::make);
    }

    private static Model make(MeshType type) {
        Builder out = new Builder();
        switch (type) {
            case SPHERE -> sphere(out);
            case CYLINDER -> cylinder(out);
            case WEDGE -> wedge(out);
            case HEAD -> head(out);
            default -> brick(out);
        }
        return out.model(type.name().toLowerCase(Locale.ROOT));
    }

    private static void brick(Builder out) {
        for (int sign : new int[] {-1, 1}) brickSide(out, new Vector3(sign, 0, 0), new Vector3(0, 0, -sign), DOWN);
        for (int sign : new int[] {-1, 1}) brickSide(out, new Vector3(0, sign, 0), new Vector3(1, 0, 0), new Vector3(0, 0, sign));
        for (int sign : new int[] {-1, 1}) brickSide(out, new Vector3(0, 0, sign), new Vector3(sign, 0, 0), DOWN);
    }

    private static void brickSide(Builder out, Vector3 normal, Vector3 across, Vector3 down) {
        Vector3 middle = normal.mul(0.5);
        Corner[] corners = new Corner[4];
        for (int n = 0; n < 4; n++) {
            double u = QUAD_UVS[n][0];
            double v = QUAD_UVS[n][1];
            Vector3 position = middle.add(across.mul(u - 0.5)).add(down.mul(v - 0.5));
            corners[n] = new Corner(position, normal, u, v);
        }
        out.quad(corners[0], corners[1], corners[2], corners[3]);
    }

    private static void wedge(Builder out) {
        for (Vector3[] face : Shapes.triangles(PartShape.WEDGE, Vector3.ONE)) {
            Vector3 normal = face[1].sub(face[0]).cross(face[2].sub(face[0])).normalize();
            Vector3 middle = face[0].add(face[1]).add(face[2]).mul(1.0 / 3);
            if (normal.dot(middle.sub(WEDGE_INSIDE)) < 0) normal = normal.neg();
            out.triangle(planar(face[0], normal), planar(face[1], normal), planar(face[2], normal));
        }
    }

    private static Corner planar(Vector3 position, Vector3 normal) {
        double facingX = Math.abs(normal.x());
        double facingY = Math.abs(normal.y());
        double facingZ = Math.abs(normal.z());
        if (facingY >= facingX && facingY >= facingZ) {
            return new Corner(position, normal, position.x() + 0.5, position.z() + 0.5);
        }
        if (facingX >= facingZ) return new Corner(position, normal, position.z() + 0.5, 0.5 - position.y());
        return new Corner(position, normal, position.x() + 0.5, 0.5 - position.y());
    }

    private static void sphere(Builder out) {
        for (int ring = 0; ring < RINGS; ring++) {
            for (int side = 0; side < SIDES; side++) {
                out.quad(onSphere(side, ring), onSphere(side + 1, ring), onSphere(side + 1, ring + 1), onSphere(side, ring + 1));
            }
        }
    }

    private static Corner onSphere(int side, int ring) {
        double u = side / (double) SIDES;
        double v = ring / (double) RINGS;
        double around = u * Math.PI * 2;
        double down = v * Math.PI;
        double x = Math.sin(down) * Math.cos(around);
        double y = Math.cos(down);
        double z = Math.sin(down) * Math.sin(around);
        Vector3 position = new Vector3(x, y, z).mul(0.5);
        return new Corner(position, position.normalize(), u, v);
    }

    private static void cylinder(Builder out) {
        Vector3 left = new Vector3(-0.5, 0, 0);
        Vector3 right = new Vector3(0.5, 0, 0);
        for (int side = 0; side < SIDES; side++) {
            double startU = side / (double) SIDES;
            double endU = (side + 1) / (double) SIDES;
            Vector3 start = rim(startU);
            Vector3 end = rim(endU);
            Vector3 startNormal = start.mul(2);
            Vector3 endNormal = end.mul(2);

            out.quad(new Corner(start.add(left), startNormal, startU, 0),
                    new Corner(end.add(left), endNormal, endU, 0),
                    new Corner(end.add(right), endNormal, endU, 1),
                    new Corner(start.add(right), startNormal, startU, 1));

            for (Vector3 cap : new Vector3[] {left, right}) {
                Vector3 normal = cap.mul(2);
                out.triangle(new Corner(cap, normal, 0.5, 0.5),
                        new Corner(cap.add(start), normal, 0.5 + start.z(), 0.5 - start.y()),
                        new Corner(cap.add(end), normal, 0.5 + end.z(), 0.5 - end.y()));
            }
        }
    }

    private static Vector3 rim(double around) {
        double angle = around * Math.PI * 2;
        return new Vector3(0, Math.cos(angle), Math.sin(angle)).mul(0.5);
    }

    private static void head(Builder out) {
        List<Ring> profile = headProfile();
        for (int ring = 0; ring < profile.size() - 1; ring++) {
            Ring upper = profile.get(ring);
            Ring lower = profile.get(ring + 1);
            for (int side = 0; side < SIDES; side++) {
                out.quad(upper.corner(side), upper.corner(side + 1), lower.corner(side + 1), lower.corner(side));
            }
        }
    }

    private static List<Ring> headProfile() {
        double topCenter = 0.5 - HEAD_CORNER;
        double bottomCenter = -0.5 + HEAD_CORNER;
        List<Ring> profile = new ArrayList<>();
        profile.add(new Ring(0, 0.5, 0, 1));
        for (int n = 0; n <= ROUNDING; n++) {
            profile.add(Ring.rounded(topCenter, Math.PI / 2 * (1 - n / (double) ROUNDING)));
        }
        for (int n = 0; n < ROUNDING; n++) {
            profile.add(Ring.rounded(bottomCenter, -Math.PI / 2 * ((n + 1) / (double) ROUNDING)));
        }
        profile.add(new Ring(0, -0.5, 0, -1));
        return profile;
    }

    private record Ring(double radius, double height, double normalRadius, double normalHeight) {

        static Ring rounded(double centerHeight, double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            return new Ring(0.5 - HEAD_CORNER + cos * HEAD_CORNER, centerHeight + sin * HEAD_CORNER, cos, sin);
        }

        Corner corner(int side) {
            double around = side / (double) SIDES * Math.PI * 2;
            double cos = Math.cos(around);
            double sin = Math.sin(around);
            Vector3 position = new Vector3(radius * cos, height, radius * sin);
            Vector3 normal = new Vector3(normalRadius * cos, normalHeight, normalRadius * sin).normalize();
            return new Corner(position, normal, side / (double) SIDES, 0.5 - height);
        }
    }

    private record Corner(Vector3 position, Vector3 normal, double u, double v) {}

    private static final class Builder {
        private static final double SLIVER = 1e-14;

        private float[] vertices = new float[1024];
        private int floats;

        void quad(Corner a, Corner b, Corner c, Corner d) {
            triangle(a, b, c);
            triangle(a, c, d);
        }

        void triangle(Corner a, Corner b, Corner c) {
            Vector3 across = b.position().sub(a.position()).cross(c.position().sub(a.position()));
            if (across.lengthSq() < SLIVER) return;
            Vector3 facing = a.normal().add(b.normal()).add(c.normal());
            put(a);
            if (across.dot(facing) < 0) {
                put(c);
                put(b);
            } else {
                put(b);
                put(c);
            }
        }

        private void put(Corner corner) {
            if (floats + ModelIR.VERTEX_STRIDE_FLOATS > vertices.length) vertices = Arrays.copyOf(vertices, vertices.length * 2);
            Vector3 position = corner.position();
            Vector3 normal = corner.normal();
            vertices[floats++] = (float) position.x();
            vertices[floats++] = (float) position.y();
            vertices[floats++] = (float) position.z();
            vertices[floats++] = (float) normal.x();
            vertices[floats++] = (float) normal.y();
            vertices[floats++] = (float) normal.z();
            vertices[floats++] = (float) corner.u();
            vertices[floats++] = (float) corner.v();
        }

        Model model(String name) {
            float[] packed = Arrays.copyOf(vertices, floats);
            int[] indices = new int[floats / ModelIR.VERTEX_STRIDE_FLOATS];
            for (int n = 0; n < indices.length; n++) indices[n] = n;
            ModelIR ir = new ModelIR();
            ir.setName(name);
            ModelIR.Material material = new ModelIR.Material();
            material.name = name;
            int index = ir.addMaterial(material);
            ir.addPart(new ModelIR.Part(packed, null, indices, index, -1, null, name));
            return Models.fromIR(ir);
        }
    }
}
