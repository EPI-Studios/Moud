package com.moud.server.raycast;

import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.entity.ModelManager;
import com.moud.server.physics.mesh.ModelCollisionLibrary;
import com.moud.server.proxy.ModelProxy;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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

        Vector3 rayOrigin = new Vector3((float) origin.x(), (float) origin.y(), (float) origin.z());
        Vector3 rayDir = new Vector3((float) normalizedDirection.x(), (float) normalizedDirection.y(), (float) normalizedDirection.z());

        for (Entity entity : instance.getEntities()) {
            if (entityFilter != null && !entityFilter.test(entity)) {
                continue;
            }

            double distance;
            ModelProxy model = ModelManager.getInstance().getByEntity(entity);

            if (model != null) {
                if (isMeshCollision(model.getWireCollisionMode())) {
                    distance = raycastModelMesh(rayOrigin, rayDir, model, maxDistance);
                } else {
                    distance = raycastModelOBB(rayOrigin, rayDir, model, maxDistance);
                }
            } else {
                distance = raycastToBoundingBox(origin, normalizedDirection, entity.getBoundingBox(), entity.getPosition());
            }

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

    private static boolean isMeshCollision(MoudPackets.CollisionMode mode) {
        return mode == MoudPackets.CollisionMode.MESH;
    }

    private static double raycastModelMesh(Vector3 origin, Vector3 direction, ModelProxy model, double maxDist) {
        ModelCollisionLibrary.MeshData mesh = ModelCollisionLibrary.getMesh(model.getModelPath());
        if (mesh == null || mesh.vertices() == null || mesh.indices() == null) {
            return raycastModelOBB(origin, direction, model, maxDist);
        }

        Entity entity = model.getEntity();
        if (entity != null) {
            double aabbDist = raycastToBoundingBox(
                    new Pos(origin.x, origin.y, origin.z),
                    new Vec(direction.x, direction.y, direction.z),
                    entity.getBoundingBox(),
                    entity.getPosition()
            );
            if (aabbDist < 0 || aabbDist > maxDist) return -1;
        }

        Vector3 localOrigin = origin.subtract(model.getPosition());
        Quaternion invRot = model.getRotation().conjugate();
        localOrigin = invRot.rotate(localOrigin);
        Vector3 localDir = invRot.rotate(direction);

        Vector3 scale = model.getScale();

        float[] verts = mesh.vertices();
        int[] indices = mesh.indices();
        double closestT = Double.MAX_VALUE;
        boolean hit = false;

        float sx = scale.x;
        float sy = scale.y;
        float sz = scale.z;

        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i] * 3;
            int i1 = indices[i + 1] * 3;
            int i2 = indices[i + 2] * 3;

            Vector3 v0 = new Vector3(verts[i0] * sx, verts[i0 + 1] * sy, verts[i0 + 2] * sz);
            Vector3 v1 = new Vector3(verts[i1] * sx, verts[i1 + 1] * sy, verts[i1 + 2] * sz);
            Vector3 v2 = new Vector3(verts[i2] * sx, verts[i2 + 1] * sy, verts[i2 + 2] * sz);

            double t = intersectTriangle(localOrigin, localDir, v0, v1, v2);
            if (t >= 0 && t < closestT) {
                closestT = t;
                hit = true;
            }
        }

        return hit && closestT <= maxDist ? closestT : -1;
    }

    private static double intersectTriangle(Vector3 orig, Vector3 dir, Vector3 v0, Vector3 v1, Vector3 v2) {
        final double EPSILON = 1e-7;
        Vector3 edge1 = v1.subtract(v0);
        Vector3 edge2 = v2.subtract(v0);
        Vector3 h = dir.cross(edge2);
        double a = edge1.dot(h);

        if (a > -EPSILON && a < EPSILON) return -1; // parallel to triangle

        double f = 1.0 / a;
        Vector3 s = orig.subtract(v0);
        double u = f * s.dot(h);

        if (u < 0.0 || u > 1.0) return -1;

        Vector3 q = s.cross(edge1);
        double v = f * dir.dot(q);

        if (v < 0.0 || u + v > 1.0) return -1;

        double t = f * edge2.dot(q);
        return t > EPSILON ? t : -1;
    }

    private static double raycastModelOBB(Vector3 origin, Vector3 direction, ModelProxy model, double maxDist) {
        List<OBB> boxes = model.getCollisionBoxes();
        if (boxes == null || boxes.isEmpty()) {
            Entity entity = model.getEntity();
            if (entity == null) return -1;
            return raycastToBoundingBox(
                    new Pos(origin.x, origin.y, origin.z),
                    new Vec(direction.x, direction.y, direction.z),
                    entity.getBoundingBox(),
                    entity.getPosition()
            );
        }

        Vector3 localOrigin = origin.subtract(model.getPosition());
        Quaternion invRot = model.getRotation().conjugate();
        localOrigin = invRot.rotate(localOrigin);
        Vector3 localDir = invRot.rotate(direction);

        Vector3 scale = model.getScale();
        if (scale.x != 0 && scale.y != 0 && scale.z != 0) {
            localOrigin = new Vector3(localOrigin.x / scale.x, localOrigin.y / scale.y, localOrigin.z / scale.z);
            localDir = new Vector3(localDir.x / scale.x, localDir.y / scale.y, localDir.z / scale.z);
        }

        double closest = Double.MAX_VALUE;
        boolean hit = false;

        for (OBB obb : boxes) {
            Vector3 obbRelOrigin = localOrigin.subtract(obb.center);
            Quaternion invObbRot = obb.rotation.conjugate();
            Vector3 rayStart = invObbRot.rotate(obbRelOrigin);
            Vector3 rayD = invObbRot.rotate(localDir);

            double t = intersectAABB(rayStart, rayD, obb.halfExtents);
            if (t >= 0 && t < closest) {
                closest = t;
                hit = true;
            }
        }

        if (!hit) return -1;

        double avgScale = (scale.x + scale.y + scale.z) / 3.0;
        double finalDist = closest * avgScale;

        return finalDist <= maxDist ? finalDist : -1;
    }

    private static double intersectAABB(Vector3 origin, Vector3 dir, Vector3 halfExtents) {
        double tMin = -Double.MAX_VALUE;
        double tMax = Double.MAX_VALUE;
        Vector3 min = halfExtents.multiply(-1);
        Vector3 max = halfExtents;

        // X Axis
        if (Math.abs(dir.x) < 1e-6) {
            if (origin.x < min.x || origin.x > max.x) return -1;
        } else {
            double invDir = 1.0 / dir.x;
            double t1 = (min.x - origin.x) * invDir;
            double t2 = (max.x - origin.x) * invDir;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // Y Axis
        if (Math.abs(dir.y) < 1e-6) {
            if (origin.y < min.y || origin.y > max.y) return -1;
        } else {
            double invDir = 1.0 / dir.y;
            double t1 = (min.y - origin.y) * invDir;
            double t2 = (max.y - origin.y) * invDir;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // Z Axis
        if (Math.abs(dir.z) < 1e-6) {
            if (origin.z < min.z || origin.z > max.z) return -1;
        } else {
            double invDir = 1.0 / dir.z;
            double t1 = (min.z - origin.z) * invDir;
            double t2 = (max.z - origin.z) * invDir;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        if (tMax < tMin || tMax < 0) return -1;
        return tMin > 0 ? tMin : tMax;
    }

    private static double raycastToBoundingBox(Point rayOrigin, Vec rayDirection, BoundingBox box, Pos boxPosition) {
        Point boxMin = boxPosition.add(box.minX(), box.minY(), box.minZ());
        Point boxMax = boxPosition.add(box.maxX(), box.maxY(), box.maxZ());

        double tmin = 0.0;
        double tmax = Double.POSITIVE_INFINITY;

        for (int i = 0; i < 3; i++) {
            double originVal = i == 0 ? rayOrigin.x() : (i == 1 ? rayOrigin.y() : rayOrigin.z());
            double dirVal = i == 0 ? rayDirection.x() : (i == 1 ? rayDirection.y() : rayDirection.z());
            double minVal = i == 0 ? boxMin.x() : (i == 1 ? boxMin.y() : boxMin.z());
            double maxVal = i == 0 ? boxMax.x() : (i == 1 ? boxMax.y() : boxMax.z());

            if (Math.abs(dirVal) < 1e-6) {
                if (originVal < minVal || originVal > maxVal) return -1;
            } else {
                double ood = 1.0 / dirVal;
                double t1 = (minVal - originVal) * ood;
                double t2 = (maxVal - originVal) * ood;
                tmin = Math.max(tmin, Math.min(t1, t2));
                tmax = Math.min(tmax, Math.max(t1, t2));
                if (tmin > tmax) return -1;
            }
        }
        return tmin;
    }

    private static Vector3 calculateBlockNormal(Point lastPos, Point currentPos) {
        if (currentPos.blockX() > lastPos.blockX()) return Vector3.left();
        if (currentPos.blockX() < lastPos.blockX()) return Vector3.right();
        if (currentPos.blockY() > lastPos.blockY()) return Vector3.down();
        if (currentPos.blockY() < lastPos.blockY()) return Vector3.up();
        if (currentPos.blockZ() > lastPos.blockZ()) return Vector3.backward();
        if (currentPos.blockZ() < lastPos.blockZ()) return Vector3.forward();
        return Vector3.up();
    }
}