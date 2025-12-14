package com.moud.client.primitives;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;

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
    private Vector3 previousPosition;
    private Vector3 targetPosition;
    private Quaternion previousRotation;
    private Quaternion targetRotation;
    private Vector3 previousScale;
    private Vector3 targetScale;
    private long lastUpdateTime;

    public ClientPrimitive(long id, MoudPackets.PrimitiveType type, Vector3 position, Quaternion rotation,
                           Vector3 scale, MoudPackets.PrimitiveMaterial material, List<Vector3> vertices,
                           String groupId) {
        this.id = id;
        this.type = type;
        this.groupId = groupId;
        this.position = position != null ? new Vector3(position) : Vector3.zero();
        this.rotation = rotation != null ? new Quaternion(rotation) : Quaternion.identity();
        this.scale = scale != null ? new Vector3(scale) : Vector3.one();
        this.material = material != null ? material : MoudPackets.PrimitiveMaterial.solid(1f, 1f, 1f);
        this.vertices = copyVertices(vertices);

        this.previousPosition = new Vector3(this.position);
        this.targetPosition = new Vector3(this.position);
        this.previousRotation = new Quaternion(this.rotation);
        this.targetRotation = new Quaternion(this.rotation);
        this.previousScale = new Vector3(this.scale);
        this.targetScale = new Vector3(this.scale);
        this.lastUpdateTime = System.currentTimeMillis();
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
}
