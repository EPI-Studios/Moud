package com.meekdev.moud.mod.adapter.gl;

import com.meekdev.amnetic.client.instanced.MeshData;
import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

public final class VertexArray {

    private final int vao;
    private final int vbo;
    private final int count;
    private final boolean indexed;

    private VertexArray(int vao, int vbo, int count, boolean indexed) {
        this.vao = vao;
        this.vbo = vbo;
        this.count = count;
        this.indexed = indexed;
    }

    public static VertexArray interleaved(int... sizes) {
        int vao = GlStateManager._glGenVertexArrays();
        int vbo = GlStateManager._glGenBuffers();
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        int floats = 0;
        for (int size : sizes) floats += size;
        int offset = 0;
        for (int index = 0; index < sizes.length; index++) {
            attribute(index, sizes[index], floats * Float.BYTES, offset * Float.BYTES);
            offset += sizes[index];
        }
        unbind();
        return new VertexArray(vao, vbo, 0, false);
    }

    public static VertexArray of(MeshData mesh) {
        int vao = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(vao);
        int vbo = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.verticesAsBuffer(), GL15.GL_STATIC_DRAW);
        attribute(0, 3, mesh.vertexStrideBytes(), 0);
        boolean indexed = mesh.hasIndices();
        if (indexed) {
            int ibo = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, mesh.indicesAsBuffer(), GL15.GL_STATIC_DRAW);
        }
        unbindBuffer();
        unbind();
        return new VertexArray(vao, vbo, indexed ? mesh.indexCount() : mesh.vertexCount(), indexed);
    }

    public void stream(FloatBuffer vertices) {
        bind();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW);
    }

    public void bind() {
        GlStateManager._glBindVertexArray(vao);
    }

    public static void unbind() {
        GlStateManager._glBindVertexArray(0);
    }

    public static void unbindBuffer() {
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void draw() {
        if (indexed) GL11.glDrawElements(GL11.GL_TRIANGLES, count, GL11.GL_UNSIGNED_INT, 0L);
        else GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
    }

    public void draw(int first, int vertices) {
        GL11.glDrawArrays(GL11.GL_TRIANGLES, first, vertices);
    }

    private static void attribute(int index, int size, int stride, int offset) {
        GL20.glVertexAttribPointer(index, size, GL11.GL_FLOAT, false, stride, offset);
        GL20.glEnableVertexAttribArray(index);
    }
}
