package com.moud.api.collision;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

public class OBB {
    public Vector3 center;
    public Vector3 halfExtents;
    public Quaternion rotation;

    public OBB(Vector3 center, Vector3 halfExtents, Quaternion rotation) {
        this.center = center;
        this.halfExtents = halfExtents;
        this.rotation = rotation;
    }

    public Vector3[] getCorners() {
        Vector3[] corners = new Vector3[8];
        Vector3[] localCorners = {
            new Vector3(-halfExtents.x, -halfExtents.y, -halfExtents.z),
            new Vector3( halfExtents.x, -halfExtents.y, -halfExtents.z),
            new Vector3(-halfExtents.x,  halfExtents.y, -halfExtents.z),
            new Vector3( halfExtents.x,  halfExtents.y, -halfExtents.z),
            new Vector3(-halfExtents.x, -halfExtents.y,  halfExtents.z),
            new Vector3( halfExtents.x, -halfExtents.y,  halfExtents.z),
            new Vector3(-halfExtents.x,  halfExtents.y,  halfExtents.z),
            new Vector3( halfExtents.x,  halfExtents.y,  halfExtents.z)
        };

        for (int i = 0; i < 8; i++) {
            corners[i] = rotation.rotate(localCorners[i]).add(center);
        }
        return corners;
    }

    public Vector3 getMin() {
        Vector3[] corners = getCorners();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;

        for (Vector3 corner : corners) {
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
        }
        return new Vector3(minX, minY, minZ);
    }

    public Vector3 getMax() {
        Vector3[] corners = getCorners();
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (Vector3 corner : corners) {
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new Vector3(maxX, maxY, maxZ);
    }

    public boolean intersects(OBB other) {
        Vector3[] axes = new Vector3[15];
        Vector3[] thisAxes = getAxes();
        Vector3[] otherAxes = other.getAxes();

        System.arraycopy(thisAxes, 0, axes, 0, 3);
        System.arraycopy(otherAxes, 0, axes, 3, 3);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                axes[9 + i * 3 + j] = thisAxes[i].cross(otherAxes[j]);
            }
        }

        for (Vector3 axis : axes) {
            if (axis.lengthSquared() < 1e-6f) continue;
            axis = axis.normalize();

            float[] thisProj = project(axis);
            float[] otherProj = other.project(axis);

            if (thisProj[1] < otherProj[0] || otherProj[1] < thisProj[0]) {
                return false;
            }
        }
        return true;
    }

    private Vector3[] getAxes() {
        return new Vector3[] {
            rotation.rotate(new Vector3(1, 0, 0)),
            rotation.rotate(new Vector3(0, 1, 0)),
            rotation.rotate(new Vector3(0, 0, 1))
        };
    }

    private float[] project(Vector3 axis) {
        Vector3[] corners = getCorners();
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;

        for (Vector3 corner : corners) {
            float proj = corner.dot(axis);
            min = Math.min(min, proj);
            max = Math.max(max, proj);
        }
        return new float[] {min, max};
    }
}
