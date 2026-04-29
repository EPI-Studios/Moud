package com.moud.server.minestom.collision.source;

import com.moud.core.scene.Node;
import com.moud.core.physics.CollisionGeometry;

import java.util.Locale;

public final class BuiltinMeshGeometrySource implements CollisionGeometrySource {
    private static final int[] BOX_INDICES = {
            0, 1, 2, 0, 2, 3,
            4, 6, 5, 4, 7, 6,
            0, 4, 5, 0, 5, 1,
            1, 5, 6, 1, 6, 2,
            2, 6, 7, 2, 7, 3,
            3, 7, 4, 3, 4, 0
    };

    @Override
    public boolean supports(String typeId) {
        return "MeshInstance3D".equals(typeId)
                || "Sprite3D".equals(typeId)
                || "AnimatedSprite3D".equals(typeId)
                || "CSGBox".equals(typeId);
    }

    @Override
    public CollisionGeometry extract(Node node, String typeId) {
        if (node == null || typeId == null) {
            return CollisionGeometry.EMPTY;
        }
        String mesh = node.getProperty("mesh");
        if (mesh == null || mesh.isBlank()) {
            mesh = ("Sprite3D".equals(typeId) || "AnimatedSprite3D".equals(typeId)) ? "sprite_quad" : "box";
        }
        return switch (mesh.trim().toLowerCase(Locale.ROOT)) {
            case "plane", "quad", "sprite_quad", "subdivided_plane" -> plane();
            case "sphere" -> sphere(12, 8);
            default -> box();
        };
    }

    private static CollisionGeometry box() {
        float[] vertices = {
                -0.5f, -0.5f, -0.5f,
                 0.5f, -0.5f, -0.5f,
                 0.5f,  0.5f, -0.5f,
                -0.5f,  0.5f, -0.5f,
                -0.5f, -0.5f,  0.5f,
                 0.5f, -0.5f,  0.5f,
                 0.5f,  0.5f,  0.5f,
                -0.5f,  0.5f,  0.5f
        };
        return new CollisionGeometry(vertices, BOX_INDICES);
    }

    private static CollisionGeometry plane() {
        return new CollisionGeometry(
                new float[]{
                        -0.5f, -0.5f, 0.0f,
                         0.5f, -0.5f, 0.0f,
                         0.5f,  0.5f, 0.0f,
                        -0.5f,  0.5f, 0.0f
                },
                new int[]{0, 1, 2, 0, 2, 3}
        );
    }

    private static CollisionGeometry sphere(int sectors, int rings) {
        int vertexCount = (rings + 1) * (sectors + 1);
        float[] vertices = new float[vertexCount * 3];
        int cursor = 0;
        for (int ring = 0; ring <= rings; ring++) {
            double v = ring / (double) rings;
            double phi = Math.PI * v;
            double y = Math.cos(phi) * 0.5;
            double r = Math.sin(phi) * 0.5;
            for (int sector = 0; sector <= sectors; sector++) {
                double u = sector / (double) sectors;
                double theta = Math.PI * 2.0 * u;
                vertices[cursor++] = (float) (Math.cos(theta) * r);
                vertices[cursor++] = (float) y;
                vertices[cursor++] = (float) (Math.sin(theta) * r);
            }
        }
        int[] indices = new int[rings * sectors * 6];
        cursor = 0;
        int stride = sectors + 1;
        for (int ring = 0; ring < rings; ring++) {
            for (int sector = 0; sector < sectors; sector++) {
                int i0 = ring * stride + sector;
                int i1 = i0 + 1;
                int i2 = i0 + stride;
                int i3 = i2 + 1;
                indices[cursor++] = i0;
                indices[cursor++] = i2;
                indices[cursor++] = i1;
                indices[cursor++] = i1;
                indices[cursor++] = i2;
                indices[cursor++] = i3;
            }
        }
        return new CollisionGeometry(vertices, indices);
    }
}
