package com.moud.server.physics.primitives;

import com.moud.api.collision.AABB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.primitives.PrimitiveType;
import com.moud.server.primitives.PrimitiveInstance;

import java.util.List;

/**
 * Computes axis-aligned collision bounds for primitives, matching the client renderer transforms.
 */
public final class PrimitiveCollisionBounds {
    private static final double MIN_HALF_EXTENT = 0.03125;

    private PrimitiveCollisionBounds() {
    }

    public static AABB computeAabb(PrimitiveInstance primitive) {
        if (primitive == null || primitive.isRemoved()) {
            return null;
        }

        PrimitiveType type = primitive.getType();
        if (type == null || type == PrimitiveType.LINE || type == PrimitiveType.LINE_STRIP) {
            return null;
        }

        Vector3 position = primitive.getPosition();
        Quaternion rotation = primitive.getRotation();
        Vector3 scale = primitive.getScale();

        Vector3 pos = position != null ? position : Vector3.zero();
        Quaternion rot = rotation != null ? rotation : Quaternion.identity();
        Vector3 scl = scale != null ? scale : Vector3.one();

        if (type == PrimitiveType.MESH) {
            MeshBounds meshBounds = computeMeshBounds(primitive.getVertices());
            if (meshBounds == null) {
                return null;
            }
            return createMeshBounds(pos, rot, scl, meshBounds);
        }

        double baseHalfX = 0.5;
        double baseHalfY = 0.5;
        double baseHalfZ = 0.5;
        if (type == PrimitiveType.PLANE) {
            baseHalfY = MIN_HALF_EXTENT;
        } else if (type == PrimitiveType.CAPSULE) {
            baseHalfY = 1.0;
        }

        double halfX = Math.max(MIN_HALF_EXTENT, Math.abs(scl.x) * baseHalfX);
        double halfY = Math.max(MIN_HALF_EXTENT, Math.abs(scl.y) * baseHalfY);
        double halfZ = Math.max(MIN_HALF_EXTENT, Math.abs(scl.z) * baseHalfZ);

        return createOrientedBounds(pos.x, pos.y, pos.z, rot, halfX, halfY, halfZ);
    }

    private static MeshBounds computeMeshBounds(List<Vector3> vertices) {
        if (vertices == null || vertices.isEmpty()) {
            return null;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
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

        return found ? new MeshBounds(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    private static AABB createMeshBounds(Vector3 position, Quaternion rotation, Vector3 scale, MeshBounds meshBounds) {
        double minX = meshBounds.minX;
        double minY = meshBounds.minY;
        double minZ = meshBounds.minZ;
        double maxX = meshBounds.maxX;
        double maxY = meshBounds.maxY;
        double maxZ = meshBounds.maxZ;

        double localCenterX = (minX + maxX) * 0.5;
        double localCenterY = (minY + maxY) * 0.5;
        double localCenterZ = (minZ + maxZ) * 0.5;
        double localHalfX = (maxX - minX) * 0.5;
        double localHalfY = (maxY - minY) * 0.5;
        double localHalfZ = (maxZ - minZ) * 0.5;

        double scaledCenterX = localCenterX * scale.x;
        double scaledCenterY = localCenterY * scale.y;
        double scaledCenterZ = localCenterZ * scale.z;

        Vector3 rotatedCenter = rotate(rotation, scaledCenterX, scaledCenterY, scaledCenterZ);
        double centerX = position.x + rotatedCenter.x;
        double centerY = position.y + rotatedCenter.y;
        double centerZ = position.z + rotatedCenter.z;

        double halfX = Math.max(MIN_HALF_EXTENT, localHalfX * Math.abs(scale.x));
        double halfY = Math.max(MIN_HALF_EXTENT, localHalfY * Math.abs(scale.y));
        double halfZ = Math.max(MIN_HALF_EXTENT, localHalfZ * Math.abs(scale.z));

        return createOrientedBounds(centerX, centerY, centerZ, rotation, halfX, halfY, halfZ);
    }

    private static Vector3 rotate(Quaternion rotation, double x, double y, double z) {
        double qx = rotation.x;
        double qy = rotation.y;
        double qz = rotation.z;
        double qw = rotation.w;
        double tx = 2.0 * (qy * z - qz * y);
        double ty = 2.0 * (qz * x - qx * z);
        double tz = 2.0 * (qx * y - qy * x);
        double rx = x + qw * tx + (qy * tz - qz * ty);
        double ry = y + qw * ty + (qz * tx - qx * tz);
        double rz = z + qw * tz + (qx * ty - qy * tx);
        return new Vector3(rx, ry, rz);
    }

    private static AABB createOrientedBounds(
            double centerX,
            double centerY,
            double centerZ,
            Quaternion rotation,
            double halfX,
            double halfY,
            double halfZ
    ) {
        double x = rotation.x;
        double y = rotation.y;
        double z = rotation.z;
        double w = rotation.w;

        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double xy = x * y;
        double xz = x * z;
        double yz = y * z;
        double wx = w * x;
        double wy = w * y;
        double wz = w * z;

        double m00 = 1.0 - 2.0 * (yy + zz);
        double m01 = 2.0 * (xy - wz);
        double m02 = 2.0 * (xz + wy);
        double m10 = 2.0 * (xy + wz);
        double m11 = 1.0 - 2.0 * (xx + zz);
        double m12 = 2.0 * (yz - wx);
        double m20 = 2.0 * (xz - wy);
        double m21 = 2.0 * (yz + wx);
        double m22 = 1.0 - 2.0 * (xx + yy);

        double worldHalfX = Math.abs(m00) * halfX + Math.abs(m01) * halfY + Math.abs(m02) * halfZ;
        double worldHalfY = Math.abs(m10) * halfX + Math.abs(m11) * halfY + Math.abs(m12) * halfZ;
        double worldHalfZ = Math.abs(m20) * halfX + Math.abs(m21) * halfY + Math.abs(m22) * halfZ;

        return new AABB(
                centerX - worldHalfX,
                centerY - worldHalfY,
                centerZ - worldHalfZ,
                centerX + worldHalfX,
                centerY + worldHalfY,
                centerZ + worldHalfZ
        );
    }

    private static final class MeshBounds {
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;

        private MeshBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }
}
