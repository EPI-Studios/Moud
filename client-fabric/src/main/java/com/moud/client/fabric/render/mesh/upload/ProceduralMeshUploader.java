package com.moud.client.fabric.render.mesh.upload;

import com.moud.client.fabric.render.mesh.cache.ClientMeshCache;
import com.moud.client.fabric.render.mesh.cache.GpuMesh;
import com.moud.client.fabric.render.mesh.cache.GpuSurface;
import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.Surface;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ProceduralMeshUploader {
    private static final int FLOATS_PER_VERTEX = 8;

    private static final ConcurrentLinkedQueue<ArrayMesh> QUEUE = new ConcurrentLinkedQueue<>();

    private ProceduralMeshUploader() {
    }

    public static void enqueue(ArrayMesh mesh) {
        if (mesh == null || ClientMeshCache.contains(mesh.hash())) {
            return;
        }
        QUEUE.add(mesh);
    }

    public static void drain() {
        ArrayMesh mesh;
        while ((mesh = QUEUE.poll()) != null) {
            if (ClientMeshCache.contains(mesh.hash())) {
                continue;
            }
            ClientMeshCache.put(uploadOnGlThread(mesh));
        }
        ClientMeshCache.flushPendingDeletes();
    }

    private static GpuMesh uploadOnGlThread(ArrayMesh mesh) {
        int prevVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevEbo = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        List<GpuSurface> surfaces = new ArrayList<>(mesh.surfaces().size());
        for (Surface surface : mesh.surfaces()) {
            surfaces.add(uploadSurface(surface));
        }

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVbo);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEbo);

        return new GpuMesh(mesh.hash(), surfaces, mesh.boundsMin(), mesh.boundsMax());
    }

    private static GpuSurface uploadSurface(Surface surface) {
        int vertexCount = surface.vertexCount();
        FloatBuffer verts = MemoryUtil.memAllocFloat(vertexCount * FLOATS_PER_VERTEX);
        IntBuffer indices = MemoryUtil.memAllocInt(surface.indices().length);
        try {
            float[] pos = surface.positions();
            float[] nrm = surface.normals();
            float[] uv = surface.uvs();
            for (int v = 0; v < vertexCount; v++) {
                verts.put(pos[v * 3])
                     .put(pos[v * 3 + 1])
                     .put(pos[v * 3 + 2])
                     .put(uv[v * 2])
                     .put(uv[v * 2 + 1])
                     .put(nrm[v * 3])
                     .put(nrm[v * 3 + 1])
                     .put(nrm[v * 3 + 2]);
            }
            for (int i : surface.indices()) {
                indices.put(i);
            }
            verts.flip();
            indices.flip();

            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);

            int ebo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);

            return new GpuSurface(vbo, ebo, surface.indices().length, surface.materialId());
        } finally {
            MemoryUtil.memFree(verts);
            MemoryUtil.memFree(indices);
        }
    }

    public static void clear() {
        QUEUE.clear();
    }
}
