package com.moud.client.primitives;

import com.moud.api.collision.AABB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;


public final class ClientPrimitiveCollisionBounds {
    private static final double MIN_HALF_EXTENT = 0.03125;

    private ClientPrimitiveCollisionBounds() {
    }

    public static AABB computeAabb(ClientPrimitive primitive) {
        if (primitive == null || !primitive.hasCollision()) {
            return null;
        }

        MoudPackets.PrimitiveType type = primitive.getType();
        if (type == null || type == MoudPackets.PrimitiveType.LINE || type == MoudPackets.PrimitiveType.LINE_STRIP) {
            return null;
        }

        Vector3 position = primitive.getInterpolatedPosition(0.0f);
        Quaternion rotation = primitive.getInterpolatedRotation(0.0f);
        Vector3 scale = primitive.getInterpolatedScale(0.0f);

        Vector3 pos = position != null ? position : Vector3.zero();
        Quaternion rot = rotation != null ? rotation : Quaternion.identity();
        Vector3 scl = scale != null ? scale : Vector3.one();

        if (type == MoudPackets.PrimitiveType.MESH) {
            ClientPrimitive.MeshBounds meshBounds = primitive.getMeshBounds();
            if (meshBounds == null) {
                return null;
            }
            return createMeshBounds(pos, rot, scl, meshBounds);
        }

        double baseHalfX = 0.5;
        double baseHalfY = 0.5;
        double baseHalfZ = 0.5;
        if (type == MoudPackets.PrimitiveType.PLANE) {
            baseHalfY = MIN_HALF_EXTENT;
        } else if (type == MoudPackets.PrimitiveType.CAPSULE) {
            baseHalfY = 1.0;
        }

        double halfX = Math.max(MIN_HALF_EXTENT, Math.abs(scl.x) * baseHalfX);
        double halfY = Math.max(MIN_HALF_EXTENT, Math.abs(scl.y) * baseHalfY);
        double halfZ = Math.max(MIN_HALF_EXTENT, Math.abs(scl.z) * baseHalfZ);

        return createOrientedBounds(pos.x, pos.y, pos.z, rot, halfX, halfY, halfZ);
    }

    private static AABB createMeshBounds(
            Vector3 position,
            Quaternion rotation,
            Vector3 scale,
            ClientPrimitive.MeshBounds meshBounds
    ) {
        double minX = meshBounds.minX();
        double minY = meshBounds.minY();
        double minZ = meshBounds.minZ();
        double maxX = meshBounds.maxX();
        double maxY = meshBounds.maxY();
        double maxZ = meshBounds.maxZ();

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
}
