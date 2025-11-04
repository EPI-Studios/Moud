package com.moud.client.model;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.util.OBJLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;

public class RenderableModel {
    private final long id;
    private final String modelPath;
    private float[] vertices;
    private int[] indices;
    private Vector3 position = Vector3.zero();
    private Quaternion rotation = Quaternion.identity();
    private Vector3 scale = Vector3.one();
    private Identifier texture = Identifier.of("minecraft", "textures/block/white_concrete.png");

    public RenderableModel(long id, String modelPath) {
        this.id = id;
        this.modelPath = modelPath;
    }

    public void uploadMesh(OBJLoader.OBJMesh meshData) {
        this.vertices = Arrays.copyOf(meshData.vertices(), meshData.vertices().length);
        this.indices = Arrays.copyOf(meshData.indices(), meshData.indices().length);
    }

    public void updateTransform(Vector3 pos, Quaternion rot, Vector3 scale) {
        this.position = pos;
        this.rotation = rot;
        this.scale = scale;
    }

    public long getId() { return id; }
    public String getModelPath() { return modelPath; }
    public Vector3 getInterpolatedPosition(float tickDelta) { return position; }
    public Quaternion getInterpolatedRotation(float tickDelta) { return rotation; }
    public Vector3 getScale() { return scale; }
    public Vector3 getPosition() { return position; }
    public boolean hasMeshData() { return vertices != null && indices != null; }
    public float[] getVertices() { return vertices; }
    public int[] getIndices() { return indices; }
    public Identifier getTexture() { return texture; }
    public void setTexture(Identifier texture) { this.texture = texture; }

    public BlockPos getBlockPos() {
        return BlockPos.ofFloored(position.x, position.y, position.z);
    }

    public void destroy() {
        this.vertices = null;
        this.indices = null;
    }
}
