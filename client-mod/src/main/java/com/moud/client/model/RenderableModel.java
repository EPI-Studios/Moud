package com.moud.client.model;

import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.rendering.MeshBuffer;
import com.moud.client.util.OBJLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RenderableModel {
    public static final int FLOATS_PER_VERTEX = 8;

    private final long id;
    private final String modelPath;
    private float[] vertices;
    private int[] indices;
    private MeshBuffer meshBuffer;
    private Vector3 position = Vector3.zero();
    private Quaternion rotation = Quaternion.identity();
    private Vector3 scale = Vector3.one();

    private Vector3 renderPosition = Vector3.zero();
    private Vector3 previousRenderPosition = Vector3.zero();
    private Quaternion renderRotation = Quaternion.identity();
    private Quaternion previousRenderRotation = Quaternion.identity();
    private Vector3 renderScale = Vector3.one();
    private Vector3 previousRenderScale = Vector3.one();

    private Vector3 smoothingStartPosition = Vector3.zero();
    private Quaternion smoothingStartRotation = Quaternion.identity();
    private Vector3 smoothingStartScale = Vector3.one();
    private static final float DEFAULT_SMOOTHING_TICKS = 5.0f;
    private static final float SNAP_DISTANCE_SQ = 64.0f;
    private float smoothingDurationTicks = DEFAULT_SMOOTHING_TICKS;
    private float smoothingProgress = 1.0f;
    private boolean smoothingEnabled = true;
    private boolean firstUpdate = true;
    private Identifier texture = Identifier.of("minecraft", "textures/block/white_concrete.png");
    private double collisionWidth;
    private double collisionHeight;
    private double collisionDepth;
    private List<OBB> collisionBoxes = new ArrayList<>();
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

        if (this.meshBuffer != null) {
            this.meshBuffer.close();
        }
        this.meshBuffer = new MeshBuffer(this.vertices, this.indices);
    }

    public void updateTransform(Vector3 pos, Quaternion rot, Vector3 scale) {
        if (pos == null || rot == null || scale == null) {
            return;
        }

        this.position = new Vector3(pos);
        this.rotation = new Quaternion(rot);
        this.scale = new Vector3(scale);

        if (firstUpdate || !smoothingEnabled) {
            this.renderPosition = new Vector3(pos);
            this.previousRenderPosition = new Vector3(pos);
            this.smoothingStartPosition = new Vector3(pos);

            this.renderRotation = new Quaternion(rot);
            this.previousRenderRotation = new Quaternion(rot);
            this.smoothingStartRotation = new Quaternion(rot);

            this.renderScale = new Vector3(scale);
            this.previousRenderScale = new Vector3(scale);
            this.smoothingStartScale = new Vector3(scale);

            this.smoothingProgress = 1.0f;
            firstUpdate = false;
            return;
        }

        this.smoothingStartPosition = new Vector3(renderPosition);
        this.smoothingStartRotation = new Quaternion(renderRotation);
        this.smoothingStartScale = new Vector3(renderScale);

        if (smoothingEnabled) {
            float distanceSq = distanceSquared(renderPosition, position);
            if (distanceSq > SNAP_DISTANCE_SQ) {
                snapToTarget();
                return;
            }
            this.smoothingProgress = 0.0f;
        } else {
            snapToTarget();
        }
    }

    public long getId() { return id; }
    public String getModelPath() { return modelPath; }
    public Vector3 getInterpolatedPosition(float tickDelta) {
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        return new Vector3(
                previousRenderPosition.x + (renderPosition.x - previousRenderPosition.x) * t,
                previousRenderPosition.y + (renderPosition.y - previousRenderPosition.y) * t,
                previousRenderPosition.z + (renderPosition.z - previousRenderPosition.z) * t
        );
    }

    public Quaternion getInterpolatedRotation(float tickDelta) {
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        return previousRenderRotation.slerp(renderRotation, t);
    }
    public Vector3 getInterpolatedScale(float tickDelta) {
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        return new Vector3(
                previousRenderScale.x + (renderScale.x - previousRenderScale.x) * t,
                previousRenderScale.y + (renderScale.y - previousRenderScale.y) * t,
                previousRenderScale.z + (renderScale.z - previousRenderScale.z) * t
        );
    }

    public void tickSmoothing(float deltaTicks) {
        if (firstUpdate || !smoothingEnabled) {
            return;
        }

        if (deltaTicks <= 0.0f) {
            deltaTicks = 1.0f;
        }

        this.previousRenderPosition = new Vector3(renderPosition);
        this.previousRenderRotation = new Quaternion(renderRotation);
        this.previousRenderScale = new Vector3(renderScale);

        if (smoothingProgress >= 1.0f) {
            snapToTarget();
            return;
        }

        float duration = Math.max(1.0f, smoothingDurationTicks);
        smoothingProgress = Math.min(1.0f, smoothingProgress + (deltaTicks / duration));
        float t = Math.max(0.0f, Math.min(1.0f, smoothingProgress));

        this.renderPosition = Vector3.lerp(smoothingStartPosition, position, t);
        this.renderRotation = smoothingStartRotation.slerp(rotation, t);
        this.renderScale = Vector3.lerp(smoothingStartScale, scale, t);
    }

    private void snapToTarget() {
        this.renderPosition = new Vector3(position);
        this.previousRenderPosition = new Vector3(position);
        this.smoothingStartPosition = new Vector3(position);

        this.renderRotation = new Quaternion(rotation);
        this.previousRenderRotation = new Quaternion(rotation);
        this.smoothingStartRotation = new Quaternion(rotation);

        this.renderScale = new Vector3(scale);
        this.previousRenderScale = new Vector3(scale);
        this.smoothingStartScale = new Vector3(scale);

        this.smoothingProgress = 1.0f;
    }

    public Quaternion getRotation() { return rotation; }
    public Vector3 getScale() { return scale; }
    public Vector3 getPosition() { return position; }
    public void setSmoothingDurationTicks(float ticks) {
        this.smoothingDurationTicks = Math.max(1.0f, ticks);
    }
    public float getSmoothingDurationTicks() {
        return smoothingDurationTicks;
    }
    public void setSmoothingEnabled(boolean enabled) {
        this.smoothingEnabled = enabled;
        if (!enabled) {
            snapToTarget();
        }
    }
    public boolean isSmoothingEnabled() {
        return smoothingEnabled;
    }
    public boolean hasMeshData() { return vertices != null && indices != null; }
    public float[] getVertices() { return vertices; }
    public int[] getIndices() { return indices; }
    public MeshBuffer getMeshBuffer() { return meshBuffer; }
    public boolean hasMeshBuffer() { return meshBuffer != null && meshBuffer.isUploaded(); }
    public Identifier getTexture() { return texture; }
    public void setTexture(Identifier texture) {
        Identifier normalized = normalizeTexture(texture);
        if (normalized == null) {
            return;
        }
        if (!normalized.equals(this.texture)) {
            this.texture = normalized;
            org.slf4j.LoggerFactory.getLogger(RenderableModel.class)
                    .info("RenderableModel {} texture set to {}", id, normalized);
        }
    }
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
    public void setCollisionBoxes(List<OBB> boxes) {
        this.collisionBoxes = new ArrayList<>(boxes);
    }
    public List<OBB> getCollisionBoxes() {
        return new ArrayList<>(collisionBoxes);
    }
    public boolean hasCollisionBoxes() {
        return !collisionBoxes.isEmpty();
    }

    @Override
    public String toString() {
        return "RenderableModel{id=" + id + ", modelPath='" + modelPath + "', texture=" + texture + "}";
    }
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
        if (this.meshBuffer != null) {
            this.meshBuffer.close();
            this.meshBuffer = null;
        }
        this.vertices = null;
        this.indices = null;
        this.meshMin = null;
        this.meshMax = null;
    }

    private static float distanceSquared(Vector3 a, Vector3 b) {
        if (a == null || b == null) {
            return Float.POSITIVE_INFINITY;
        }
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        float dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
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

    private Identifier normalizeTexture(Identifier original) {
        if (original == null) {
            return null;
        }
        if ("moud".equals(original.getNamespace())) {
            String path = original.getPath();
            if (path.startsWith("moud/") && path.length() > 5) {
                return Identifier.of("moud", path.substring(5));
            }
        }
        return original;
    }
}
