package com.moud.client.collision;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class MeshCollider {
    private static final int SWEEP_ITERATIONS = 8;
    private static final double EPSILON = 1.0e-7;
    private static final double SAT_EPS = 1.0e-4;

    private MeshCollider() {
    }

    public static CollisionResult collideWithStepUp(Box box, Vec3d movement, CollisionMesh mesh, float stepHeight) {
        if (mesh != null && (mesh.getOffsetX() != 0 || mesh.getOffsetY() != 0 || mesh.getOffsetZ() != 0)) {
            box = box.offset(-mesh.getOffsetX(), -mesh.getOffsetY(), -mesh.getOffsetZ());
        }
        double targetY = movement.y;
        double allowedY = collideAxis(box, targetY, Direction.Axis.Y, mesh);

        Box boxAtBodyHeight = box.offset(0, allowedY, 0);

        double targetX = movement.x;
        double targetZ = movement.z;

        double allowedX = collideAxis(boxAtBodyHeight, targetX, Direction.Axis.X, mesh);
        double allowedZ = collideAxis(boxAtBodyHeight.offset(allowedX, 0, 0), targetZ, Direction.Axis.Z, mesh);

        boolean hitHoriz = Math.abs(allowedX - targetX) > EPSILON || Math.abs(allowedZ - targetZ) > EPSILON;

        if (!hitHoriz || stepHeight <= 0) {
            return new CollisionResult(new Vec3d(allowedX, allowedY, allowedZ), hitHoriz, false, Vec3d.ZERO, 0);
        }

        double allowedLift = collideAxis(boxAtBodyHeight, stepHeight, Direction.Axis.Y, mesh);
        Box liftedBox = boxAtBodyHeight.offset(0, allowedLift, 0);
        // TODO : fix that it lift too much like for 90° straight stuff it shouldn't

        double stepX = collideAxis(liftedBox, targetX, Direction.Axis.X, mesh);
        double stepZ = collideAxis(liftedBox.offset(stepX, 0, 0), targetZ, Direction.Axis.Z, mesh);
        Box forwardBox = liftedBox.offset(stepX, 0, stepZ);

        double stepDrop = collideAxis(forwardBox, -allowedLift - 0.1, Direction.Axis.Y, mesh);


        Vec3d stepMovement = new Vec3d(stepX, allowedY + allowedLift + stepDrop, stepZ);

        double distBaseSq = (allowedX * allowedX) + (allowedZ * allowedZ);
        double distStepSq = (stepX * stepX) + (stepZ * stepZ);

        if (distStepSq > distBaseSq) {
            return new CollisionResult(stepMovement, true, true, Vec3d.ZERO, 0);
        }

        return new CollisionResult(new Vec3d(allowedX, allowedY, allowedZ), hitHoriz, false, Vec3d.ZERO, 0);
    }

    private static double collideAxis(Box box, double value, Direction.Axis axis, CollisionMesh mesh) {
        if (Math.abs(value) < EPSILON) return 0.0;

        Vec3d movement = new Vec3d(
                axis == Direction.Axis.X ? value : 0,
                axis == Direction.Axis.Y ? value : 0,
                axis == Direction.Axis.Z ? value : 0
        );

        Box swept = box.union(box.offset(movement));
        List<Triangle> candidates = mesh.queryTriangles(swept.expand(0.01));

        double allowedDist = value;

        for (Triangle tri : candidates) {
            if (tri == null) continue;

            if (tri.normal.dotProduct(movement) >= 0) continue;

            // stat test
            if (!boxIntersectsTriangle(box.offset(movement), tri) &&
                    !boxIntersectsTriangle(box, tri)) {
                if (findContactTime(box, movement, tri) >= 1.0 - EPSILON) continue;
            }

            double t = findContactTime(box, movement, tri);

            if (t < 1.0) {
                double hitDist = value * t;
                if (Math.abs(hitDist) < Math.abs(allowedDist)) {
                    allowedDist = hitDist - (Math.signum(value) * EPSILON);
                }
            }
        }
        return allowedDist;
    }

    private static double findContactTime(Box box, Vec3d movement, Triangle tri) {
        double low = 0.0;
        double high = 1.0;
        if (!boxIntersectsTriangle(box.offset(movement), tri)) return 1.0;
        for (int i = 0; i < SWEEP_ITERATIONS; i++) {
            double mid = (low + high) * 0.5;
            if (boxIntersectsTriangle(box.offset(movement.multiply(mid)), tri)) high = mid;
            else low = mid;
        }
        return high;
    }

    public static boolean boxIntersectsTriangle(Box box, Triangle tri) {
        Vec3d c = new Vec3d((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
        Vec3d e = new Vec3d((box.maxX - box.minX) * 0.5, (box.maxY - box.minY) * 0.5, (box.maxZ - box.minZ) * 0.5);
        Vec3d v0 = tri.v0.subtract(c);
        Vec3d v1 = tri.v1.subtract(c);
        Vec3d v2 = tri.v2.subtract(c);
        Vec3d e0 = v1.subtract(v0);
        Vec3d e1 = v2.subtract(v1);
        Vec3d e2 = v0.subtract(v2);

        if (!axisTestX01(e0.z, e0.y, Math.abs(e0.z), Math.abs(e0.y), v0, v2, e)) return false;
        if (!axisTestY02(e0.z, e0.x, Math.abs(e0.z), Math.abs(e0.x), v0, v2, e)) return false;
        if (!axisTestZ12(e0.y, e0.x, Math.abs(e0.y), Math.abs(e0.x), v1, v2, e)) return false;
        if (!axisTestX01(e1.z, e1.y, Math.abs(e1.z), Math.abs(e1.y), v0, v2, e)) return false;
        if (!axisTestY02(e1.z, e1.x, Math.abs(e1.z), Math.abs(e1.x), v0, v2, e)) return false;
        if (!axisTestZ12(e1.y, e1.x, Math.abs(e1.y), Math.abs(e1.x), v0, v1, e)) return false;
        if (!axisTestX01(e2.z, e2.y, Math.abs(e2.z), Math.abs(e2.y), v1, v0, e)) return false;
        if (!axisTestY02(e2.z, e2.x, Math.abs(e2.z), Math.abs(e2.x), v1, v0, e)) return false;
        if (!axisTestZ12(e2.y, e2.x, Math.abs(e2.y), Math.abs(e2.x), v0, v1, e)) return false;

        if (!overlap(v0.x, v1.x, v2.x, e.x)) return false;
        if (!overlap(v0.y, v1.y, v2.y, e.y)) return false;
        if (!overlap(v0.z, v1.z, v2.z, e.z)) return false;

        Vec3d normal = e0.crossProduct(e1);
        double d0 = v0.dotProduct(normal);
        double r = e.x * Math.abs(normal.x) + e.y * Math.abs(normal.y) + e.z * Math.abs(normal.z);
        return !(Math.abs(d0) > r + SAT_EPS);
    }

    private static boolean overlap(double a, double b, double c, double extent) {
        double min = Math.min(a, Math.min(b, c));
        double max = Math.max(a, Math.max(b, c));
        return !(min > extent + SAT_EPS || max < -extent - SAT_EPS);
    }

    private static boolean axisTestX01(double a, double b, double fa, double fb, Vec3d v0, Vec3d v2, Vec3d e) {
        double p0 = a * v0.y - b * v0.z;
        double p2 = a * v2.y - b * v2.z;
        double rad = fa * e.y + fb * e.z;
        return !(Math.min(p0, p2) > rad + SAT_EPS || Math.max(p0, p2) < -rad - SAT_EPS);
    }

    private static boolean axisTestY02(double a, double b, double fa, double fb, Vec3d v0, Vec3d v2, Vec3d e) {
        double p0 = -a * v0.x + b * v0.z;
        double p2 = -a * v2.x + b * v2.z;
        double rad = fa * e.x + fb * e.z;
        return !(Math.min(p0, p2) > rad + SAT_EPS || Math.max(p0, p2) < -rad - SAT_EPS);
    }

    private static boolean axisTestZ12(double a, double b, double fa, double fb, Vec3d v0, Vec3d v1, Vec3d e) {
        double p1 = a * v0.x - b * v0.y;
        double p2 = a * v1.x - b * v1.y;
        double rad = fa * e.x + fb * e.y;
        return !(Math.min(p1, p2) > rad + SAT_EPS || Math.max(p1, p2) < -rad - SAT_EPS);
    }
}
