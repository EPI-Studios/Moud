package com.moud.client.rendering;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class MeshBuffer {
    private final VertexBuffer vertexBuffer;
    private final int vertexCount;
    private final VertexFormat.DrawMode drawMode;
    private boolean uploaded = false;

    public MeshBuffer(float[] vertices, int[] indices) {
        this.vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.drawMode = VertexFormat.DrawMode.TRIANGLES;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.begin(drawMode, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);

        // vertices format: [x, y, z, u, v, nx, ny, nz] (8 floats per vertex)
        // VertexFormats.POSITION_TEXTURE_COLOR_NORMAL expects: position(3), texture(2), color(4), normal(3)

        for (int i = 0; i < indices.length; i++) {
            int vertexIndex = indices[i] * 8; // 8 floats per vertex

            float x = vertices[vertexIndex];
            float y = vertices[vertexIndex + 1];
            float z = vertices[vertexIndex + 2];
            float u = vertices[vertexIndex + 3];
            float v = vertices[vertexIndex + 4];
            float nx = vertices[vertexIndex + 5];
            float ny = vertices[vertexIndex + 6];
            float nz = vertices[vertexIndex + 7];

            builder.vertex(x, y, z)
                   .texture(u, v)
                   .color(255, 255, 255, 255)
                   .normal(nx, ny, nz);
        }

        BuiltBuffer builtBuffer = builder.endNullable();
        if (builtBuffer != null) {
            this.vertexBuffer.bind();
            this.vertexBuffer.upload(builtBuffer);
            VertexBuffer.unbind();
            builtBuffer.close();
        }

        this.vertexCount = indices.length;
        this.uploaded = true;
    }

    public VertexBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public VertexFormat.DrawMode getDrawMode() {
        return drawMode;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void close() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
        }
        uploaded = false;
    }
}
