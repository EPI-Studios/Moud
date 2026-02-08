package com.moud.client.primitives;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.client.util.IdentifierUtils;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.List;

public class PrimitiveRenderer {
    private static final Identifier DEFAULT_TEXTURE = Identifier.of("minecraft", "textures/block/white_concrete.png");
    private static final float HALF = 0.5f;
    private static final int SPHERE_LAT = 16;
    private static final int SPHERE_LON = 24;
    private static final int CYLINDER_SEGMENTS = 16;

    public void renderSolid(ClientPrimitive primitive, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (consumers == null || primitive == null) {
            return;
        }
        MoudPackets.PrimitiveMaterial material = primitive.getMaterial();
        Identifier texture = resolveTexture(material);
        boolean xray = material != null && material.renderThroughBlocks();
        boolean unlit = material != null && material.unlit();
        boolean doubleSided = material != null && material.doubleSided();

        float a;
        boolean opaque = false;
        RenderLayer layer = PrimitiveRenderLayers.getLayer(texture, unlit, doubleSided, xray, opaque);
        VertexConsumer consumer = consumers.getBuffer(layer);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();

        float r = material != null ? material.r() : 1.0f;
        float g = material != null ? material.g() : 1.0f;
        float b = material != null ? material.b() : 1.0f;
        a = material != null ? material.a() : 1.0f;

        switch (primitive.getType()) {
            case CUBE -> renderCube(consumer, matrix, entry, light, r, g, b, a);
            case SPHERE -> renderSphere(consumer, matrix, entry, light, r, g, b, a);
            case CYLINDER -> renderCylinder(consumer, matrix, entry, light, r, g, b, a);
            case CAPSULE -> renderCapsule(consumer, matrix, entry, light, r, g, b, a);
            case PLANE -> renderPlane(consumer, matrix, entry, light, r, g, b, a);
            case CONE -> renderCone(consumer, matrix, entry, light, r, g, b, a);
            case MESH -> renderMesh(primitive, consumer, matrix, entry, light, r, g, b, a);
            default -> renderCube(consumer, matrix, entry, light, r, g, b, a);
        }
    }

    public void renderLines(ClientPrimitive primitive, MatrixStack matrices, VertexConsumerProvider consumers,
                            boolean xray, int light) {
        List<Vector3> verts = primitive.getVertices();
        if (consumers == null || verts == null || verts.size() < 2) {
            return;
        }
        VertexConsumer consumer = consumers.getBuffer(PrimitiveRenderLayers.getLineLayer(xray));
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();

        MoudPackets.PrimitiveMaterial material = primitive.getMaterial();
        float r = material != null ? material.r() : 1.0f;
        float g = material != null ? material.g() : 1.0f;
        float b = material != null ? material.b() : 1.0f;
        float a = material != null ? material.a() : 1.0f;

        if (primitive.getType() == MoudPackets.PrimitiveType.LINE) {
            Vector3 v0 = verts.get(0);
            Vector3 v1 = verts.size() > 1 ? verts.get(1) : v0;
            writeLine(consumer, matrix, entry, v0, v1, r, g, b, a, light);
        } else {
            for (int i = 0; i < verts.size() - 1; i++) {
                Vector3 v0 = verts.get(i);
                Vector3 v1 = verts.get(i + 1);
                writeLine(consumer, matrix, entry, v0, v1, r, g, b, a, light);
            }
        }
    }

    private void renderCube(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                            int light, float r, float g, float b, float a) {
        // +Z Front
        addQuad(consumer, matrix, entry,
                -HALF, -HALF, HALF,
                HALF, -HALF, HALF,
                HALF, HALF, HALF,
                -HALF, HALF, HALF,
                0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f,
                light, r, g, b, a, 0f, 0f, 1f);

        // -Z Back
        addQuad(consumer, matrix, entry,
                HALF, -HALF, -HALF,
                -HALF, -HALF, -HALF,
                -HALF, HALF, -HALF,
                HALF, HALF, -HALF,
                0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f,
                light, r, g, b, a, 0f, 0f, -1f);

        // +X Right
        addQuad(consumer, matrix, entry,
                HALF, -HALF, HALF,
                HALF, -HALF, -HALF,
                HALF, HALF, -HALF,
                HALF, HALF, HALF,
                0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f,
                light, r, g, b, a, 1f, 0f, 0f);

        // -X Left
        addQuad(consumer, matrix, entry,
                -HALF, -HALF, -HALF,
                -HALF, -HALF, HALF,
                -HALF, HALF, HALF,
                -HALF, HALF, -HALF,
                0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f,
                light, r, g, b, a, -1f, 0f, 0f);

        // +Y Top
        addQuad(consumer, matrix, entry,
                -HALF, HALF, HALF,
                HALF, HALF, HALF,
                HALF, HALF, -HALF,
                -HALF, HALF, -HALF,
                0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f,
                light, r, g, b, a, 0f, 1f, 0f);

        // -Y Bottom
        addQuad(consumer, matrix, entry,
                -HALF, -HALF, -HALF,
                HALF, -HALF, -HALF,
                HALF, -HALF, HALF,
                -HALF, -HALF, HALF,
                0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f,
                light, r, g, b, a, 0f, -1f, 0f);
    }

    private void renderPlane(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                             int light, float r, float g, float b, float a) {
        addQuad(consumer, matrix, entry,
                -HALF, 0f, -HALF,
                HALF, 0f, -HALF,
                HALF, 0f, HALF,
                -HALF, 0f, HALF,
                0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f,
                light, r, g, b, a, 0f, 1f, 0f);
    }

    private void renderSphere(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                              int light, float r, float g, float b, float a) {
        for (int lat = 0; lat < SPHERE_LAT; lat++) {
            float theta0 = (float) Math.PI * lat / SPHERE_LAT;
            float theta1 = (float) Math.PI * (lat + 1) / SPHERE_LAT;

            for (int lon = 0; lon < SPHERE_LON; lon++) {
                float phi0 = (float) (2 * Math.PI * lon / SPHERE_LON);
                float phi1 = (float) (2 * Math.PI * (lon + 1) / SPHERE_LON);

                Vector3 p0 = spherical(theta0, phi0);
                Vector3 p1 = spherical(theta1, phi0);
                Vector3 p2 = spherical(theta1, phi1);
                Vector3 p3 = spherical(theta0, phi1);

                addTri(consumer, matrix, entry, p0, p2, p1, light, r, g, b, a);
                addTri(consumer, matrix, entry, p0, p3, p2, light, r, g, b, a);
            }
        }
    }

    private Vector3 spherical(float theta, float phi) {
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        return new Vector3(
                sinTheta * cosPhi * HALF,
                cosTheta * HALF,
                sinTheta * sinPhi * HALF
        );
    }

    private void renderCylinder(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                                int light, float r, float g, float b, float a) {
        float halfHeight = HALF;
        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            float angle0 = (float) (2 * Math.PI * i / CYLINDER_SEGMENTS);
            float angle1 = (float) (2 * Math.PI * (i + 1) / CYLINDER_SEGMENTS);
            float x0 = (float) Math.cos(angle0) * HALF;
            float z0 = (float) Math.sin(angle0) * HALF;
            float x1 = (float) Math.cos(angle1) * HALF;
            float z1 = (float) Math.sin(angle1) * HALF;

            Vector3 v00 = new Vector3(x0, -halfHeight, z0);
            Vector3 v01 = new Vector3(x0, halfHeight, z0);
            Vector3 v10 = new Vector3(x1, -halfHeight, z1);
            Vector3 v11 = new Vector3(x1, halfHeight, z1);

            addTri(consumer, matrix, entry, v00, v01, v10, light, r, g, b, a);
            addTri(consumer, matrix, entry, v10, v01, v11, light, r, g, b, a);

            Vector3 topCenter = new Vector3(0f, halfHeight, 0f);
            addTri(consumer, matrix, entry, topCenter, v11, v01, light, r, g, b, a, 0f, 1f, 0f);
            Vector3 bottomCenter = new Vector3(0f, -halfHeight, 0f);
            addTri(consumer, matrix, entry, bottomCenter, v00, v10, light, r, g, b, a, 0f, -1f, 0f);
        }
    }

    private void renderCapsule(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                               int light, float r, float g, float b, float a) {
        renderCylinder(consumer, matrix, entry, light, r, g, b, a);
        for (int lat = 0; lat < SPHERE_LAT / 2; lat++) {
            float theta0 = (float) (Math.PI * lat / SPHERE_LAT);
            float theta1 = (float) (Math.PI * (lat + 1) / SPHERE_LAT);

            for (int lon = 0; lon < SPHERE_LON; lon++) {
                float phi0 = (float) (2 * Math.PI * lon / SPHERE_LON);
                float phi1 = (float) (2 * Math.PI * (lon + 1) / SPHERE_LON);

                Vector3 p0 = spherical(theta0, phi0);
                Vector3 p1 = spherical(theta1, phi0);
                Vector3 p2 = spherical(theta1, phi1);
                Vector3 p3 = spherical(theta0, phi1);

                p0.y += HALF;
                p1.y += HALF;
                p2.y += HALF;
                p3.y += HALF;

                addTri(consumer, matrix, entry, p0, p1, p2, light, r, g, b, a);
                addTri(consumer, matrix, entry, p0, p2, p3, light, r, g, b, a);
            }
        }
        for (int lat = 0; lat < SPHERE_LAT / 2; lat++) {
            float theta0 = (float) (Math.PI * lat / SPHERE_LAT);
            float theta1 = (float) (Math.PI * (lat + 1) / SPHERE_LAT);

            for (int lon = 0; lon < SPHERE_LON; lon++) {
                float phi0 = (float) (2 * Math.PI * lon / SPHERE_LON);
                float phi1 = (float) (2 * Math.PI * (lon + 1) / SPHERE_LON);

                Vector3 p0 = spherical(theta0, phi0);
                Vector3 p1 = spherical(theta1, phi0);
                Vector3 p2 = spherical(theta1, phi1);
                Vector3 p3 = spherical(theta0, phi1);

                p0.y -= HALF;
                p1.y -= HALF;
                p2.y -= HALF;
                p3.y -= HALF;

                addTri(consumer, matrix, entry, p3, p2, p1, light, r, g, b, a);
                addTri(consumer, matrix, entry, p3, p1, p0, light, r, g, b, a);
            }
        }
    }

    private void renderCone(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                            int light, float r, float g, float b, float a) {
        Vector3 tip = new Vector3(0f, HALF, 0f);
        float baseY = -HALF;
        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            float angle0 = (float) (2 * Math.PI * i / CYLINDER_SEGMENTS);
            float angle1 = (float) (2 * Math.PI * (i + 1) / CYLINDER_SEGMENTS);
            Vector3 v0 = new Vector3((float) Math.cos(angle0) * HALF, baseY, (float) Math.sin(angle0) * HALF);
            Vector3 v1 = new Vector3((float) Math.cos(angle1) * HALF, baseY, (float) Math.sin(angle1) * HALF);

            addTri(consumer, matrix, entry, tip, v1, v0, light, r, g, b, a);

            Vector3 baseCenter = new Vector3(0f, baseY, 0f);
            addTri(consumer, matrix, entry, baseCenter, v0, v1, light, r, g, b, a, 0f, -1f, 0f);
        }
    }

    private void renderMesh(ClientPrimitive primitive, VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                            int light, float r, float g, float b, float a) {
        List<Vector3> verts = primitive.getVertices();
        if (verts == null || verts.size() < 3) {
            return;
        }
        List<Integer> indices = primitive.getIndices();
        if (indices != null && indices.size() >= 3) {
            for (int i = 0; i + 2 < indices.size(); i += 3) {
                Integer ia = indices.get(i);
                Integer ib = indices.get(i + 1);
                Integer ic = indices.get(i + 2);
                if (ia == null || ib == null || ic == null) continue;
                if (ia < 0 || ib < 0 || ic < 0) continue;
                if (ia >= verts.size() || ib >= verts.size() || ic >= verts.size()) continue;
                Vector3 a0 = verts.get(ia);
                Vector3 b0 = verts.get(ib);
                Vector3 c0 = verts.get(ic);
                if (a0 == null || b0 == null || c0 == null) continue;
                addTri(consumer, matrix, entry, a0, b0, c0, light, r, g, b, a);
            }
            return;
        }
        for (int i = 0; i + 2 < verts.size(); i += 3) {
            Vector3 a0 = verts.get(i);
            Vector3 b0 = verts.get(i + 1);
            Vector3 c0 = verts.get(i + 2);
            if (a0 == null || b0 == null || c0 == null) continue;
            addTri(consumer, matrix, entry, a0, b0, c0, light, r, g, b, a);
        }
    }

    private void addTri(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                        Vector3 v0, Vector3 v1, Vector3 v2, int light,
                        float r, float g, float b, float a) {
        Vector3 normal = computeNormal(v0, v1, v2);
        addTri(consumer, matrix, entry, v0, v1, v2, light, r, g, b, a, normal.x, normal.y, normal.z);
    }

    private void addTri(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                        Vector3 v0, Vector3 v1, Vector3 v2, int light,
                        float r, float g, float b, float a,
                        float nx, float ny, float nz) {
        consumer.vertex(matrix, v0.x, v0.y, v0.z)
                .color(r, g, b, a)
                .texture(0f, 0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, nx, ny, nz);
        consumer.vertex(matrix, v1.x, v1.y, v1.z)
                .color(r, g, b, a)
                .texture(1f, 0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, nx, ny, nz);
        consumer.vertex(matrix, v2.x, v2.y, v2.z)
                .color(r, g, b, a)
                .texture(1f, 1f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, nx, ny, nz);
    }

    private void addQuad(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                         float x0, float y0, float z0,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
                         int light, float r, float g, float b, float a,
                         float nx, float ny, float nz) {
        // Triangle 1: 0-1-2
        consumer.vertex(matrix, x0, y0, z0).color(r, g, b, a).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
        consumer.vertex(matrix, x1, y1, z1).color(r, g, b, a).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
        consumer.vertex(matrix, x2, y2, z2).color(r, g, b, a).texture(u2, v2).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);

        // Triangle 2: 0-2-3
        consumer.vertex(matrix, x0, y0, z0).color(r, g, b, a).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
        consumer.vertex(matrix, x2, y2, z2).color(r, g, b, a).texture(u2, v2).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
        consumer.vertex(matrix, x3, y3, z3).color(r, g, b, a).texture(u3, v3).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
    }

    private void writeLine(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                           Vector3 a, Vector3 b, float r, float g, float bCol, float aCol, int light) {
        consumer.vertex(matrix, a.x, a.y, a.z)
                .color(r, g, bCol, aCol)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0f, 1f, 0f);
        consumer.vertex(matrix, b.x, b.y, b.z)
                .color(r, g, bCol, aCol)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0f, 1f, 0f);
    }

    private Vector3 computeNormal(Vector3 p0, Vector3 p1, Vector3 p2) {
        float ux = p1.x - p0.x;
        float uy = p1.y - p0.y;
        float uz = p1.z - p0.z;

        float vx = p2.x - p0.x;
        float vy = p2.y - p0.y;
        float vz = p2.z - p0.z;

        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;

        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-5f) {
            return new Vector3(0f, 1f, 0f);
        }
        return new Vector3(nx / len, ny / len, nz / len);
    }

    private Identifier resolveTexture(MoudPackets.PrimitiveMaterial material) {
        if (material == null || material.texture() == null || material.texture().isBlank()) {
            return DEFAULT_TEXTURE;
        }
        Identifier parsed = IdentifierUtils.resolveMoudIdentifier(material.texture());
        return parsed != null ? parsed : DEFAULT_TEXTURE;
    }
}
