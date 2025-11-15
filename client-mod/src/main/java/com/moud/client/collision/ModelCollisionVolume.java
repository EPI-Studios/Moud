package com.moud.client.collision;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.model.RenderableModel;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

public final class ModelCollisionVolume {
    private static final float ROTATION_EPSILON = 1.0e-3f;

    private final long modelId;
    private Vector3 position = Vector3.zero();
    private Quaternion rotation = Quaternion.identity();
    private Vector3 scale = Vector3.one();
    private double fallbackWidth;
    private double fallbackHeight;
    private double fallbackDepth;
    private Box bounds;
    private VoxelShape voxelShape;

    public ModelCollisionVolume(long modelId) {
        this.modelId = modelId;
    }

    public long getModelId() {
        return modelId;
    }

    public void update(RenderableModel model) {
        Objects.requireNonNull(model, "model");
        Vector3 modelPos = model.getPosition();
        this.position = modelPos != null ? new Vector3(modelPos) : Vector3.zero();
        Quaternion modelRot = model.getRotation();
        this.rotation = modelRot != null ? new Quaternion(modelRot) : Quaternion.identity();
        this.scale = model.getScale() != null ? new Vector3(model.getScale()) : Vector3.one();
        this.fallbackWidth = model.getCollisionWidth();
        this.fallbackHeight = model.getCollisionHeight();
        this.fallbackDepth = model.getCollisionDepth();
        rebuildVolume(model);
    }

    public boolean isActive() {
        return bounds != null && voxelShape != null;
    }

    public boolean intersects(Box other) {
        return bounds != null && bounds.intersects(other);
    }

    public Box getBounds() {
        return bounds;
    }

    public VoxelShape getVoxelShape() {
        return voxelShape;
    }

    private void rebuildVolume(RenderableModel model) {
        Box computed = computeFromMeshVertices(model);
        if (computed == null) {
            computed = computeFromFallback();
        }
        bounds = computed;
        voxelShape = bounds == null ? null : VoxelShapes.cuboid(
                bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ
        );
    }

    private Box computeFromMeshVertices(RenderableModel model) {
        if (model == null) {
            return null;
        }

        float[] vertices = model.getVertices();
        if (vertices == null || vertices.length < 3) {
            return null;
        }

        Vector3 scaleVec = this.scale != null ? this.scale : Vector3.one();
        Quaternionf rotationQuat = new Quaternionf(rotation.x, rotation.y, rotation.z, rotation.w);
        if (rotationQuat.lengthSquared() > 0) {
            rotationQuat.normalize();
        } else {
            rotationQuat.identity();
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        Vector3f scratch = new Vector3f();

        for (int i = 0; i < vertices.length; i += RenderableModel.FLOATS_PER_VERTEX) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            scratch.set(
                    x * scaleVec.x,
                    y * scaleVec.y,
                    z * scaleVec.z
            );

            rotationQuat.transform(scratch);
            scratch.add(position.x, position.y, position.z);

            double cx = scratch.x();
            double cy = scratch.y();
            double cz = scratch.z();

            minX = Math.min(minX, cx);
            minY = Math.min(minY, cy);
            minZ = Math.min(minZ, cz);
            maxX = Math.max(maxX, cx);
            maxY = Math.max(maxY, cy);
            maxZ = Math.max(maxZ, cz);
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)) {
            return null;
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Box computeFromFallback() {
        if (fallbackWidth <= 0 || fallbackHeight <= 0 || fallbackDepth <= 0) {
            return null;
        }

        float lenSq = rotation.x * rotation.x + rotation.y * rotation.y + rotation.z * rotation.z;
        if (lenSq <= ROTATION_EPSILON) {
            double halfX = fallbackWidth * 0.5d;
            double halfZ = fallbackDepth * 0.5d;
            double minX = position.x - halfX;
            double minY = position.y;
            double minZ = position.z - halfZ;
            return new Box(minX, minY, minZ, minX + fallbackWidth, minY + fallbackHeight, minZ + fallbackDepth);
        }

        Quaternionf rotationQuat = new Quaternionf(rotation.x, rotation.y, rotation.z, rotation.w);
        if (rotationQuat.lengthSquared() > 0) {
            rotationQuat.normalize();
        } else {
            rotationQuat.identity();
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        Vector3f center = new Vector3f(position.x, (float) (position.y + fallbackHeight * 0.5d), position.z);
        Vector3f halfExtents = new Vector3f(
                (float) (fallbackWidth * 0.5d),
                (float) (fallbackHeight * 0.5d),
                (float) (fallbackDepth * 0.5d)
        );

        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dy = -1; dy <= 1; dy += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    Vector3f corner = new Vector3f(
                            halfExtents.x * dx,
                            halfExtents.y * dy,
                            halfExtents.z * dz
                    );
                    rotationQuat.transform(corner);
                    corner.add(center);

                    double cx = corner.x();
                    double cy = corner.y();
                    double cz = corner.z();
                    minX = Math.min(minX, cx);
                    minY = Math.min(minY, cy);
                    minZ = Math.min(minZ, cz);
                    maxX = Math.max(maxX, cx);
                    maxY = Math.max(maxY, cy);
                    maxZ = Math.max(maxZ, cz);
                }
            }
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)) {
            return null;
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
