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
    private final Object linesLock = new Object();
    // double-buffered, render() swaps lines/backBuffer so the live list walks outside the lock without copying
    private List<Line> lines = new ArrayList<>();
    private List<Line> backBuffer = new ArrayList<>();

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

        synchronized (linesLock) {
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

        synchronized (linesLock) {
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
        circleAxis(center.x, center.y, center.z, radius, 1, 0, 0, colorARGB, segs);
        circleAxis(center.x, center.y, center.z, radius, 0, 1, 0, colorARGB, segs);
        circleAxis(center.x, center.y, center.z, radius, 0, 0, 1, colorARGB, segs);
    }

    @Override
    public void circle(Vector3f center, float radius, Vector3f normal, int colorARGB, int segments) {
        if (center == null || normal == null) return;
        circleAxis(center.x, center.y, center.z, radius, normal.x, normal.y, normal.z,
                colorARGB, resolveSegments(segments, radius));
    }

    private void circleAxis(float cx, float cy, float cz, float radius,
                            float nxIn, float nyIn, float nzIn, int colorARGB, int segs) {
        float nLenSq = nxIn * nxIn + nyIn * nyIn + nzIn * nzIn;
        if (nLenSq < 1e-12f) return;
        float invN = 1f / (float) Math.sqrt(nLenSq);
        float nx = nxIn * invN, ny = nyIn * invN, nz = nzIn * invN;

        // Pick a non-parallel up vector, then build orthonormal basis (axA, axB).
        float upx, upy, upz;
        if (Math.abs(ny) < 0.99f) { upx = 0; upy = 1; upz = 0; }
        else                       { upx = 1; upy = 0; upz = 0; }

        float axAx = ny * upz - nz * upy;
        float axAy = nz * upx - nx * upz;
        float axAz = nx * upy - ny * upx;
        float axALenSq = axAx*axAx + axAy*axAy + axAz*axAz;
        if (axALenSq < 1e-12f) return;
        float invA = 1f / (float) Math.sqrt(axALenSq);
        axAx *= invA; axAy *= invA; axAz *= invA;

        float axBx = ny * axAz - nz * axAy;
        float axBy = nz * axAx - nx * axAz;
        float axBz = nx * axAy - ny * axAx;
        float axBLenSq = axBx*axBx + axBy*axBy + axBz*axBz;
        if (axBLenSq > 1e-12f) {
            float invB = 1f / (float) Math.sqrt(axBLenSq);
            axBx *= invB; axBy *= invB; axBz *= invB;
        }

        double step = 2.0 * Math.PI / segs;

        synchronized (linesLock) {
            for (int i = 0; i < segs; i++) {
                float a1 = (float) (i * step),       c1 = (float) Math.cos(a1) * radius, s1 = (float) Math.sin(a1) * radius;
                float a2 = (float) ((i + 1) * step), c2 = (float) Math.cos(a2) * radius, s2 = (float) Math.sin(a2) * radius;

                addLine(
                        cx + axAx*c1 + axBx*s1,
                        cy + axAy*c1 + axBy*s1,
                        cz + axAz*c1 + axBz*s1,
                        cx + axAx*c2 + axBx*s2,
                        cy + axAy*c2 + axBy*s2,
                        cz + axAz*c2 + axBz*s2,
                        colorARGB, DEFAULT_WIDTH
                );
            }
        }
    }

    @Override
    public void arc(Vector3f center, float radius, Vector3f normal, Vector3f startDir, float angleDeg, int colorARGB, int segments) {
        if (center == null || normal == null || startDir == null) return;

        float nLenSq = normal.x*normal.x + normal.y*normal.y + normal.z*normal.z;
        float sLenSq = startDir.x*startDir.x + startDir.y*startDir.y + startDir.z*startDir.z;
        if (nLenSq < 1e-12f || sLenSq < 1e-12f) return;
        float invN = 1f / (float) Math.sqrt(nLenSq);
        float invS = 1f / (float) Math.sqrt(sLenSq);
        float nx = normal.x * invN,  ny = normal.y * invN,  nz = normal.z * invN;
        float sdx = startDir.x * invS, sdy = startDir.y * invS, sdz = startDir.z * invS;

        float perpx = ny * sdz - nz * sdy;
        float perpy = nz * sdx - nx * sdz;
        float perpz = nx * sdy - ny * sdx;
        float pLenSq = perpx*perpx + perpy*perpy + perpz*perpz;
        if (pLenSq > 1e-12f) {
            float invP = 1f / (float) Math.sqrt(pLenSq);
            perpx *= invP; perpy *= invP; perpz *= invP;
        }

        int segs = Math.max(4, segments);
        float totalRad = (float) Math.toRadians(angleDeg);
        float cx = center.x, cy = center.y, cz = center.z;

        synchronized (linesLock) {
            for (int i = 0; i < segs; i++) {
                float a1 = totalRad * i / segs,       c1 = (float) Math.cos(a1) * radius, s1 = (float) Math.sin(a1) * radius;
                float a2 = totalRad * (i + 1) / segs, c2 = (float) Math.cos(a2) * radius, s2 = (float) Math.sin(a2) * radius;

                addLine(
                        cx + sdx*c1 + perpx*s1,
                        cy + sdy*c1 + perpy*s1,
                        cz + sdz*c1 + perpz*s1,
                        cx + sdx*c2 + perpx*s2,
                        cy + sdy*c2 + perpy*s2,
                        cz + sdz*c2 + perpz*s2,
                        colorARGB, DEFAULT_WIDTH
                );
            }
        }
    }

    @Override
    public void clear() {
        synchronized (linesLock) {
            lines.clear();
        }
    }

    public void render(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Camera camera) {
        if (matrices == null || consumers == null || camera == null) return;

        List<Line> snapshot;
        synchronized (linesLock) {
            if (lines.isEmpty()) return;
            snapshot = lines;
            lines = backBuffer;
            backBuffer = snapshot;
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

        synchronized (linesLock) {
            snapshot.clear();
        }
    }

    private void addLine(float x0, float y0, float z0, float x1, float y1, float z1, int color, float width) {
        synchronized (linesLock) {
            lines.add(new Line(x0, y0, z0, x1, y1, z1, color, width));
        }
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