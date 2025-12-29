package com.moud.client.collision;

import java.util.List;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class MeshCollider {
    private static final int SWEEP_ITERATIONS = 8;
    private static final double EPSILON = 1.0e-7;
    private static final double SAT_EPS = 1.0e-4;
    private static final int PENETRATION_ITERATIONS = 8;
    private static final double MAX_DEPENETRATE_STEP = 0.15;
    private static final double STEP_DROP_EXTRA = 0.1;
    private static final double MIN_FLOOR_Y = 0.7;

    private MeshCollider() {}

    public static CollisionResult collideWithStepUp(Box box, Vec3d movement, CollisionMesh mesh, float stepHeight) {
        if (mesh != null && (mesh.getOffsetX() != 0 || mesh.getOffsetY() != 0 || mesh.getOffsetZ() != 0)) {
            box = box.offset(-mesh.getOffsetX(), -mesh.getOffsetY(), -mesh.getOffsetZ());
        }
        Box startBox = box;
        box = depenetrate(box, mesh);
        double depenetrateDx = ((box.minX + box.maxX) * 0.5) - ((startBox.minX + startBox.maxX) * 0.5);
        double depenetrateDy = box.minY - startBox.minY;
        double depenetrateDz = ((box.minZ + box.maxZ) * 0.5) - ((startBox.minZ + startBox.maxZ) * 0.5);

        double targetY = movement.y;
        SweepResult sweepY = collideAxis(box, targetY, Direction.Axis.Y, mesh);
        double allowedY = sweepY.allowedDist;

        Box boxAtBodyHeight = box.offset(0, allowedY, 0);

        double targetX = movement.x;
        double targetZ = movement.z;

        SweepResult sweepX = collideAxis(boxAtBodyHeight, targetX, Direction.Axis.X, mesh);
        double allowedX = sweepX.allowedDist;

        if (Math.abs(allowedX) < Math.abs(targetX) - EPSILON && sweepX.hitNormal != null) {
            double dot = targetX * sweepX.hitNormal.x + targetZ * sweepX.hitNormal.z;
            double slideZ = targetZ - sweepX.hitNormal.z * dot;
            targetZ = slideZ;
        }

        SweepResult sweepZ = collideAxis(boxAtBodyHeight.offset(allowedX, 0, 0), targetZ, Direction.Axis.Z, mesh);
        double allowedZ = sweepZ.allowedDist;

        boolean hitHoriz = Math.abs(allowedX - targetX) > EPSILON || Math.abs(allowedZ - targetZ) > EPSILON;
        boolean hitVert = Math.abs(allowedY - targetY) > EPSILON;
        Vec3d groundNormal = (hitVert && targetY < 0 && sweepY.hitNormal != null) ? sweepY.hitNormal : Vec3d.ZERO;

        if (!hitHoriz || stepHeight <= 0) {
            return new CollisionResult(
                    new Vec3d(allowedX + depenetrateDx, allowedY + depenetrateDy, allowedZ + depenetrateDz),
                    hitHoriz,
                    hitVert,
                    groundNormal,
                    0
            );
        }

        SweepResult liftCheck = collideAxis(boxAtBodyHeight, stepHeight, Direction.Axis.Y, mesh);
        double allowedLift = liftCheck.allowedDist;
        Box liftedBox = boxAtBodyHeight.offset(0, allowedLift, 0);

        SweepResult stepXRes = collideAxis(liftedBox, targetX, Direction.Axis.X, mesh);
        double stepX = stepXRes.allowedDist;
        SweepResult stepZRes = collideAxis(liftedBox.offset(stepX, 0, 0), targetZ, Direction.Axis.Z, mesh);
        double stepZ = stepZRes.allowedDist;
        Box forwardBox = liftedBox.offset(stepX, 0, stepZ);

        double desiredDrop = -allowedLift - STEP_DROP_EXTRA;
        SweepResult dropCheck = collideAxis(forwardBox, desiredDrop, Direction.Axis.Y, mesh);
        double stepDrop = dropCheck.allowedDist;

        boolean hitSomething = Math.abs(stepDrop - desiredDrop) > EPSILON;
        boolean landedOnFloor = hitSomething && dropCheck.hitNormal != null && dropCheck.hitNormal.y >= MIN_FLOOR_Y;
        boolean landed = desiredDrop < 0.0 && landedOnFloor;

        if (!landed) {
            return new CollisionResult(
                    new Vec3d(allowedX + depenetrateDx, allowedY + depenetrateDy, allowedZ + depenetrateDz),
                    hitHoriz,
                    false,
                    Vec3d.ZERO,
                    0
            );
        }

        Vec3d stepMovement = new Vec3d(stepX, allowedY + allowedLift + stepDrop, stepZ);

        Box testBox = box.offset(stepMovement.x - box.minX, stepMovement.y - box.minY, stepMovement.z - box.minZ);
        Box depenetratedBox = depenetrate(testBox, mesh);
        double safetyDistSq = new Vec3d(
                ((depenetratedBox.minX + depenetratedBox.maxX) * 0.5) - ((testBox.minX + testBox.maxX) * 0.5),
                depenetratedBox.minY - testBox.minY,
                ((depenetratedBox.minZ + depenetratedBox.maxZ) * 0.5) - ((testBox.minZ + testBox.maxZ) * 0.5)
        ).lengthSquared();

        if (safetyDistSq > 0.0025) {
            return new CollisionResult(
                    new Vec3d(allowedX + depenetrateDx, allowedY + depenetrateDy, allowedZ + depenetrateDz),
                    hitHoriz,
                    false,
                    Vec3d.ZERO,
                    0
            );
        }

        double distBaseSq = (allowedX * allowedX) + (allowedZ * allowedZ);
        double distStepSq = (stepX * stepX) + (stepZ * stepZ);

        if (distStepSq > distBaseSq + 1.0e-4 && distStepSq > 1.0e-4) {
            return new CollisionResult(
                    stepMovement.add(depenetrateDx, depenetrateDy, depenetrateDz),
                    true,
                    true,
                    Vec3d.ZERO,
                    0
            );
        }

        return new CollisionResult(
                new Vec3d(allowedX + depenetrateDx, allowedY + depenetrateDy, allowedZ + depenetrateDz),
                hitHoriz,
                hitVert,
                groundNormal,
                0
        );
    }

    private record SweepResult(double allowedDist, Vec3d hitNormal) {}

    private static SweepResult collideAxis(Box box, double value, Direction.Axis axis, CollisionMesh mesh) {
        if (Math.abs(value) < EPSILON) return new SweepResult(0.0, null);

        Vec3d movement = new Vec3d(
                axis == Direction.Axis.X ? value : 0,
                axis == Direction.Axis.Y ? value : 0,
                axis == Direction.Axis.Z ? value : 0
        );

        Box swept = box.union(box.offset(movement));
        List<Triangle> candidates = mesh.queryTriangles(swept.expand(0.01));

        double allowedDist = value;
        Vec3d hitNormal = null;

        for (Triangle tri : candidates) {
            if (tri == null) continue;
            if (tri.normal.dotProduct(movement) >= 0) continue;

            if (movement.y == 0.0) {
                if (tri.normal.y >= MIN_FLOOR_Y) continue;
                double triMaxY = Math.max(tri.v0.y, Math.max(tri.v1.y, tri.v2.y));
                if (triMaxY <= box.minY + 1.0e-4) continue;
            }

            double t = findContactTime(box, movement, tri);
            if (t < 1.0) {
                double hitDist = value * t;
                if (Math.abs(hitDist) < Math.abs(allowedDist)) {
                    allowedDist = hitDist - (Math.signum(value) * EPSILON);
                    hitNormal = tri.normal;
                }
            }
        }
        return new SweepResult(allowedDist, hitNormal);
    }

    private static double findContactTime(Box box, Vec3d movement, Triangle tri) {
        if (movement.x == 0.0 && movement.y == 0.0 && movement.z == 0.0) return 1.0;
        if (boxIntersectsTriangle(box, tri)) return 0.0;

        double low = 0.0;
        double high = 1.0;
        double prev = 0.0;
        boolean found = false;

        for (int i = 1; i <= SWEEP_ITERATIONS; i++) {
            double t = i / (double) SWEEP_ITERATIONS;
            if (boxIntersectsTriangle(box.offset(movement.multiply(t)), tri)) {
                low = prev;
                high = t;
                found = true;
                break;
            }
            prev = t;
        }

        if (!found) return 1.0;

        for (int i = 0; i < SWEEP_ITERATIONS; i++) {
            double mid = (low + high) * 0.5;
            if (boxIntersectsTriangle(box.offset(movement.multiply(mid)), tri)) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return high;
    }

    private static Box depenetrate(Box box, CollisionMesh mesh) {
        if (box == null || mesh == null) return box;

        Box current = box;
        for (int iter = 0; iter < PENETRATION_ITERATIONS; iter++) {
            List<Triangle> candidates = mesh.queryTriangles(current.expand(0.05));
            if (candidates.isEmpty()) break;

            Vec3d center = new Vec3d(
                    (current.minX + current.maxX) * 0.5,
                    (current.minY + current.maxY) * 0.5,
                    (current.minZ + current.maxZ) * 0.5
            );
            Vec3d halfExtents = new Vec3d(
                    (current.maxX - current.minX) * 0.5,
                    (current.maxY - current.minY) * 0.5,
                    (current.maxZ - current.minZ) * 0.5
            );

            Vec3d totalPush = Vec3d.ZERO;
            int count = 0;

            for (Triangle tri : candidates) {
                if (tri == null || tri.normal == null) continue;
                if (tri.bounds != null && !tri.bounds.intersects(current)) continue;
                if (!boxIntersectsTriangle(current, tri)) continue;

                Vec3d n = tri.normal;
                double nLenSq = n.lengthSquared();
                if (nLenSq < 1.0e-12) continue;

                Vec3d p = center.subtract(tri.v0);
                double dist = p.dotProduct(n);
                double r = halfExtents.x * Math.abs(n.x) + halfExtents.y * Math.abs(n.y) + halfExtents.z * Math.abs(n.z);
                double overlap = r - Math.abs(dist);
                if (overlap <= 0.0) continue;

                double sign = dist >= 0.0 ? 1.0 : -1.0;
                double push = overlap + EPSILON;

                double dy = n.y * sign * push;
                boolean isFloor = Math.abs(n.y) >= MIN_FLOOR_Y;
                if (!isFloor) dy = 0.0;

                Vec3d pushVec = new Vec3d(n.x * sign * push, dy, n.z * sign * push);
                totalPush = totalPush.add(pushVec);
                count++;
            }

            if (count == 0) break;

            Vec3d avgPush = totalPush.multiply(1.0 / count);
            double lenSq = avgPush.lengthSquared();
            if (lenSq < 1.0e-12) break;

            double len = Math.sqrt(lenSq);
            if (len > MAX_DEPENETRATE_STEP) {
                double scale = MAX_DEPENETRATE_STEP / len;
                avgPush = avgPush.multiply(scale);
            }
            current = current.offset(avgPush);
        }
        return current;
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