package com.moud.server.minestom.physics.rapier.bake;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Pure greedy mesher: turns a 16x16x16 boolean solid mask into a triangle soup
 * of merged exterior faces, suitable for feeding into Rapier's trimesh shape.
 *
 * <p>Faces are emitted CCW when viewed from the air side (so the geometric
 * normal points away from the solid).</p>
 */
public final class GreedyMesher {

    public static final int SIZE = 16;
    private static final int AREA = SIZE * SIZE;
    private static final int VOLUME = SIZE * SIZE * SIZE;

    private GreedyMesher() {
    }

    /**
     * @param solid 16x16x16 mask, addressed as {@code solid[x*256 + y*16 + z]}.
     * @return triangle soup of merged quad faces, or {@link SectionTrimesh#empty()} if no faces.
     */
    public static SectionTrimesh mesh(boolean[] solid) {
        if (solid.length != VOLUME) {
            throw new IllegalArgumentException("Expected " + VOLUME + "-entry solid mask, got " + solid.length);
        }

        FloatArrayList verts = new FloatArrayList();
        IntArrayList indices = new IntArrayList();

        // Mask cell value: 0 = no face, 1 = face with +axis normal, 2 = face with -axis normal.
        byte[] mask = new byte[AREA];

        // u and v are the two axes orthogonal to the sweep axis a.
        for (int a = 0; a < 3; a++) {
            int u = (a + 1) % 3;
            int v = (a + 2) % 3;

            for (int k = 0; k <= SIZE; k++) {
                buildMask(solid, mask, a, u, v, k);
                emitMaskQuads(mask, verts, indices, a, u, v, k);
            }
        }

        if (indices.isEmpty()) return SectionTrimesh.empty();
        return new SectionTrimesh(verts.toFloatArray(), indices.toIntArray());
    }

    private static void buildMask(boolean[] solid, byte[] mask, int a, int u, int v, int k) {
        int[] coord = new int[3];
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                coord[u] = i;
                coord[v] = j;

                boolean below;
                if (k == 0) {
                    below = false;
                } else {
                    coord[a] = k - 1;
                    below = solid[index(coord[0], coord[1], coord[2])];
                }

                boolean above;
                if (k == SIZE) {
                    above = false;
                } else {
                    coord[a] = k;
                    above = solid[index(coord[0], coord[1], coord[2])];
                }

                if (below == above) {
                    mask[j * SIZE + i] = 0;
                } else if (below) {
                    // solid -> air: face has +a normal
                    mask[j * SIZE + i] = 1;
                } else {
                    // air -> solid: face has -a normal
                    mask[j * SIZE + i] = 2;
                }
            }
        }
    }

    private static void emitMaskQuads(byte[] mask, FloatArrayList verts, IntArrayList indices,
                                      int a, int u, int v, int k) {
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; ) {
                byte cell = mask[j * SIZE + i];
                if (cell == 0) {
                    i++;
                    continue;
                }

                // Run width along u.
                int w = 1;
                while (i + w < SIZE && mask[j * SIZE + (i + w)] == cell) w++;

                // Run height along v: extend while the entire row of width w matches.
                int h = 1;
                outer:
                while (j + h < SIZE) {
                    for (int x = 0; x < w; x++) {
                        if (mask[(j + h) * SIZE + (i + x)] != cell) break outer;
                    }
                    h++;
                }

                emitQuad(verts, indices, a, u, v, k, i, j, w, h, cell == 1);

                // Clear consumed cells.
                for (int dy = 0; dy < h; dy++) {
                    for (int dx = 0; dx < w; dx++) {
                        mask[(j + dy) * SIZE + (i + dx)] = 0;
                    }
                }

                i += w;
            }
        }
    }

    private static void emitQuad(FloatArrayList verts, IntArrayList indices,
                                 int a, int u, int v, int k,
                                 int i, int j, int w, int h, boolean positiveNormal) {
        // Compute the 4 corners of the quad in (a,u,v)-aligned space.
        // Corner ordering (CCW when viewed from +a side): (i,j), (i+w,j), (i+w,j+h), (i,j+h).
        float[] c0 = corner(a, u, v, k, i,     j);
        float[] c1 = corner(a, u, v, k, i + w, j);
        float[] c2 = corner(a, u, v, k, i + w, j + h);
        float[] c3 = corner(a, u, v, k, i,     j + h);

        int base = verts.size() / 3;
        if (positiveNormal) {
            // Normal points +a: viewed from +a side the order above is CCW.
            push(verts, c0); push(verts, c1); push(verts, c2); push(verts, c3);
        } else {
            // Normal points -a: flip winding so CCW is from the -a side.
            push(verts, c0); push(verts, c3); push(verts, c2); push(verts, c1);
        }

        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        indices.add(base);
        indices.add(base + 2);
        indices.add(base + 3);
    }

    private static float[] corner(int a, int u, int v, int k, int ui, int vi) {
        float[] p = new float[3];
        p[a] = k;
        p[u] = ui;
        p[v] = vi;
        return p;
    }

    private static void push(FloatArrayList verts, float[] p) {
        verts.add(p[0]);
        verts.add(p[1]);
        verts.add(p[2]);
    }

    private static int index(int x, int y, int z) {
        return x * AREA + y * SIZE + z;
    }
}
