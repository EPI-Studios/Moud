package com.moud.client.fabric.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class VeilDebugRenderer implements DebugRenderer {
    private static VeilDebugRenderer instance;

    private final List<Shape> shapes = new ArrayList<>();

    public static VeilDebugRenderer instance() {
        if (instance == null) {
            instance = new VeilDebugRenderer();
        }
        return instance;
    }

    @Override
    public void line(Vector3f from, Vector3f to, int colorARGB, float thickness) {
        if (from == null || to == null) {
            return;
        }
        synchronized (shapes) {
            shapes.add(new LineShape(from.x, from.y, from.z, to.x, to.y, to.z, colorARGB, thickness));
        }
    }

    @Override
    public void box(Vector3f min, Vector3f max, int colorARGB, float thickness) {
        if (min == null || max == null) {
            return;
        }
        synchronized (shapes) {
            shapes.add(new BoxShape(min.x, min.y, min.z, max.x, max.y, max.z, colorARGB, thickness));
        }
    }

    @Override
    public void frustum(Vector3f[] points, int colorARGB, float thickness) {
        if (points == null || points.length != 8) {
            throw new IllegalArgumentException("Frustum requires 8 points");
        }
        float[] xyz = new float[8 * 3];
        for (int i = 0; i < 8; i++) {
            Vector3f p = points[i];
            if (p == null) {
                return;
            }
            int o = i * 3;
            xyz[o] = p.x;
            xyz[o + 1] = p.y;
            xyz[o + 2] = p.z;
        }
        synchronized (shapes) {
            shapes.add(new FrustumShape(xyz, colorARGB, thickness));
        }
    }

    @Override
    public void sphere(Vector3f center, float radius, int colorARGB, int segments) {
        if (center == null) {
            return;
        }
        synchronized (shapes) {
            shapes.add(new SphereShape(center.x, center.y, center.z, radius, colorARGB, segments));
        }
    }

    @Override
    public void clear() {
        synchronized (shapes) {
            shapes.clear();
        }
    }

    public void render(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Camera camera) {
        if (matrices == null || consumers == null || camera == null) {
            return;
        }
        List<Shape> toRender;
        synchronized (shapes) {
            if (shapes.isEmpty()) {
                return;
            }
            toRender = new ArrayList<>(shapes);
        }

        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        Vec3d pos = camera.getPos();
        matrices.push();
        matrices.translate(-pos.x, -pos.y, -pos.z);
        try {
            for (Shape shape : toRender) {
                shape.render(matrices, lines);
            }
        } finally {
            matrices.pop();
        }
    }

    private sealed interface Shape {
        void render(MatrixStack matrices, VertexConsumer consumer);
    }

    private record LineShape(float fromX, float fromY, float fromZ,
                             float toX, float toY, float toZ,
                             int color, float thickness) implements Shape {
        @Override
        public void render(MatrixStack matrices, VertexConsumer consumer) {
            Matrix4f mat = matrices.peek().getPositionMatrix();
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float a = ((color >> 24) & 0xFF) / 255f;

            consumer.vertex(mat, fromX, fromY, fromZ).color(r, g, b, a).normal(matrices.peek(), 0f, 1f, 0f);
            consumer.vertex(mat, toX, toY, toZ).color(r, g, b, a).normal(matrices.peek(), 0f, 1f, 0f);
        }
    }

    private record BoxShape(float minX, float minY, float minZ,
                            float maxX, float maxY, float maxZ,
                            int color, float thickness) implements Shape {
        @Override
        public void render(MatrixStack matrices, VertexConsumer consumer) {
            Vector3f[] corners = {
                    new Vector3f(minX, minY, minZ),
                    new Vector3f(maxX, minY, minZ),
                    new Vector3f(maxX, maxY, minZ),
                    new Vector3f(minX, maxY, minZ),
                    new Vector3f(minX, minY, maxZ),
                    new Vector3f(maxX, minY, maxZ),
                    new Vector3f(maxX, maxY, maxZ),
                    new Vector3f(minX, maxY, maxZ)
            };

            int[][] edges = {
                    {0,1}, {1,2}, {2,3}, {3,0},
                    {4,5}, {5,6}, {6,7}, {7,4},
                    {0,4}, {1,5}, {2,6}, {3,7}
            };

            for (int[] edge : edges) {
                Vector3f a = corners[edge[0]];
                Vector3f b = corners[edge[1]];
                new LineShape(a.x, a.y, a.z, b.x, b.y, b.z, color, thickness).render(matrices, consumer);
            }
        }
    }

    private record FrustumShape(float[] pointsXyz, int color, float thickness) implements Shape {
        @Override
        public void render(MatrixStack matrices, VertexConsumer consumer) {
            int[][] edges = {
                    {0,1}, {1,2}, {2,3}, {3,0},
                    {4,5}, {5,6}, {6,7}, {7,4},
                    {0,4}, {1,5}, {2,6}, {3,7}
            };

            for (int[] edge : edges) {
                int a = edge[0] * 3;
                int b = edge[1] * 3;
                new LineShape(
                        pointsXyz[a], pointsXyz[a + 1], pointsXyz[a + 2],
                        pointsXyz[b], pointsXyz[b + 1], pointsXyz[b + 2],
                        color,
                        thickness
                ).render(matrices, consumer);
            }
        }
    }

    private record SphereShape(float cx, float cy, float cz, float radius, int color, int segments) implements Shape {
        @Override
        public void render(MatrixStack matrices, VertexConsumer consumer) {
            for (int axis = 0; axis < 3; axis++) {
                for (int i = 0; i < segments; i++) {
                    float angle1 = (float) (i * 2 * Math.PI / segments);
                    float angle2 = (float) ((i + 1) * 2 * Math.PI / segments);

                    Vector3f p1 = pointOnCircle(angle1, axis);
                    Vector3f p2 = pointOnCircle(angle2, axis);

                    new LineShape(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, color, 1f).render(matrices, consumer);
                }
            }
        }

        private Vector3f pointOnCircle(float angle, int axis) {
            float x = (float) Math.cos(angle) * radius;
            float y = (float) Math.sin(angle) * radius;

            return switch (axis) {
                case 0 -> new Vector3f(cx, cy + y, cz + x);
                case 1 -> new Vector3f(cx + x, cy, cz + y);
                default -> new Vector3f(cx + x, cy + y, cz);
            };
        }
    }
}