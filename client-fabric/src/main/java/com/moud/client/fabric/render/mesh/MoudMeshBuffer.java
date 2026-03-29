package com.moud.client.fabric.render.mesh;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class MoudMeshBuffer {

    private static final int FLOATS_PER_VERTEX = 8;
    private static final int STRIDE = FLOATS_PER_VERTEX * Float.BYTES;

    private static final int PLANE_RES = 64;
    private static final int SPHERE_RINGS = 32;
    private static final int SPHERE_SECTORS = 32;

    private static final int CUBE_INDEX_COUNT = 36;
    private static final int CROSS_INDEX_COUNT = 12;

    private static int vbo;
    private static int ebo;
    private static boolean initialized;

    private static int planeVbo;
    private static int planeEbo;
    private static int planeIndexCount;
    private static boolean planeInitialized;

    private static int sphereVbo;
    private static int sphereEbo;
    private static int sphereIndexCount;
    private static boolean sphereInitialized;

    private static int crossVbo;
    private static int crossEbo;
    private static boolean crossInitialized;

    private MoudMeshBuffer() {}

    public static void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        FloatBuffer verts = MemoryUtil.memAllocFloat(24 * FLOATS_PER_VERTEX);
        IntBuffer indices = MemoryUtil.memAllocInt(CUBE_INDEX_COUNT);

        try {
            buildCubeVertices(verts);
            buildCubeIndices(indices);
            verts.flip();
            indices.flip();

            int[] handles = upload(verts, indices);
            vbo = handles[0];
            ebo = handles[1];
        } finally {
            MemoryUtil.memFree(verts);
            MemoryUtil.memFree(indices);
        }
    }

    public static void ensurePlaneInitialized() {
        if (planeInitialized) return;
        planeInitialized = true;

        int vertsPerSide = PLANE_RES + 1;
        int vertCount = vertsPerSide * vertsPerSide;
        planeIndexCount = PLANE_RES * PLANE_RES * 6;

        FloatBuffer verts = MemoryUtil.memAllocFloat(vertCount * FLOATS_PER_VERTEX);
        IntBuffer indices = MemoryUtil.memAllocInt(planeIndexCount);

        try {
            for (int z = 0; z <= PLANE_RES; z++) {
                for (int x = 0; x <= PLANE_RES; x++) {
                    float fx = x / (float) PLANE_RES;
                    float fz = z / (float) PLANE_RES;
                    v(verts, fx, 0, fz, fx, fz, 0, 1, 0);
                }
            }

            for (int z = 0; z < PLANE_RES; z++) {
                for (int x = 0; x < PLANE_RES; x++) {
                    int tl = z * vertsPerSide + x;
                    int tr = tl + 1;
                    int bl = tl + vertsPerSide;
                    int br = bl + 1;
                    indices.put(tl).put(bl).put(tr);
                    indices.put(tr).put(bl).put(br);
                }
            }

            verts.flip();
            indices.flip();

            int[] handles = upload(verts, indices);
            planeVbo = handles[0];
            planeEbo = handles[1];
        } finally {
            MemoryUtil.memFree(verts);
            MemoryUtil.memFree(indices);
        }
    }

    public static void ensureSphereInitialized() {
        if (sphereInitialized) return;
        sphereInitialized = true;

        int vertCount = (SPHERE_RINGS + 1) * (SPHERE_SECTORS + 1);
        sphereIndexCount = SPHERE_RINGS * SPHERE_SECTORS * 6;

        FloatBuffer verts = MemoryUtil.memAllocFloat(vertCount * FLOATS_PER_VERTEX);
        IntBuffer indices = MemoryUtil.memAllocInt(sphereIndexCount);

        try {
            float ringStep = 1f / SPHERE_RINGS;
            float sectorStep = 1f / SPHERE_SECTORS;

            for (int r = 0; r <= SPHERE_RINGS; r++) {
                for (int s = 0; s <= SPHERE_SECTORS; s++) {
                    float y = (float) Math.sin(-Math.PI / 2 + Math.PI * r * ringStep);
                    float x = (float) (Math.cos(2 * Math.PI * s * sectorStep) * Math.sin(Math.PI * r * ringStep));
                    float z = (float) (Math.sin(2 * Math.PI * s * sectorStep) * Math.sin(Math.PI * r * ringStep));
                    v(verts, x * 0.5f + 0.5f, y * 0.5f + 0.5f, z * 0.5f + 0.5f, s * sectorStep, r * ringStep, x, y, z);
                }
            }

            for (int r = 0; r < SPHERE_RINGS; r++) {
                for (int s = 0; s < SPHERE_SECTORS; s++) {
                    int a = r * (SPHERE_SECTORS + 1) + s;
                    int b = a + SPHERE_SECTORS + 1;
                    indices.put(a).put(b).put(a + 1);
                    indices.put(a + 1).put(b).put(b + 1);
                }
            }

            verts.flip();
            indices.flip();

            int[] handles = upload(verts, indices);
            sphereVbo = handles[0];
            sphereEbo = handles[1];
        } finally {
            MemoryUtil.memFree(verts);
            MemoryUtil.memFree(indices);
        }
    }

    // two quads forming a cross, y=0 at base, y=1 at tip, uv.y used for wind gradient
    public static void ensureCrossInitialized() {
        if (crossInitialized) return;
        crossInitialized = true;

        FloatBuffer verts = MemoryUtil.memAllocFloat(8 * FLOATS_PER_VERTEX);
        IntBuffer indices = MemoryUtil.memAllocInt(CROSS_INDEX_COUNT);

        try {
            v(verts, -0.5f, 0f, 0f, 0f, 0f, 0f, 0f, 1f);
            v(verts,  0.5f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
            v(verts, -0.5f, 1f, 0f, 0f, 1f, 0f, 0f, 1f);
            v(verts,  0.5f, 1f, 0f, 1f, 1f, 0f, 0f, 1f);

            v(verts, 0f, 0f, -0.5f, 0f, 0f, 1f, 0f, 0f);
            v(verts, 0f, 0f,  0.5f, 1f, 0f, 1f, 0f, 0f);
            v(verts, 0f, 1f, -0.5f, 0f, 1f, 1f, 0f, 0f);
            v(verts, 0f, 1f,  0.5f, 1f, 1f, 1f, 0f, 0f);

            indices.put(new int[]{ 0,2,1, 1,2,3, 4,6,5, 5,6,7 });

            verts.flip();
            indices.flip();

            int[] handles = upload(verts, indices);
            crossVbo = handles[0];
            crossEbo = handles[1];
        } finally {
            MemoryUtil.memFree(verts);
            MemoryUtil.memFree(indices);
        }
    }

    public static void cleanup() {
        if (initialized) {
            deleteBuffers(vbo, ebo);
            vbo = 0; ebo = 0;
            initialized = false;
        }
        if (planeInitialized) {
            deleteBuffers(planeVbo, planeEbo);
            planeVbo = 0; planeEbo = 0;
            planeInitialized = false;
        }
        if (sphereInitialized) {
            deleteBuffers(sphereVbo, sphereEbo);
            sphereVbo = 0; sphereEbo = 0;
            sphereInitialized = false;
        }
        if (crossInitialized) {
            deleteBuffers(crossVbo, crossEbo);
            crossVbo = 0; crossEbo = 0;
            crossInitialized = false;
        }
    }

    public static int vbo() { return vbo; }
    public static int ebo() { return ebo; }
    public static int indexCount() { return CUBE_INDEX_COUNT; }
    public static int planeVbo() { return planeVbo; }
    public static int planeEbo() { return planeEbo; }
    public static int planeIndexCount() { return planeIndexCount; }
    public static int sphereVbo() { return sphereVbo; }
    public static int sphereEbo() { return sphereEbo; }
    public static int sphereIndexCount() { return sphereIndexCount; }
    public static int crossVbo() { return crossVbo; }
    public static int crossEbo() { return crossEbo; }
    public static int crossIndexCount() { return CROSS_INDEX_COUNT; }

    private static int[] upload(FloatBuffer verts, IntBuffer indices) {
        int prevVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevEbo = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        int genVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, genVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);

        int genEbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, genEbo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVbo);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEbo);

        return new int[]{ genVbo, genEbo };
    }

    private static void deleteBuffers(int targetVbo, int targetEbo) {
        GL15.glDeleteBuffers(targetVbo);
        GL15.glDeleteBuffers(targetEbo);
    }

    private static void buildCubeVertices(FloatBuffer buf) {
        v(buf,  0,  0,  0,  0,  1,  0,  0, -1);
        v(buf,  0,  1,  0,  0,  0,  0,  0, -1);
        v(buf,  1,  1,  0,  1,  0,  0,  0, -1);
        v(buf,  1,  0,  0,  1,  1,  0,  0, -1);

        v(buf,  0,  0,  1,  1,  1,  0,  0,  1);
        v(buf,  1,  0,  1,  0,  1,  0,  0,  1);
        v(buf,  1,  1,  1,  0,  0,  0,  0,  1);
        v(buf,  0,  1,  1,  1,  0,  0,  0,  1);

        v(buf,  0,  0,  0,  1,  1, -1,  0,  0);
        v(buf,  0,  0,  1,  0,  1, -1,  0,  0);
        v(buf,  0,  1,  1,  0,  0, -1,  0,  0);
        v(buf,  0,  1,  0,  1,  0, -1,  0,  0);

        v(buf,  1,  0,  0,  0,  1,  1,  0,  0);
        v(buf,  1,  1,  0,  0,  0,  1,  0,  0);
        v(buf,  1,  1,  1,  1,  0,  1,  0,  0);
        v(buf,  1,  0,  1,  1,  1,  1,  0,  0);

        v(buf,  0,  0,  0,  0,  0,  0, -1,  0);
        v(buf,  1,  0,  0,  1,  0,  0, -1,  0);
        v(buf,  1,  0,  1,  1,  1,  0, -1,  0);
        v(buf,  0,  0,  1,  0,  1,  0, -1,  0);

        v(buf,  0,  1,  0,  0,  1,  0,  1,  0);
        v(buf,  0,  1,  1,  0,  0,  0,  1,  0);
        v(buf,  1,  1,  1,  1,  0,  0,  1,  0);
        v(buf,  1,  1,  0,  1,  1,  0,  1,  0);
    }

    private static void buildCubeIndices(IntBuffer buf) {
        for (int face = 0; face < 6; face++) {
            int base = face * 4;
            buf.put(base).put(base + 1).put(base + 2);
            buf.put(base).put(base + 2).put(base + 3);
        }
    }

    private static void v(FloatBuffer buf, float px, float py, float pz, float u, float v, float nx, float ny, float nz) {
        buf.put(px).put(py).put(pz).put(u).put(v).put(nx).put(ny).put(nz);
    }
}