package com.moud.client.model;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.util.OBJLoader;
import foundry.veil.api.client.render.vertex.VertexArray;
import foundry.veil.api.client.render.vertex.VertexArrayBuilder;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;

public class RenderableModel {
    private final long id;
    private final String modelPath;
    private VertexArray vertexArray;

    private Vector3 position = Vector3.zero();
    private Vector3 prevPosition = Vector3.zero();
    private Quaternion rotation = Quaternion.identity();
    private Quaternion prevRotation = Quaternion.identity();
    private Vector3 scale = Vector3.one();

    public RenderableModel(long id, String modelPath) {
        this.id = id;
        this.modelPath = modelPath;
    }

    public void uploadMesh(OBJLoader.OBJMesh meshData) {
        if (this.vertexArray != null) {
            this.vertexArray.free();
        }
        this.vertexArray = VertexArray.create();

        int vbo = this.vertexArray.getOrCreateBuffer(VertexArray.VERTEX_BUFFER);
        ByteBuffer vertexData = org.lwjgl.BufferUtils.createByteBuffer(meshData.vertices().length * Float.BYTES);
        vertexData.asFloatBuffer().put(meshData.vertices());
        vertexData.rewind();
        VertexArray.upload(vbo, vertexData, VertexArray.DrawUsage.STATIC);

        ByteBuffer indexData = org.lwjgl.BufferUtils.createByteBuffer(meshData.indices().length * Integer.BYTES);
        indexData.asIntBuffer().put(meshData.indices());
        indexData.rewind();
        this.vertexArray.uploadIndexBuffer(indexData, VertexArray.IndexType.INT);

        VertexArrayBuilder builder = this.vertexArray.editFormat();
        int stride = (3 + 2 + 3) * Float.BYTES; // pos (3), uv (2), normal (3)
        builder.defineVertexBuffer(0, vbo, 0, stride, 0);

        builder.setVertexAttribute(0, 0, 3, VertexArrayBuilder.DataType.FLOAT, false, 0); // Position (location=0)
        builder.setVertexAttribute(1, 0, 2, VertexArrayBuilder.DataType.FLOAT, false, 3 * Float.BYTES); // UV0 (location=1)
        builder.setVertexAttribute(2, 0, 3, VertexArrayBuilder.DataType.FLOAT, true, 5 * Float.BYTES); // Normal (location=2)
    }

    public void updateTransform(Vector3 pos, Quaternion rot, Vector3 scale) {
        // If this is the first position update (prevPosition == Vector3.zero()), initialize both to avoid interpolation from origin
        if (this.prevPosition.equals(Vector3.zero()) && this.position.equals(Vector3.zero())) {
            this.prevPosition = pos;
            this.position = pos;
        } else {
            this.prevPosition = this.position;
            this.position = pos;
        }
        this.prevRotation = this.rotation;
        this.rotation = rot;
        this.scale = scale;
    }

    public long getId() { return id; }
    public String getModelPath() { return modelPath; }
    public Vector3 getInterpolatedPosition(float tickDelta) { return prevPosition.lerp(position, tickDelta); }
    public Quaternion getInterpolatedRotation(float tickDelta) { return prevRotation.slerp(rotation, tickDelta); }
    public VertexArray getVertexArray() { return vertexArray; }
    public Vector3 getScale() { return scale; }
    public Vector3 getPosition() { return position; }

    public BlockPos getBlockPos() {
        return BlockPos.ofFloored(position.x, position.y, position.z);
    }

    public void destroy() {
        if (this.vertexArray != null) {
            this.vertexArray.free();
            this.vertexArray = null;
        }
    }
}