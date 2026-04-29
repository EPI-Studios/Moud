package com.moud.core.mesh.build;

import com.moud.core.mesh.MeshPrimitive;
import com.moud.core.mesh.Surface;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SurfaceTool {
    private final MeshPrimitive primitive;
    private final List<Float> positions = new ArrayList<>();
    private final List<Float> normals = new ArrayList<>();
    private final List<Float> uvs = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();

    private String materialId = "";
    private float normalX = 0f;
    private float normalY = 1f;
    private float normalZ = 0f;
    private float uvU = 0f;
    private float uvV = 0f;
    private int color = 0xFFFFFFFF;
    private boolean hasColors;

    private SurfaceTool(MeshPrimitive primitive) {
        this.primitive = Objects.requireNonNull(primitive, "primitive");
    }

    public static SurfaceTool begin(MeshPrimitive primitive) {
        return new SurfaceTool(primitive);
    }

    public SurfaceTool setMaterial(String id) {
        materialId = id == null ? "" : id;
        return this;
    }

    public SurfaceTool setNormal(float x, float y, float z) {
        normalX = x;
        normalY = y;
        normalZ = z;
        return this;
    }

    public SurfaceTool setUv(float u, float v) {
        uvU = u;
        uvV = v;
        return this;
    }

    public SurfaceTool setColor(float r, float g, float b, float a) {
        color = packColor(r, g, b, a);
        hasColors = true;
        return this;
    }

    public int addVertex(float x, float y, float z) {
        int index = vertexCount();
        positions.add(x);
        positions.add(y);
        positions.add(z);
        normals.add(normalX);
        normals.add(normalY);
        normals.add(normalZ);
        uvs.add(uvU);
        uvs.add(uvV);
        colors.add(color);
        return index;
    }

    public SurfaceTool addTriangle(int a, int b, int c) {
        validateIndex(a);
        validateIndex(b);
        validateIndex(c);
        indices.add(a);
        indices.add(b);
        indices.add(c);
        return this;
    }

    public SurfaceTool addTriangleVerts(float ax, float ay, float az,
                                        float bx, float by, float bz,
                                        float cx, float cy, float cz) {
        int a = addVertex(ax, ay, az);
        int b = addVertex(bx, by, bz);
        int c = addVertex(cx, cy, cz);
        return addTriangle(a, b, c);
    }

    public SurfaceTool addVertexArrays(float[] positions, float[] uvs, int[] colors) {
        if (positions == null || positions.length % 3 != 0) {
            throw new IllegalArgumentException("positions length must be divisible by 3");
        }
        int count = positions.length / 3;
        if (uvs != null && uvs.length < count * 2) {
            throw new IllegalArgumentException("uv array is shorter than vertex count");
        }
        if (colors != null && colors.length < count) {
            throw new IllegalArgumentException("color array is shorter than vertex count");
        }
        for (int i = 0; i < count; i++) {
            if (uvs != null) {
                setUv(uvs[i * 2], uvs[i * 2 + 1]);
            }
            if (colors != null) {
                color = colors[i];
                hasColors = true;
            }
            addVertex(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
        }
        return this;
    }

    public SurfaceTool addTriangleArray(int[] indices) {
        if (indices == null || indices.length % 3 != 0) {
            throw new IllegalArgumentException("triangle index array length must be divisible by 3");
        }
        for (int i = 0; i < indices.length; i += 3) {
            addTriangle(indices[i], indices[i + 1], indices[i + 2]);
        }
        return this;
    }

    public SurfaceTool generateFlatNormals() {
        for (int i = 0; i < normals.size(); i++) {
            normals.set(i, 0f);
        }
        for (int i = 0; i + 2 < indices.size(); i += 3) {
            int a = indices.get(i);
            int b = indices.get(i + 1);
            int c = indices.get(i + 2);
            float ax = positions.get(a * 3);
            float ay = positions.get(a * 3 + 1);
            float az = positions.get(a * 3 + 2);
            float bx = positions.get(b * 3);
            float by = positions.get(b * 3 + 1);
            float bz = positions.get(b * 3 + 2);
            float cx = positions.get(c * 3);
            float cy = positions.get(c * 3 + 1);
            float cz = positions.get(c * 3 + 2);
            float ux = bx - ax;
            float uy = by - ay;
            float uz = bz - az;
            float vx = cx - ax;
            float vy = cy - ay;
            float vz = cz - az;
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-6f) {
                nx /= len;
                ny /= len;
                nz /= len;
            } else {
                nx = 0f;
                ny = 1f;
                nz = 0f;
            }
            setVertexNormal(a, nx, ny, nz);
            setVertexNormal(b, nx, ny, nz);
            setVertexNormal(c, nx, ny, nz);
        }
        return this;
    }

    public SurfaceTool generateSmoothNormals(float angleDeg) {
        for (int i = 0; i < normals.size(); i++) {
            normals.set(i, 0f);
        }
        for (int i = 0; i + 2 < indices.size(); i += 3) {
            int a = indices.get(i);
            int b = indices.get(i + 1);
            int c = indices.get(i + 2);
            float[] n = triangleNormal(a, b, c);
            addVertexNormal(a, n[0], n[1], n[2]);
            addVertexNormal(b, n[0], n[1], n[2]);
            addVertexNormal(c, n[0], n[1], n[2]);
        }
        normalizeNormals();
        return this;
    }

    public Surface end() {
        if (primitive == MeshPrimitive.TRIANGLES && indices.size() % 3 != 0) {
            throw new IllegalStateException("triangle indices must be grouped by 3");
        }
        return new Surface(
                toFloatArray(positions),
                toFloatArray(normals),
                toFloatArray(uvs),
                hasColors ? toIntArray(colors) : null,
                toIntArray(indices),
                materialId,
                primitive);
    }

    private int vertexCount() {
        return positions.size() / 3;
    }

    private void validateIndex(int index) {
        if (index < 0 || index >= vertexCount()) {
            throw new IndexOutOfBoundsException("vertex index out of range: " + index);
        }
    }

    private void setVertexNormal(int index, float x, float y, float z) {
        normals.set(index * 3, x);
        normals.set(index * 3 + 1, y);
        normals.set(index * 3 + 2, z);
    }

    private void addVertexNormal(int index, float x, float y, float z) {
        normals.set(index * 3, normals.get(index * 3) + x);
        normals.set(index * 3 + 1, normals.get(index * 3 + 1) + y);
        normals.set(index * 3 + 2, normals.get(index * 3 + 2) + z);
    }

    private float[] triangleNormal(int a, int b, int c) {
        float ax = positions.get(a * 3);
        float ay = positions.get(a * 3 + 1);
        float az = positions.get(a * 3 + 2);
        float bx = positions.get(b * 3);
        float by = positions.get(b * 3 + 1);
        float bz = positions.get(b * 3 + 2);
        float cx = positions.get(c * 3);
        float cy = positions.get(c * 3 + 1);
        float cz = positions.get(c * 3 + 2);
        float ux = bx - ax;
        float uy = by - ay;
        float uz = bz - az;
        float vx = cx - ax;
        float vy = cy - ay;
        float vz = cz - az;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len <= 1e-6f) {
            return new float[]{0f, 1f, 0f};
        }
        return new float[]{nx / len, ny / len, nz / len};
    }

    private void normalizeNormals() {
        for (int i = 0; i + 2 < normals.size(); i += 3) {
            float x = normals.get(i);
            float y = normals.get(i + 1);
            float z = normals.get(i + 2);
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len > 1e-6f) {
                normals.set(i, x / len);
                normals.set(i + 1, y / len);
                normals.set(i + 2, z / len);
            } else {
                normals.set(i, 0f);
                normals.set(i + 1, 1f);
                normals.set(i + 2, 0f);
            }
        }
    }

    private static int packColor(float r, float g, float b, float a) {
        int ri = clampColor(r);
        int gi = clampColor(g);
        int bi = clampColor(b);
        int ai = clampColor(a);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    private static int clampColor(float value) {
        int scaled = Math.round(value * 255f);
        return Math.max(0, Math.min(255, scaled));
    }

    private static float[] toFloatArray(List<Float> values) {
        float[] out = new float[values.size()];
        for (int i = 0; i < values.size(); i++) out[i] = values.get(i);
        return out;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) out[i] = values.get(i);
        return out;
    }
}
