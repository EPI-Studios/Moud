package com.moud.client.fabric.render.veil;

import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

public final class GlUtil {

    private static final int STRIDE = 8 * Float.BYTES;

    private GlUtil() {}

    public static int currentProgram() {
        return GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    }

    public static void uniformMat4(int program, String name, Matrix4f mat) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc < 0) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            mat.get(buf);
            GL20.glUniformMatrix4fv(loc, false, buf);
        }
    }

    public static void uniform4f(int program, String name, float x, float y, float z, float w) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc >= 0) GL20.glUniform4f(loc, x, y, z, w);
    }

    public static void uniform3f(int program, String name, float x, float y, float z) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc >= 0) GL20.glUniform3f(loc, x, y, z);
    }

    public static void uniform1f(int program, String name, float v) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc >= 0) GL20.glUniform1f(loc, v);
    }

    public static void uniform2f(int program, String name, float x, float y) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc >= 0) GL20.glUniform2f(loc, x, y);
    }

    public static void uniform1i(int program, String name, int v) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc >= 0) GL20.glUniform1i(loc, v);
    }

    public static int createMeshVao(int program, int vbo, int ebo) {
        int locPos  = GL20.glGetAttribLocation(program, "aPos");
        int locTex  = GL20.glGetAttribLocation(program, "aTexCoord");
        int locNorm = GL20.glGetAttribLocation(program, "aNormal");

        int prevArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);

        bindMeshAttribs(locPos, locTex, locNorm);

        GL30.glBindVertexArray(prevVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevArrayBuffer);
        return vao;
    }

    public static int createInstancedMeshVao(int program, int meshVbo, int meshEbo, int instanceVbo) {
        int locPos  = GL20.glGetAttribLocation(program, "aPos");
        int locTex  = GL20.glGetAttribLocation(program, "aTexCoord");
        int locNorm = GL20.glGetAttribLocation(program, "aNormal");

        int prevArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, meshVbo);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, meshEbo);

        bindMeshAttribs(locPos, locTex, locNorm);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        int instanceStride = 20 * Float.BYTES;

        bindInstancedMat4(program, "aWorldMat0", instanceStride, 0);

        int locTint = GL20.glGetAttribLocation(program, "aTint");
        if (locTint >= 0) {
            GL20.glEnableVertexAttribArray(locTint);
            GL20.glVertexAttribPointer(locTint, 4, GL11.GL_FLOAT, false, instanceStride, 16 * Float.BYTES);
            GL33.glVertexAttribDivisor(locTint, 1);
        }

        GL30.glBindVertexArray(prevVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevArrayBuffer);
        return vao;
    }

    public static void drawElements(int vao, int indexCount) {
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0);
        GL30.glBindVertexArray(prevVao);
    }

    public static void drawElementsInstanced(int vao, int indexCount, int instanceCount) {
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0, instanceCount);
        GL30.glBindVertexArray(prevVao);
    }

    public static void deleteVao(int vao) {
        GL30.glDeleteVertexArrays(vao);
    }

    private static void bindMeshAttribs(int locPos, int locTex, int locNorm) {
        if (locPos >= 0) {
            GL20.glEnableVertexAttribArray(locPos);
            GL20.glVertexAttribPointer(locPos, 3, GL11.GL_FLOAT, false, STRIDE, 0);
        }
        if (locTex >= 0) {
            GL20.glEnableVertexAttribArray(locTex);
            GL20.glVertexAttribPointer(locTex, 2, GL11.GL_FLOAT, false, STRIDE, 3 * Float.BYTES);
        }
        if (locNorm >= 0) {
            GL20.glEnableVertexAttribArray(locNorm);
            GL20.glVertexAttribPointer(locNorm, 3, GL11.GL_FLOAT, false, STRIDE, 5 * Float.BYTES);
        }
    }

    private static void bindInstancedMat4(int program, String attrib, int stride, int floatOffset) {
        String prefix = attrib.substring(0, attrib.length() - 1);
        for (int col = 0; col < 4; col++) {
            int loc = GL20.glGetAttribLocation(program, prefix + col);
            if (loc < 0) continue;
            GL20.glEnableVertexAttribArray(loc);
            GL20.glVertexAttribPointer(loc, 4, GL11.GL_FLOAT, false, stride, (floatOffset + col * 4) * Float.BYTES);
            GL33.glVertexAttribDivisor(loc, 1);
        }
    }
}
