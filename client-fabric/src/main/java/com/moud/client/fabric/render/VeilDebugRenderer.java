package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;


public class VeilDebugRenderer implements DebugRenderer {

    private static VeilDebugRenderer instance;

    public static VeilDebugRenderer instance() {
        if (instance == null) instance = new VeilDebugRenderer();
        return instance;
    }

    private static final float DEFAULT_WIDTH = 2.0f;
    private final List<Line> lines = new ArrayList<>();

    @Override
    public void line(Vector3f from, Vector3f to, int colorARGB, float thickness) {
        if (from == null || to == null) return;
        addLine(from.x, from.y, from.z, to.x, to.y, to.z, colorARGB, resolveWidth(thickness));
    }

    @Override
    public void box(Vector3f min, Vector3f max, int colorARGB, float thickness) {
        if (min == null || max == null) return;

        float x0 = min.x, y0 = min.y, z0 = min.z;
        float x1 = max.x, y1 = max.y, z1 = max.z;
        float w = resolveWidth(thickness);

        synchronized (lines) {
            // bottom ring
            addLine(x0, y0, z0,  x1, y0, z0,  colorARGB, w);
            addLine(x1, y0, z0,  x1, y0, z1,  colorARGB, w);
            addLine(x1, y0, z1,  x0, y0, z1,  colorARGB, w);
            addLine(x0, y0, z1,  x0, y0, z0,  colorARGB, w);

            // top ring
            addLine(x0, y1, z0,  x1, y1, z0,  colorARGB, w);
            addLine(x1, y1, z0,  x1, y1, z1,  colorARGB, w);
            addLine(x1, y1, z1,  x0, y1, z1,  colorARGB, w);
            addLine(x0, y1, z1,  x0, y1, z0,  colorARGB, w);

            // vertical pillars
            addLine(x0, y0, z0,  x0, y1, z0,  colorARGB, w);
            addLine(x1, y0, z0,  x1, y1, z0,  colorARGB, w);
            addLine(x1, y0, z1,  x1, y1, z1,  colorARGB, w);
            addLine(x0, y0, z1,  x0, y1, z1,  colorARGB, w);
        }
    }

    @Override
    public void frustum(Vector3f[] points, int colorARGB, float thickness) {
        if (points == null || points.length != 8) return;
        float w = resolveWidth(thickness);

        // near face: 0-3, far face: 4-7, connecting edges
        int[][] edges = {
                {0,1}, {1,2}, {2,3}, {3,0},   // near
                {4,5}, {5,6}, {6,7}, {7,4},   // far
                {0,4}, {1,5}, {2,6}, {3,7}    // sides
        };

        synchronized (lines) {
            for (int[] e : edges) {
                Vector3f a = points[e[0]], b = points[e[1]];
                if (a != null && b != null)
                    addLine(a.x, a.y, a.z, b.x, b.y, b.z, colorARGB, w);
            }
        }
    }

    @Override
    public void sphere(Vector3f center, float radius, int colorARGB, int segments) {
        if (center == null) return;
        int segs = resolveSegments(segments, radius);
        circle(center, radius, new Vector3f(1, 0, 0), colorARGB, segs);
        circle(center, radius, new Vector3f(0, 1, 0), colorARGB, segs);
        circle(center, radius, new Vector3f(0, 0, 1), colorARGB, segs);
    }

    @Override
    public void circle(Vector3f center, float radius, Vector3f normal, int colorARGB, int segments) {
        if (center == null || normal == null) return;

        Vector3f n = new Vector3f(normal);
        if (n.lengthSquared() < 1e-12f) return;
        n.normalize();

        Vector3f up  = Math.abs(n.y) < 0.99f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f axA = new Vector3f(n).cross(up).normalize();
        Vector3f axB = new Vector3f(n).cross(axA).normalize();

        int segs = resolveSegments(segments, radius);
        double step = 2.0 * Math.PI / segs;

        synchronized (lines) {
            for (int i = 0; i < segs; i++) {
                float a1 = (float) (i * step),       c1 = (float) Math.cos(a1) * radius, s1 = (float) Math.sin(a1) * radius;
                float a2 = (float) ((i + 1) * step), c2 = (float) Math.cos(a2) * radius, s2 = (float) Math.sin(a2) * radius;

                addLine(
                        center.x + axA.x*c1 + axB.x*s1,
                        center.y + axA.y*c1 + axB.y*s1,
                        center.z + axA.z*c1 + axB.z*s1,
                        center.x + axA.x*c2 + axB.x*s2,
                        center.y + axA.y*c2 + axB.y*s2,
                        center.z + axA.z*c2 + axB.z*s2,
                        colorARGB, DEFAULT_WIDTH
                );
            }
        }
    }

    @Override
    public void arc(Vector3f center, float radius, Vector3f normal, Vector3f startDir, float angleDeg, int colorARGB, int segments) {
        if (center == null || normal == null || startDir == null) return;

        Vector3f n    = new Vector3f(normal).normalize();
        Vector3f sd   = new Vector3f(startDir).normalize();
        Vector3f perp = new Vector3f(n).cross(sd).normalize();

        int segs = Math.max(4, segments);
        float totalRad = (float) Math.toRadians(angleDeg);

        synchronized (lines) {
            for (int i = 0; i < segs; i++) {
                float a1 = totalRad * i / segs,       c1 = (float) Math.cos(a1) * radius, s1 = (float) Math.sin(a1) * radius;
                float a2 = totalRad * (i + 1) / segs, c2 = (float) Math.cos(a2) * radius, s2 = (float) Math.sin(a2) * radius;

                addLine(
                        center.x + sd.x*c1 + perp.x*s1,
                        center.y + sd.y*c1 + perp.y*s1,
                        center.z + sd.z*c1 + perp.z*s1,
                        center.x + sd.x*c2 + perp.x*s2,
                        center.y + sd.y*c2 + perp.y*s2,
                        center.z + sd.z*c2 + perp.z*s2,
                        colorARGB, DEFAULT_WIDTH
                );
            }
        }
    }

    @Override
    public void clear() {
        synchronized (lines) {
            lines.clear();
        }
    }

    public void render(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Camera camera) {
        if (matrices == null || consumers == null || camera == null) return;

        List<Line> snapshot;
        synchronized (lines) {
            if (lines.isEmpty()) return;
            snapshot = new ArrayList<>(lines);
        }

        Vec3d camPos = camera.getPos();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        for (Line l : snapshot) {
            float r = ((l.color >> 16) & 0xFF) / 255f;
            float g = ((l.color >>  8) & 0xFF) / 255f;
            float b = ( l.color        & 0xFF) / 255f;
            float a = ((l.color >> 24) & 0xFF) / 255f;

            float dx = l.toX - l.fromX;
            float dy = l.toY - l.fromY;
            float dz = l.toZ - l.fromZ;
            float len = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);

            float nx, ny, nz;
            if (len > 1e-8f) { nx = dx/len; ny = dy/len; nz = dz/len; }
            else              { nx = 0;      ny = 1;      nz = 0;      }

            vc.vertex(mat, l.fromX, l.fromY, l.fromZ).color(r, g, b, a).normal(matrices.peek(), nx, ny, nz);
            vc.vertex(mat, l.toX,   l.toY,   l.toZ  ).color(r, g, b, a).normal(matrices.peek(), nx, ny, nz);
        }

        matrices.pop();
    }

    private void addLine(float x0, float y0, float z0, float x1, float y1, float z1, int color, float width) {
        lines.add(new Line(x0, y0, z0, x1, y1, z1, color, width));
    }

    private static float resolveWidth(float thickness) {
        return thickness > 0 ? thickness : DEFAULT_WIDTH;
    }

    private static int resolveSegments(int requested, float radius) {
        if (requested > 0) return Math.max(requested, 8);
        return Math.max(32, (int) (radius * 10));
    }

    private record Line(
            float fromX, float fromY, float fromZ,
            float toX,   float toY,   float toZ,
            int   color, float width
    ) {}
}