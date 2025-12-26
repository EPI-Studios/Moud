package com.moud.server.collision;

import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Vec;

import java.util.ArrayList;
import java.util.List;

public class MinestomCollisionAdapter {
    private static final double MAX_MAIN_BOX_DIM = 64.0;

    public static List<BoundingBox> convertToBoundingBoxes(List<OBB> obbs, Quaternion rotation, Vector3 scale) {
        List<BoundingBox> boxes = new ArrayList<>();

        for (OBB obb : obbs) {
            Vector3 scaledCenter = new Vector3(
                    obb.center.x * scale.x,
                    obb.center.y * scale.y,
                    obb.center.z * scale.z
            );
            Vector3 rotatedCenter = rotation.rotate(scaledCenter);

            Vector3 scaledExtents = new Vector3(
                    Math.abs(obb.halfExtents.x * scale.x),
                    Math.abs(obb.halfExtents.y * scale.y),
                    Math.abs(obb.halfExtents.z * scale.z)
            );

            boxes.add(new BoundingBox(
                    scaledExtents.x * 2,
                    scaledExtents.y * 2,
                    scaledExtents.z * 2,
                    new Vec(rotatedCenter.x, rotatedCenter.y - scaledExtents.y, rotatedCenter.z)
            ));
        }

        return boxes;
    }

    public static BoundingBox getLargestBox(List<BoundingBox> boxes) {
        if (boxes.isEmpty()) {
            return new BoundingBox(1, 1, 1);
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (BoundingBox box : boxes) {
            double boxMinX = box.minX();
            double boxMinY = box.minY();
            double boxMinZ = box.minZ();
            double boxMaxX = box.maxX();
            double boxMaxY = box.maxY();
            double boxMaxZ = box.maxZ();

            minX = Math.min(minX, boxMinX);
            minY = Math.min(minY, boxMinY);
            minZ = Math.min(minZ, boxMinZ);
            maxX = Math.max(maxX, boxMaxX);
            maxY = Math.max(maxY, boxMaxY);
            maxZ = Math.max(maxZ, boxMaxZ);
        }

        double width = maxX - minX;
        double height = maxY - minY;
        double depth = maxZ - minZ;

        width = Math.max(0.25, Math.min(width, MAX_MAIN_BOX_DIM));
        height = Math.max(0.25, Math.min(height, MAX_MAIN_BOX_DIM));
        depth = Math.max(0.25, Math.min(depth, MAX_MAIN_BOX_DIM));

        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        double newMinY = centerY - height * 0.5;

        return new BoundingBox(
                width,
                height,
                depth,
                new Vec(centerX, newMinY, centerZ)
        );
    }
}
