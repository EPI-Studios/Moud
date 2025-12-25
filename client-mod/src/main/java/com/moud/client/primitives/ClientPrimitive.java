package com.moud.client.primitives;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ClientPrimitive {
    private static final float INTERPOLATION_MS = 60.0f;
    private final long id;
    private final MoudPackets.PrimitiveType type;
    private final String groupId;
    private Vector3 position;
    private Quaternion rotation;
    private Vector3 scale;
    private MoudPackets.PrimitiveMaterial material;
    private List<Vector3> vertices;
    private List<Integer> indices;
    private Vector3 previousPosition;
    private Vector3 targetPosition;
    private Quaternion previousRotation;
    private Quaternion targetRotation;
    private Vector3 previousScale;
    private Vector3 targetScale;
    private long lastUpdateTime;

    public record MeshBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    }

    private volatile @Nullable MeshBounds meshBounds;

    private boolean hasCollision;
    private boolean isDynamic;
    private float mass;

    public ClientPrimitive(long id, MoudPackets.PrimitiveType type, Vector3 position, Quaternion rotation,
                           Vector3 scale, MoudPackets.PrimitiveMaterial material, List<Vector3> vertices,
                           List<Integer> indices, String groupId, MoudPackets.PrimitivePhysics physics) {
        this.id = id;
        this.type = type;
        this.groupId = groupId;
        this.position = position != null ? new Vector3(position) : Vector3.zero();
        this.rotation = rotation != null ? new Quaternion(rotation) : Quaternion.identity();
        this.scale = scale != null ? new Vector3(scale) : Vector3.one();
        this.material = material != null ? material : MoudPackets.PrimitiveMaterial.solid(1f, 1f, 1f);
        this.vertices = copyVertices(vertices);
        this.indices = copyIndices(indices);
        recomputeMeshBounds();

        if (physics != null) {
            this.hasCollision = physics.hasCollision();
            this.isDynamic = physics.isDynamic();
            this.mass = physics.mass();
        } else {
            boolean isLine = type == MoudPackets.PrimitiveType.LINE || type == MoudPackets.PrimitiveType.LINE_STRIP;
            this.hasCollision = !isLine;
            this.isDynamic = false;
            this.mass = 0f;
        }

        this.previousPosition = new Vector3(this.position);
        this.targetPosition = new Vector3(this.position);
        this.previousRotation = new Quaternion(this.rotation);
        this.targetRotation = new Quaternion(this.rotation);
        this.previousScale = new Vector3(this.scale);
        this.targetScale = new Vector3(this.scale);
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public ClientPrimitive(long id, MoudPackets.PrimitiveType type, Vector3 position, Quaternion rotation,
                           Vector3 scale, MoudPackets.PrimitiveMaterial material, List<Vector3> vertices,
                           List<Integer> indices, String groupId) {
        this(id, type, position, rotation, scale, material, vertices, indices, groupId, null);
    }

    public long getId() {
        return id;
    }

    public MoudPackets.PrimitiveType getType() {
        return type;
    }

    public String getGroupId() {
        return groupId;
    }

    public MoudPackets.PrimitiveMaterial getMaterial() {
        return material;
    }

    public List<Vector3> getVertices() {
        return vertices;
    }

    public List<Integer> getIndices() {
        return indices;
    }

    public @Nullable MeshBounds getMeshBounds() {
        return meshBounds;
    }

    public boolean isLineType() {
        return type == MoudPackets.PrimitiveType.LINE || type == MoudPackets.PrimitiveType.LINE_STRIP;
    }

    public boolean isUnlit() {
        return material != null && material.unlit();
    }

    public boolean isRenderThroughBlocks() {
        return material != null && material.renderThroughBlocks();
    }

    public boolean isDoubleSided() {
        return material != null && material.doubleSided();
    }

    public boolean hasCollision() {
        return hasCollision;
    }

    public boolean isDynamic() {
        return isDynamic;
    }

    public float getMass() {
        return mass;
    }

    public void updateTransform(Vector3 newPos, Quaternion newRot, Vector3 newScale) {
        float t = getInterpolationT();
        this.previousPosition = lerpVec(previousPosition, targetPosition, t);
        this.previousRotation = previousRotation.slerp(targetRotation, t);
        this.previousScale = lerpVec(previousScale, targetScale, t);

        if (newPos != null) {
            this.position = new Vector3(newPos);
            this.targetPosition = new Vector3(newPos);
        }
        if (newRot != null) {
            this.rotation = new Quaternion(newRot);
            this.targetRotation = new Quaternion(newRot);
        }
        if (newScale != null) {
            this.scale = new Vector3(newScale);
            this.targetScale = new Vector3(newScale);
        }
        this.lastUpdateTime = System.currentTimeMillis();
    }

    private float getInterpolationT() {
        long elapsed = System.currentTimeMillis() - lastUpdateTime;
        return Math.min(1.0f, elapsed / INTERPOLATION_MS);
    }

    private Vector3 lerpVec(Vector3 a, Vector3 b, float t) {
        return new Vector3(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    public void updateMaterial(MoudPackets.PrimitiveMaterial material) {
        if (material != null) {
            this.material = material;
        }
    }

    public void updateVertices(List<Vector3> verts) {
        this.vertices = copyVertices(verts);
        recomputeMeshBounds();
    }

    public void updateMesh(List<Vector3> verts, List<Integer> inds) {
        this.vertices = copyVertices(verts);
        this.indices = copyIndices(inds);
        recomputeMeshBounds();
    }

    public void tickSmoothing(float deltaTicks) {
    }

    public Vector3 getInterpolatedPosition(float tickDelta) {
        float t = getInterpolationT();
        return lerpVec(previousPosition, targetPosition, t);
    }

    public Quaternion getInterpolatedRotation(float tickDelta) {
        float t = getInterpolationT();
        return previousRotation.slerp(targetRotation, t);
    }

    public Vector3 getInterpolatedScale(float tickDelta) {
        float t = getInterpolationT();
        return lerpVec(previousScale, targetScale, t);
    }

    private List<Vector3> copyVertices(List<Vector3> verts) {
        List<Vector3> copy = new ArrayList<>();
        if (verts != null) {
            for (Vector3 v : verts) {
                if (v != null) {
                    copy.add(new Vector3(v));
                }
            }
        }
        return copy;
    }

    private List<Integer> copyIndices(List<Integer> inds) {
        List<Integer> copy = new ArrayList<>();
        if (inds != null) {
            for (Integer idx : inds) {
                if (idx != null) {
                    copy.add(idx);
                }
            }
        }
        return copy;
    }

    private void recomputeMeshBounds() {
        if (type != MoudPackets.PrimitiveType.MESH || vertices == null || vertices.isEmpty()) {
            meshBounds = null;
            return;
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        boolean found = false;
        for (Vector3 v : vertices) {
            if (v == null) {
                continue;
            }
            found = true;
            minX = Math.min(minX, v.x);
            minY = Math.min(minY, v.y);
            minZ = Math.min(minZ, v.z);
            maxX = Math.max(maxX, v.x);
            maxY = Math.max(maxY, v.y);
            maxZ = Math.max(maxZ, v.z);
        }
        meshBounds = found ? new MeshBounds(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }
}
