package com.moud.server.raycast;

import com.moud.api.math.Vector3;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class RaycastUtil {

    public static RaycastResult performRaycast(@NotNull Instance instance, @NotNull Point origin, @NotNull Vec direction,
                                               double maxDistance, @Nullable Predicate<Entity> entityFilter) {
        final double step = 0.1;
        Vec normalizedDirection = direction.normalize();
        if (normalizedDirection.lengthSquared() == 0) {
            return RaycastResult.noHit(new Vector3((float) origin.x(), (float) origin.y(), (float) origin.z()), 0);
        }

        Entity closestEntity = null;
        double closestEntityDistance = maxDistance;
        Point entityHitPoint = null;

        for (Entity entity : instance.getEntities()) {
            if (entityFilter != null && !entityFilter.test(entity)) {
                continue;
            }

            double distance = raycastToBoundingBox(origin, normalizedDirection, entity.getBoundingBox(), entity.getPosition());
            if (distance >= 0 && distance < closestEntityDistance) {
                closestEntityDistance = distance;
                closestEntity = entity;
                entityHitPoint = origin.add(normalizedDirection.mul(distance));
            }
        }

        Point lastPos = origin;
        for (double d = 0; d < closestEntityDistance; d += step) {
            Point currentPos = origin.add(normalizedDirection.mul(d));
            Block block = instance.getBlock(currentPos);

            if (!block.isAir() && !block.isLiquid()) {
                Point hitPos = origin.add(normalizedDirection.mul(Math.max(0, d - step)));
                Vector3 normal = calculateBlockNormal(lastPos, currentPos);
                return new RaycastResult(
                        true,
                        new Vector3((float) hitPos.x(), (float) hitPos.y(), (float) hitPos.z()),
                        normal,
                        null,
                        block,
                        Math.sqrt(hitPos.distanceSquared(origin))
                );
            }
            lastPos = currentPos;
        }

        if (closestEntity != null) {
            return new RaycastResult(
                    true,
                    new Vector3((float) entityHitPoint.x(), (float) entityHitPoint.y(), (float) entityHitPoint.z()),
                    new Vector3(0, 1, 0),
                    closestEntity,
                    null,
                    closestEntityDistance
            );
        }

        Point endPoint = origin.add(normalizedDirection.mul(maxDistance));
        return RaycastResult.noHit(new Vector3((float) endPoint.x(), (float) endPoint.y(), (float) endPoint.z()), maxDistance);
    }

    private static double raycastToBoundingBox(Point rayOrigin, Vec rayDirection, BoundingBox box, Pos boxPosition) {
        Point boxMin = boxPosition.add(box.minX(), box.minY(), box.minZ());
        Point boxMax = boxPosition.add(box.maxX(), box.maxY(), box.maxZ());

        double tmin = 0.0;
        double tmax = Double.POSITIVE_INFINITY;

        if (Math.abs(rayDirection.x()) < 1e-6) {
            if (rayOrigin.x() < boxMin.x() || rayOrigin.x() > boxMax.x()) return -1;
        } else {
            double ood = 1.0 / rayDirection.x();
            double t1 = (boxMin.x() - rayOrigin.x()) * ood;
            double t2 = (boxMax.x() - rayOrigin.x()) * ood;
            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }

        if (Math.abs(rayDirection.y()) < 1e-6) {
            if (rayOrigin.y() < boxMin.y() || rayOrigin.y() > boxMax.y()) return -1;
        } else {
            double ood = 1.0 / rayDirection.y();
            double t1 = (boxMin.y() - rayOrigin.y()) * ood;
            double t2 = (boxMax.y() - rayOrigin.y()) * ood;
            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }

        if (Math.abs(rayDirection.z()) < 1e-6) {
            if (rayOrigin.z() < boxMin.z() || rayOrigin.z() > boxMax.z()) return -1;
        } else {
            double ood = 1.0 / rayDirection.z();
            double t1 = (boxMin.z() - rayOrigin.z()) * ood;
            double t2 = (boxMax.z() - rayOrigin.z()) * ood;
            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }

        return tmin;
    }

    private static Vector3 calculateBlockNormal(Point lastPos, Point currentPos) {
        int lastBlockX = lastPos.blockX();
        int lastBlockY = lastPos.blockY();
        int lastBlockZ = lastPos.blockZ();

        int currentBlockX = currentPos.blockX();
        int currentBlockY = currentPos.blockY();
        int currentBlockZ = currentPos.blockZ();

        if (currentBlockX > lastBlockX) return Vector3.left();
        if (currentBlockX < lastBlockX) return Vector3.right();
        if (currentBlockY > lastBlockY) return Vector3.down();
        if (currentBlockY < lastBlockY) return Vector3.up();
        if (currentBlockZ > lastBlockZ) return Vector3.backward();
        if (currentBlockZ < lastBlockZ) return Vector3.forward();

        return Vector3.up();
    }
}