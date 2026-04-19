package com.moud.client.fabric.render.scene.subrender;

import com.moud.client.fabric.physics.CapsuleShapeData;
import com.moud.client.fabric.render.Model3DRenderer;
import com.moud.client.fabric.render.VeilDebugRenderer;
import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.core.physics.CollisionGeometry;
import com.moud.net.protocol.SceneSnapshot;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class SceneDebugRenderer {
    public void render(List<SceneSnapshot.NodeSnapshot> nodes,
                       Map<Long, List<CollisionGeometry>> collisionGeometryCache,
                       java.util.function.Function<Long, Pose> poseResolver) {
        VeilDebugRenderer debug = VeilDebugRenderer.instance();
        debug.clear();

        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null) {
                continue;
            }
            Pose world = poseResolver.apply(node.nodeId());
            if (world == null) {
                continue;
            }

            boolean solid = isCollisionDebugSolid(node);
            boolean isPhysicsBody = "StaticBody3D".equals(node.type()) || "RigidBody3D".equals(node.type()) || "CharacterBody3D".equals(node.type());
            boolean isCsg = "CSGBox".equals(node.type()) || "CSGBlock".equals(node.type());
            if (!solid && !isPhysicsBody && !isCsg) {
                continue;
            }

            int color = isPhysicsBody ? 0x80FF00FF : 0x8000FF00;
            List<CollisionGeometry> hulls = collisionGeometryCache.get(node.nodeId());
            if (isCsg) {
                Matrix4f matrix = new Matrix4f()
                        .translation(world.pos.x, world.pos.y, world.pos.z)
                        .rotate(world.rot)
                        .scale(world.scale.x, world.scale.y, world.scale.z)
                        .translate(-0.5f, -0.5f, -0.5f);
                drawWireframeUnitCube(debug, matrix, color);
            } else if (hulls != null && !hulls.isEmpty()) {
                int hullColor = "Model3D".equals(node.type()) ? 0x8000FFFF : color;
                for (CollisionGeometry hull : hulls) {
                    drawCollisionGeometry(debug, hull, world, hullColor);
                }
            } else if ("Model3D".equals(node.type())) {
                Model3DRenderer.forEachCubeTransform(node, matrix -> {
                    Matrix4f worldMatrix = new Matrix4f()
                            .translation(world.pos.x, world.pos.y, world.pos.z)
                            .rotate(world.rot)
                            .scale(world.scale.x, world.scale.y, world.scale.z)
                            .mul(matrix);
                    drawWireframeUnitCube(debug, worldMatrix, 0x8000FFFF);
                });
            } else if ("MeshInstance3D".equals(node.type())) {
                String mesh = NodePropertyUtils.stringProp(node, "mesh");
                if (mesh == null || mesh.isBlank()) {
                    mesh = "box";
                }
                mesh = mesh.trim().toLowerCase(java.util.Locale.ROOT);
                if ("sphere".equals(mesh)) {
                    debug.sphere(world.pos, 0.5f * Math.max(world.scale.x, Math.max(world.scale.y, world.scale.z)), color, 16);
                } else {
                    Matrix4f matrix = new Matrix4f()
                            .translation(world.pos.x, world.pos.y, world.pos.z)
                            .rotate(world.rot)
                            .scale(world.scale.x, world.scale.y, world.scale.z)
                            .translate(-0.5f, -0.5f, -0.5f);
                    drawWireframeUnitCube(debug, matrix, color);
                }
            } else if (isPhysicsBody) {
                String shape = NodePropertyUtils.stringProp(node, "shape");
                if (shape == null || shape.isBlank()) {
                    shape = "auto";
                }
                shape = shape.trim().toLowerCase(java.util.Locale.ROOT);
                if ("sphere".equals(shape)) {
                    float radius = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "radius"), 0.5f);
                    debug.sphere(world.pos, radius, color, 16);
                } else if ("capsule".equals(shape)) {
                    drawBottomAnchoredCapsule(debug, world, CapsuleShapeData.fromNode(node), color);
                } else if ("box".equals(shape)) {
                    Matrix4f matrix = new Matrix4f()
                            .translation(world.pos.x, world.pos.y, world.pos.z)
                            .rotate(world.rot)
                            .scale(world.scale.x, world.scale.y, world.scale.z)
                            .translate(-0.5f, -0.5f, -0.5f);
                    drawWireframeUnitCube(debug, matrix, color);
                }
            }
        }
    }

    private boolean isCollisionDebugSolid(SceneSnapshot.NodeSnapshot node) {
        boolean solidByDefault = "CSGBox".equals(node.type())
                || "CSGBlock".equals(node.type())
                || "Model3D".equals(node.type())
                || "MeshInstance3D".equals(node.type())
                || "Sprite3D".equals(node.type())
                || "AnimatedSprite3D".equals(node.type());
        return NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "solid"), solidByDefault);
    }

    private void drawCollisionGeometry(VeilDebugRenderer debug, CollisionGeometry hull, Pose world, int color) {
        if (hull == null) {
            return;
        }
        float[] vertices = hull.vertices();
        int[] indices = hull.indices();
        if (vertices == null || vertices.length < 9) {
            return;
        }
        int vertexCount = vertices.length / 3;

        if (indices != null && indices.length >= 3) {
            for (int i = 0; i + 2 < indices.length; i += 3) {
                int i0 = indices[i];
                int i1 = indices[i + 1];
                int i2 = indices[i + 2];
                if (i0 < 0 || i0 >= vertexCount || i1 < 0 || i1 >= vertexCount || i2 < 0 || i2 >= vertexCount) {
                    continue;
                }
                Vector3f a = transformVertex(vertices, i0, world);
                Vector3f b = transformVertex(vertices, i1, world);
                Vector3f c = transformVertex(vertices, i2, world);
                debug.line(a, b, color, 1.0f);
                debug.line(b, c, color, 1.0f);
                debug.line(c, a, color, 1.0f);
            }
            return;
        }

        float cx = 0.0f;
        float cy = 0.0f;
        float cz = 0.0f;
        for (int i = 0; i < vertexCount; i++) {
            cx += vertices[i * 3];
            cy += vertices[i * 3 + 1];
            cz += vertices[i * 3 + 2];
        }
        cx /= vertexCount;
        cy /= vertexCount;
        cz /= vertexCount;
        Vector3f centroid = new Vector3f(cx, cy, cz);
        world.rot.transform(centroid);
        centroid.add(world.pos);
        for (int i = 0; i < vertexCount; i++) {
            Vector3f v = transformVertex(vertices, i, world);
            debug.line(centroid, v, color, 1.0f);
            Vector3f next = transformVertex(vertices, (i + 1) % vertexCount, world);
            debug.line(v, next, color, 1.0f);
        }
    }

    private void drawBottomAnchoredCapsule(VeilDebugRenderer debug, Pose world, CapsuleShapeData capsule, int color) {
        float radius = capsule.radius();
        float bottomY = world.pos.y + capsule.bottomSphereY();
        float topY = world.pos.y + capsule.topSphereY();

        debug.sphere(new Vector3f(world.pos.x, bottomY, world.pos.z), radius, color, 16);
        debug.sphere(new Vector3f(world.pos.x, topY, world.pos.z), radius, color, 16);
        debug.line(new Vector3f(world.pos.x + radius, bottomY, world.pos.z), new Vector3f(world.pos.x + radius, topY, world.pos.z), color, 1.0f);
        debug.line(new Vector3f(world.pos.x - radius, bottomY, world.pos.z), new Vector3f(world.pos.x - radius, topY, world.pos.z), color, 1.0f);
        debug.line(new Vector3f(world.pos.x, bottomY, world.pos.z + radius), new Vector3f(world.pos.x, topY, world.pos.z + radius), color, 1.0f);
        debug.line(new Vector3f(world.pos.x, bottomY, world.pos.z - radius), new Vector3f(world.pos.x, topY, world.pos.z - radius), color, 1.0f);
    }

    private Vector3f transformVertex(float[] vertices, int index, Pose world) {
        Vector3f vertex = new Vector3f(vertices[index * 3], vertices[index * 3 + 1], vertices[index * 3 + 2]);
        world.rot.transform(vertex);
        vertex.add(world.pos);
        return vertex;
    }

    private void drawWireframeUnitCube(VeilDebugRenderer debug, Matrix4f matrix, int color) {
        Vector3f[] points = new Vector3f[] {
                new Vector3f(0, 0, 0),
                new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0),
                new Vector3f(0, 1, 0),
                new Vector3f(0, 0, 1),
                new Vector3f(1, 0, 1),
                new Vector3f(1, 1, 1),
                new Vector3f(0, 1, 1)
        };
        for (Vector3f point : points) {
            matrix.transformPosition(point);
        }
        debug.line(points[0], points[1], color, 1.0f);
        debug.line(points[1], points[2], color, 1.0f);
        debug.line(points[2], points[3], color, 1.0f);
        debug.line(points[3], points[0], color, 1.0f);
        debug.line(points[4], points[5], color, 1.0f);
        debug.line(points[5], points[6], color, 1.0f);
        debug.line(points[6], points[7], color, 1.0f);
        debug.line(points[7], points[4], color, 1.0f);
        debug.line(points[0], points[4], color, 1.0f);
        debug.line(points[1], points[5], color, 1.0f);
        debug.line(points[2], points[6], color, 1.0f);
        debug.line(points[3], points[7], color, 1.0f);
    }
}
