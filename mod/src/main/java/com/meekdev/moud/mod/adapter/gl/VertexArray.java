package com.meekdev.moud.mod.adapter.gl;

import com.meekdev.amnetic.client.instanced.MeshData;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

public final class VertexArray {

    private final int vao;
    private final int count;
    private final boolean indexed;

    private VertexArray(int vao, int count, boolean indexed) {
        this.vao = vao;
        this.count = count;
        this.indexed = indexed;
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
        return new VertexArray(vao, indexed ? mesh.indexCount() : mesh.vertexCount(), indexed);
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

    private static void attribute(int index, int size, int stride, int offset) {
        GL20.glVertexAttribPointer(index, size, GL11.GL_FLOAT, false, stride, offset);
        GL20.glEnableVertexAttribArray(index);
    }
}
