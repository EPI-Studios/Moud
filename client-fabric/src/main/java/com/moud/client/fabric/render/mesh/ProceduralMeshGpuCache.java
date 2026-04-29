package com.moud.client.fabric.render.mesh;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.MeshRegistry;
import com.moud.core.mesh.Surface;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProceduralMeshGpuCache {

    public record SurfaceRange(int firstIndex, int indexCount, String materialId) {}
    public record Handle(int vbo, int ebo, int indexCount, List<SurfaceRange> surfaces) {}

    private static final int DEFAULT_CAPACITY = 512;
    private static final int FLOATS_PER_VERTEX = 8;
    private static final Object LOCK = new Object();
    private static int capacity = DEFAULT_CAPACITY;
    private static final LinkedHashMap<String, Handle> BY_HASH =
            new LinkedHashMap<>(64, 0.75f, true);

    private ProceduralMeshGpuCache() {}

    public static void setCapacity(int cap) {
        synchronized (LOCK) {
            capacity = Math.max(1, cap);
        }
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ProceduralMeshGpuCache::evictOverflow);
            return;
        }
        evictOverflow();
    }

    private static void evictOverflow() {
        synchronized (LOCK) {
            evictOverflowLocked();
        }
    }

    public static Handle getOrUpload(String hash) {
        if (hash == null) return null;
        synchronized (LOCK) {
            Handle existing = BY_HASH.get(hash);
            if (existing != null) return existing;
        }
        if (!RenderSystem.isOnRenderThread()) return null;
        ArrayMesh mesh = MeshRegistry.instance().get(hash).orElse(null);
        if (mesh == null) return null;
        Handle uploaded = upload(mesh);
        if (uploaded != null) {
            synchronized (LOCK) {
                Handle previous = BY_HASH.put(hash, uploaded);
                if (previous != null && previous != uploaded) delete(previous);
                evictOverflowLocked();
            }
        }
        return uploaded;
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ProceduralMeshGpuCache::clear);
            return;
        }
        List<Handle> handles;
        synchronized (LOCK) {
            handles = new ArrayList<>(BY_HASH.values());
            BY_HASH.clear();
        }
        for (Handle handle : handles) {
            delete(handle);
        }
    }

    private static void evictOverflowLocked() {
        while (BY_HASH.size() > capacity) {
            var it = BY_HASH.entrySet().iterator();
            if (!it.hasNext()) return;
            Map.Entry<String, Handle> eldest = it.next();
            Handle handle = eldest.getValue();
            it.remove();
            delete(handle);
        }
    }

    private static void delete(Handle handle) {
        if (handle == null) return;
        if (handle.vbo() != 0) GL15.glDeleteBuffers(handle.vbo());
        if (handle.ebo() != 0) GL15.glDeleteBuffers(handle.ebo());
    }

    private static Handle upload(ArrayMesh mesh) {
        int totalVerts = 0;
        int totalIndices = 0;
        for (Surface s : mesh.surfaces()) {
            totalVerts += s.vertexCount();
            totalIndices += s.indices().length;
        }
        if (totalVerts == 0 || totalIndices == 0) return null;

        FloatBuffer verts = MemoryUtil.memAllocFloat(totalVerts * FLOATS_PER_VERTEX);
        IntBuffer indices = MemoryUtil.memAllocInt(totalIndices);
        List<SurfaceRange> ranges = new ArrayList<>(mesh.surfaces().size());
        try {
            int vertexBase = 0;
            int indexCursor = 0;
            for (Surface s : mesh.surfaces()) {
                float[] pos = s.positions();
                float[] uv = s.uvs();
                float[] nor = s.normals();
                int vCount = s.vertexCount();
                for (int i = 0; i < vCount; i++) {
                    verts.put(pos[i * 3]).put(pos[i * 3 + 1]).put(pos[i * 3 + 2]);
                    verts.put(uv[i * 2]).put(uv[i * 2 + 1]);
                    verts.put(nor[i * 3]).put(nor[i * 3 + 1]).put(nor[i * 3 + 2]);
                }
                int surfaceFirstIndex = indexCursor;
                for (int idx : s.indices()) {
                    indices.put(idx + vertexBase);
                    indexCursor++;
                }
                ranges.add(new SurfaceRange(surfaceFirstIndex, s.indices().length, s.materialId()));
                vertexBase += vCount;
            }
            verts.flip();
            indices.flip();

            int prevVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            int prevEbo = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);
            int ebo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVbo);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEbo);
            return new Handle(vbo, ebo, totalIndices, List.copyOf(ranges));
        } finally {
            MemoryUtil.memFree(verts);
            MemoryUtil.memFree(indices);
        }
    }
}
