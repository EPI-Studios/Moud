package com.moud.client.model;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.util.OBJLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;

public class RenderableModel {
    public static final int FLOATS_PER_VERTEX = 8;

    private final long id;
    private final String modelPath;
    private float[] vertices;
    private int[] indices;
    private Vector3 position = Vector3.zero();
    private Quaternion rotation = Quaternion.identity();
    private Vector3 scale = Vector3.one();
    private Identifier texture = Identifier.of("minecraft", "textures/block/white_concrete.png");
    private double collisionWidth;
    private double collisionHeight;
    private double collisionDepth;
    private Vector3 meshMin;
    private Vector3 meshMax;

    public RenderableModel(long id, String modelPath) {
        this.id = id;
        this.modelPath = modelPath;
    }

    public void uploadMesh(OBJLoader.OBJMesh meshData) {
        this.vertices = Arrays.copyOf(meshData.vertices(), meshData.vertices().length);
        this.indices = Arrays.copyOf(meshData.indices(), meshData.indices().length);
        computeMeshBounds();
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
    public Quaternion getRotation() { return rotation; }
    public Vector3 getScale() { return scale; }
    public Vector3 getPosition() { return position; }
    public boolean hasMeshData() { return vertices != null && indices != null; }
    public float[] getVertices() { return vertices; }
    public int[] getIndices() { return indices; }
    public Identifier getTexture() { return texture; }
    public void setTexture(Identifier texture) { this.texture = texture; }
    public boolean hasCollisionBox() {
        return collisionWidth > 0 && collisionHeight > 0 && collisionDepth > 0;
    }
    public void updateCollisionBox(double width, double height, double depth) {
        this.collisionWidth = width;
        this.collisionHeight = height;
        this.collisionDepth = depth;
    }
    public double getCollisionWidth() { return collisionWidth; }
    public double getCollisionHeight() { return collisionHeight; }
    public double getCollisionDepth() { return collisionDepth; }
    public boolean hasMeshBounds() {
        return meshMin != null && meshMax != null;
    }
    public Vector3 getMeshMin() {
        return meshMin;
    }
    public Vector3 getMeshMax() {
        return meshMax;
    }
    public Vector3 getMeshCenter() {
        if (!hasMeshBounds()) {
            return Vector3.zero();
        }
        return new Vector3(
                (meshMin.x + meshMax.x) * 0.5f,
                (meshMin.y + meshMax.y) * 0.5f,
                (meshMin.z + meshMax.z) * 0.5f
        );
    }
    public Vector3 getMeshHalfExtents() {
        if (!hasMeshBounds()) {
            return Vector3.zero();
        }
        return new Vector3(
                (meshMax.x - meshMin.x) * 0.5f,
                (meshMax.y - meshMin.y) * 0.5f,
                (meshMax.z - meshMin.z) * 0.5f
        );
    }

    public BlockPos getBlockPos() {
        return BlockPos.ofFloored(position.x, position.y, position.z);
    }

    public void destroy() {
        this.vertices = null;
        this.indices = null;
        this.meshMin = null;
        this.meshMax = null;
    }

    private void computeMeshBounds() {
        if (vertices == null || vertices.length < 8) {
            meshMin = null;
            meshMax = null;
            return;
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < vertices.length; i += 8) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)) {
            meshMin = null;
            meshMax = null;
            return;
        }
        meshMin = new Vector3(minX, minY, minZ);
        meshMax = new Vector3(maxX, maxY, maxZ);
    }
}
