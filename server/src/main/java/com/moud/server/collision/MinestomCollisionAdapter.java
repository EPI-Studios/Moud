package com.moud.server.collision;

import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;

import java.util.ArrayList;
import java.util.List;

public class MinestomCollisionAdapter {

    public static List<BoundingBox> convertToBoundingBoxes(List<OBB> obbs, Vector3 position, Quaternion rotation, Vector3 scale) {
        List<BoundingBox> boxes = new ArrayList<>();

        for (OBB obb : obbs) {
            Vector3 scaledCenter = new Vector3(
                obb.center.x * scale.x,
                obb.center.y * scale.y,
                obb.center.z * scale.z
            );
            Vector3 rotatedCenter = rotation.rotate(scaledCenter);
            Vector3 worldCenter = position.add(rotatedCenter);

            Vector3 scaledExtents = new Vector3(
                Math.abs(obb.halfExtents.x * scale.x),
                Math.abs(obb.halfExtents.y * scale.y),
                Math.abs(obb.halfExtents.z * scale.z)
            );

            boxes.add(new BoundingBox(
                scaledExtents.x * 2,
                scaledExtents.y * 2,
                scaledExtents.z * 2,
                new Vec(worldCenter.x - scaledExtents.x, worldCenter.y - scaledExtents.y, worldCenter.z - scaledExtents.z)
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

        return new BoundingBox(
            maxX - minX,
            maxY - minY,
            maxZ - minZ,
            new Vec(minX, minY, minZ)
        );
    }
}
