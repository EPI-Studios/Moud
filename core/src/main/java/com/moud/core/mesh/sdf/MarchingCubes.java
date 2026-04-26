package com.moud.core.mesh.sdf;

import com.moud.core.mesh.MeshPrimitive;
import com.moud.core.mesh.Surface;
import com.moud.core.mesh.build.SurfaceTool;

public final class MarchingCubes {
    private MarchingCubes() {
    }

    public static Surface extract(ScalarField field,
                                   float iso,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   int nx, int ny, int nz,
                                   String materialId) {
        if (nx < 1 || ny < 1 || nz < 1) throw new IllegalArgumentException("resolution must be ≥ 1 on every axis");
        int cx = nx + 1, cy = ny + 1, cz = nz + 1;
        float dx = (x1 - x0) / nx;
        float dy = (y1 - y0) / ny;
        float dz = (z1 - z0) / nz;

        float[] samples = new float[cx * cy * cz];
        for (int k = 0; k < cz; k++) {
            for (int j = 0; j < cy; j++) {
                for (int i = 0; i < cx; i++) {
                    samples[i + cx * (j + cy * k)] = field.sample(x0 + i * dx, y0 + j * dy, z0 + k * dz);
                }
            }
        }

        SurfaceTool st = SurfaceTool.begin(MeshPrimitive.TRIANGLES);
        if (materialId != null) st.setMaterial(materialId);

        float[] corners = new float[8];
        float[][] cornerPos = new float[8][3];
        float[][] edgeVerts = new float[12][3];

        for (int k = 0; k < nz; k++) {
            for (int j = 0; j < ny; j++) {
                for (int i = 0; i < nx; i++) {
                    sampleCube(samples, cx, cy, i, j, k, corners);
                    cornerPositions(x0, y0, z0, dx, dy, dz, i, j, k, cornerPos);

                    int cubeIndex = 0;
                    for (int c = 0; c < 8; c++) if (corners[c] < iso) cubeIndex |= 1 << c;
                    int edgeMask = EDGE_TABLE[cubeIndex];
                    if (edgeMask == 0) continue;

                    for (int e = 0; e < 12; e++) {
                        if ((edgeMask & (1 << e)) == 0) continue;
                        int a = EDGE_CORNERS[e][0];
                        int b = EDGE_CORNERS[e][1];
                        interp(cornerPos[a], cornerPos[b], corners[a], corners[b], iso, edgeVerts[e]);
                    }

                    int[] tri = TRI_TABLE[cubeIndex];
                    for (int t = 0; t + 2 < tri.length && tri[t] >= 0; t += 3) {
                        float[] va = edgeVerts[tri[t]];
                        float[] vb = edgeVerts[tri[t + 1]];
                        float[] vc = edgeVerts[tri[t + 2]];
                        st.addTriangleVerts(va[0], va[1], va[2], vb[0], vb[1], vb[2], vc[0], vc[1], vc[2]);
                    }
                }
            }
        }
        st.generateFlatNormals();
        return st.end();
    }

    private static void sampleCube(float[] samples, int cx, int cy, int i, int j, int k, float[] out) {
        out[0] = samples[(i)     + cx * ((j)     + cy * (k))];
        out[1] = samples[(i + 1) + cx * ((j)     + cy * (k))];
        out[2] = samples[(i + 1) + cx * ((j + 1) + cy * (k))];
        out[3] = samples[(i)     + cx * ((j + 1) + cy * (k))];
        out[4] = samples[(i)     + cx * ((j)     + cy * (k + 1))];
        out[5] = samples[(i + 1) + cx * ((j)     + cy * (k + 1))];
        out[6] = samples[(i + 1) + cx * ((j + 1) + cy * (k + 1))];
        out[7] = samples[(i)     + cx * ((j + 1) + cy * (k + 1))];
    }

    private static void cornerPositions(float x0, float y0, float z0, float dx, float dy, float dz,
                                         int i, int j, int k, float[][] out) {
        float bx = x0 + i * dx, by = y0 + j * dy, bz = z0 + k * dz;
        float tx = bx + dx, ty = by + dy, tz = bz + dz;
        out[0][0] = bx; out[0][1] = by; out[0][2] = bz;
        out[1][0] = tx; out[1][1] = by; out[1][2] = bz;
        out[2][0] = tx; out[2][1] = ty; out[2][2] = bz;
        out[3][0] = bx; out[3][1] = ty; out[3][2] = bz;
        out[4][0] = bx; out[4][1] = by; out[4][2] = tz;
        out[5][0] = tx; out[5][1] = by; out[5][2] = tz;
        out[6][0] = tx; out[6][1] = ty; out[6][2] = tz;
        out[7][0] = bx; out[7][1] = ty; out[7][2] = tz;
    }

    private static void interp(float[] a, float[] b, float va, float vb, float iso, float[] out) {
        float denom = vb - va;
        float t = Math.abs(denom) < 1e-6f ? 0.5f : (iso - va) / denom;
        if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
        out[0] = a[0] + t * (b[0] - a[0]);
        out[1] = a[1] + t * (b[1] - a[1]);
        out[2] = a[2] + t * (b[2] - a[2]);
    }

    private static final int[][] EDGE_CORNERS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private static final int[] EDGE_TABLE = MarchingCubesTables.EDGE_TABLE;
    private static final int[][] TRI_TABLE = MarchingCubesTables.TRI_TABLE;
}