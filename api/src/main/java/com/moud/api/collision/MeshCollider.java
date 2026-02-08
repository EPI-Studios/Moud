package com.moud.api.collision;

import com.moud.api.math.Vector3;

import java.util.List;


public final class MeshCollider {
    private static final int SWEEP_ITERATIONS = 8;
    private static final double EPSILON = 1.0e-7;
    private static final double SAT_EPS = 1.0e-4;
    private static final int PENETRATION_ITERATIONS = 8;
    private static final double MAX_DEPENETRATE_STEP = 0.15;
    private static final double STEP_DROP_EXTRA = 0.1;
    private static final double MIN_FLOOR_Y = 0.7;

    private MeshCollider() {
    }

    public static MeshCollisionResult collideWithStepUp(
            AABB box,
            Vector3 movement,
            CollisionMesh mesh,
            float stepHeight
    ) {
        if (mesh == null || movement == null || box == null) {
            return MeshCollisionResult.none(movement);
        }

        double ox = mesh.getOffsetX();
        double oy = mesh.getOffsetY();
        double oz = mesh.getOffsetZ();
        if (ox != 0 || oy != 0 || oz != 0) {
            box = box.moved(-ox, -oy, -oz);
        }

        AABB startBox = box;
        box = depenetrate(box, mesh);
        double depenetrateDx = box.centerX() - startBox.centerX();
        double depenetrateDy = box.minY() - startBox.minY();
        double depenetrateDz = box.centerZ() - startBox.centerZ();

        double targetY = movement.y;
        SweepResult sweepY = collideAxis(box, targetY, Axis.Y, mesh);
        double allowedY = sweepY.allowedDist;

        AABB boxAtBodyHeight = box.moved(0, allowedY, 0);

        double targetX = movement.x;
        double targetZ = movement.z;

        SweepResult sweepX = collideAxis(boxAtBodyHeight, targetX, Axis.X, mesh);
        double allowedX = sweepX.allowedDist;
        
        if (Math.abs(allowedX) < Math.abs(targetX) - EPSILON && sweepX.hitNormal != null) {
            double dot = targetX * sweepX.hitNormal.x + targetZ * sweepX.hitNormal.z;
            // double slideX = targetX - sweepX.hitNormal.x * dot;
            double slideZ = targetZ - sweepX.hitNormal.z * dot;
            targetZ = slideZ;
        }
        
        SweepResult sweepZ = collideAxis(boxAtBodyHeight.moved(allowedX, 0, 0), targetZ, Axis.Z, mesh);
        double allowedZ = sweepZ.allowedDist;

        boolean hitHoriz = Math.abs(allowedX - targetX) > EPSILON || Math.abs(allowedZ - targetZ) > EPSILON;
        boolean hitVert = Math.abs(allowedY - targetY) > EPSILON;
        Vector3 groundNormal = (hitVert && targetY < 0 && sweepY.hitNormal != null) ? sweepY.hitNormal : Vector3.zero();

        if (!hitHoriz || stepHeight <= 0) {
            return new MeshCollisionResult(
                    new Vector3(allowedX + depenetrateDx, allowedY + depenetrateDy, allowedZ + depenetrateDz),
                    hitHoriz,
                    hitVert,
                    groundNormal,
                    0
            );
        }

        SweepResult liftCheck = collideAxis(boxAtBodyHeight, stepHeight, Axis.Y, mesh);
        double allowedLift = liftCheck.allowedDist;
        AABB liftedBox = boxAtBodyHeight.moved(0, allowedLift, 0);

        SweepResult stepXRes = collideAxis(liftedBox, targetX, Axis.X, mesh);
        double stepX = stepXRes.allowedDist;
        SweepResult stepZRes = collideAxis(liftedBox.moved(stepX, 0, 0), targetZ, Axis.Z, mesh);
        double stepZ = stepZRes.allowedDist;
        
        AABB forwardBox = liftedBox.moved(stepX, 0, stepZ);

        double desiredDrop = -allowedLift - STEP_DROP_EXTRA;
        SweepResult dropCheck = collideAxis(forwardBox, desiredDrop, Axis.Y, mesh);
        double stepDrop = dropCheck.allowedDist;
        
        boolean hitSomething = Math.abs(stepDrop - desiredDrop) > EPSILON;
        boolean landedOnFloor = hitSomething && dropCheck.hitNormal != null && dropCheck.hitNormal.y >= MIN_FLOOR_Y;
        
        boolean landed = desiredDrop < 0.0 && landedOnFloor;
        
        if (!landed) {
            return new MeshCollisionResult(
                    new Vector3(allowedX + depenetrateDx, allowedY + depenetrateDy, allowedZ + depenetrateDz),
                    hitHoriz,
                    false,
                    Vector3.zero(),
                    0
            );
        }

        Vector3 stepMovement = new Vector3(stepX, allowedY + allowedLift + stepDrop, stepZ);

        AABB testBox = box.moved(stepMovement.x - box.minX(), stepMovement.y - box.minY(), stepMovement.z - box.minZ());
        AABB depenetratedBox = depenetrate(testBox, mesh);
        double safetyDistSq = (depenetratedBox.centerX() - testBox.centerX()) * (depenetratedBox.centerX() - testBox.centerX())
                + (depenetratedBox.minY() - testBox.minY()) * (depenetratedBox.minY() - testBox.minY())
                + (depenetratedBox.centerZ() - testBox.centerZ()) * (depenetratedBox.centerZ() - testBox.centerZ());
        
        if (safetyDistSq > 0.0025) {
             return new MeshCollisionResult(
                    new Vector3(allowedX + depenetrateDx, allowedY + depenetrateDy, allowedZ + depenetrateDz),
                    hitHoriz,
                    false,
                    Vector3.zero(),
                    0
            );
        }

        double distBaseSq = (allowedX * allowedX) + (allowedZ * allowedZ);
        double distStepSq = (stepX * stepX) + (stepZ * stepZ);

        if (distStepSq > distBaseSq + 1.0e-4 && distStepSq > 1.0e-4) {
            return new MeshCollisionResult(
                    new Vector3(stepMovement.x + depenetrateDx, stepMovement.y + depenetrateDy, stepMovement.z + depenetrateDz),
                    true,
                    true,
                    Vector3.zero(),
                    0
            );
        }

        return new MeshCollisionResult(
                new Vector3(allowedX + depenetrateDx, allowedY + depenetrateDy, allowedZ + depenetrateDz),
                hitHoriz,
                hitVert,
                groundNormal,
                0
        );
    }

    private record SweepResult(double allowedDist, Vector3 hitNormal) {}

    private static SweepResult collideAxis(AABB box, double value, Axis axis, CollisionMesh mesh) {
        if (Math.abs(value) < EPSILON) return new SweepResult(0.0, null);

        Vector3 movement = switch (axis) {
            case X -> new Vector3(value, 0, 0);
            case Y -> new Vector3(0, value, 0);
            case Z -> new Vector3(0, 0, value);
        };

        AABB swept = box.union(box.moved(movement.x, movement.y, movement.z)).expanded(0.01, 0.01, 0.01);
        List<Triangle> candidates = mesh.queryTriangles(swept);

        double allowedDist = value;
        Vector3 hitNormal = null;

        for (Triangle tri : candidates) {
            if (tri == null) continue;

            double dot = tri.normal.x * movement.x + tri.normal.y * movement.y + tri.normal.z * movement.z;
            if (dot >= 0) continue;

            if (movement.y == 0.0) {
                if (tri.normal.y >= MIN_FLOOR_Y) {
                    continue;
                }
                double triMaxY = Math.max(tri.v0.y, Math.max(tri.v1.y, tri.v2.y));
                if (triMaxY <= box.minY() + 1.0e-4) {
                    continue;
                }
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

    private static double findContactTime(AABB box, Vector3 movement, Triangle tri) {
        if (movement.x == 0.0 && movement.y == 0.0 && movement.z == 0.0) {
            return 1.0;
        }
        if (boxIntersectsTriangle(box, tri)) {
            return 0.0;
        }

        double low = 0.0;
        double high = 1.0;

        double prev = 0.0;
        boolean found = false;
        for (int i = 1; i <= SWEEP_ITERATIONS; i++) {
            double t = i / (double) SWEEP_ITERATIONS;
            AABB midBox = box.moved(movement.x * t, movement.y * t, movement.z * t);
            if (boxIntersectsTriangle(midBox, tri)) {
                low = prev;
                high = t;
                found = true;
                break;
            }
            prev = t;
        }

        if (!found) {
            return 1.0;
        }

        for (int i = 0; i < SWEEP_ITERATIONS; i++) {
            double mid = (low + high) * 0.5;
            AABB midBox = box.moved(movement.x * mid, movement.y * mid, movement.z * mid);
            if (boxIntersectsTriangle(midBox, tri)) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return high;
    }

    private static AABB depenetrate(AABB box, CollisionMesh mesh) {
        if (box == null || mesh == null) {
            return box;
        }

        AABB current = box;
        for (int iter = 0; iter < PENETRATION_ITERATIONS; iter++) {
            AABB region = current.expanded(0.05, 0.05, 0.05);
            List<Triangle> candidates = mesh.queryTriangles(region);
            if (candidates.isEmpty()) {
                break;
            }

            double cx = current.centerX();
            double cy = current.centerY();
            double cz = current.centerZ();
            double ex = current.width() * 0.5;
            double ey = current.height() * 0.5;
            double ez = current.depth() * 0.5;

            double totalDx = 0.0;
            double totalDy = 0.0;
            double totalDz = 0.0;
            int count = 0;

            for (Triangle tri : candidates) {
                if (tri == null || tri.normal == null) {
                    continue;
                }
                if (tri.bounds != null && !tri.bounds.intersects(current)) {
                    continue;
                }
                if (!boxIntersectsTriangle(current, tri)) {
                    continue;
                }

                double nx = tri.normal.x;
                double ny = tri.normal.y;
                double nz = tri.normal.z;
                double nLenSq = (nx * nx) + (ny * ny) + (nz * nz);
                if (nLenSq < 1.0e-12) {
                    continue;
                }

                double px = cx - tri.v0.x;
                double py = cy - tri.v0.y;
                double pz = cz - tri.v0.z;
                double dist = (px * nx) + (py * ny) + (pz * nz);
                double r = ex * Math.abs(nx) + ey * Math.abs(ny) + ez * Math.abs(nz);
                double overlap = r - Math.abs(dist);
                if (overlap <= 0.0) {
                    continue;
                }

                double sign = dist >= 0.0 ? 1.0 : -1.0;
                double push = overlap + EPSILON;
                

                double dy = ny * sign * push;
                boolean isFloor = Math.abs(ny) >= MIN_FLOOR_Y;
                if (!isFloor) {
                    dy = 0.0;
                }
                
                double dx = nx * sign * push;
                double dz = nz * sign * push;
                
                totalDx += dx;
                totalDy += dy;
                totalDz += dz;
                count++;
            }

            if (count == 0) {
                break;
            }
            
            double avgDx = totalDx / count;
            double avgDy = totalDy / count;
            double avgDz = totalDz / count;
            
            double lenSq = avgDx * avgDx + avgDy * avgDy + avgDz * avgDz;
            if (lenSq < 1.0e-12) {
                break;
            }
            
            double len = Math.sqrt(lenSq);
            if (len > MAX_DEPENETRATE_STEP) {
                double scale = MAX_DEPENETRATE_STEP / len;
                avgDx *= scale;
                avgDy *= scale;
                avgDz *= scale;
            }
            
            current = current.moved(avgDx, avgDy, avgDz);
        }

        return current;
    }

    public static boolean boxIntersectsTriangle(AABB box, Triangle tri) {
        double cx = (box.minX() + box.maxX()) * 0.5;
        double cy = (box.minY() + box.maxY()) * 0.5;
        double cz = (box.minZ() + box.maxZ()) * 0.5;
        double ex = (box.maxX() - box.minX()) * 0.5;
        double ey = (box.maxY() - box.minY()) * 0.5;
        double ez = (box.maxZ() - box.minZ()) * 0.5;

        // vertices relatives to center
        double v0x = tri.v0.x - cx, v0y = tri.v0.y - cy, v0z = tri.v0.z - cz;
        double v1x = tri.v1.x - cx, v1y = tri.v1.y - cy, v1z = tri.v1.z - cz;
        double v2x = tri.v2.x - cx, v2y = tri.v2.y - cy, v2z = tri.v2.z - cz;

        // edges
        double e0x = v1x - v0x, e0y = v1y - v0y, e0z = v1z - v0z;
        double e1x = v2x - v1x, e1y = v2y - v1y, e1z = v2z - v1z;
        double e2x = v0x - v2x, e2y = v0y - v2y, e2z = v0z - v2z;

        // ugly testing that should be refactored
        if (!axisTestX01(e0z, e0y, Math.abs(e0z), Math.abs(e0y), v0y, v0z, v2y, v2z, ey, ez)) return false;
        if (!axisTestY02(e0z, e0x, Math.abs(e0z), Math.abs(e0x), v0x, v0z, v2x, v2z, ex, ez)) return false;
        if (!axisTestZ12(e0y, e0x, Math.abs(e0y), Math.abs(e0x), v1x, v1y, v2x, v2y, ex, ey)) return false;
        if (!axisTestX01(e1z, e1y, Math.abs(e1z), Math.abs(e1y), v0y, v0z, v2y, v2z, ey, ez)) return false;
        if (!axisTestY02(e1z, e1x, Math.abs(e1z), Math.abs(e1x), v0x, v0z, v2x, v2z, ex, ez)) return false;
        if (!axisTestZ12(e1y, e1x, Math.abs(e1y), Math.abs(e1x), v0x, v0y, v1x, v1y, ex, ey)) return false;
        if (!axisTestX01(e2z, e2y, Math.abs(e2z), Math.abs(e2y), v1y, v1z, v0y, v0z, ey, ez)) return false;
        if (!axisTestY02(e2z, e2x, Math.abs(e2z), Math.abs(e2x), v1x, v1z, v0x, v0z, ex, ez)) return false;
        if (!axisTestZ12(e2y, e2x, Math.abs(e2y), Math.abs(e2x), v0x, v0y, v1x, v1y, ex, ey)) return false;

        if (!overlap(v0x, v1x, v2x, ex)) return false;
        if (!overlap(v0y, v1y, v2y, ey)) return false;
        if (!overlap(v0z, v1z, v2z, ez)) return false;

        double nx = tri.normal.x, ny = tri.normal.y, nz = tri.normal.z;
        double d0 = v0x * nx + v0y * ny + v0z * nz;
        double r = ex * Math.abs(nx) + ey * Math.abs(ny) + ez * Math.abs(nz);
        return !(Math.abs(d0) > r + SAT_EPS);
    }

    private static boolean overlap(double a, double b, double c, double extent) {
        double min = Math.min(a, Math.min(b, c));
        double max = Math.max(a, Math.max(b, c));
        return !(min > extent + SAT_EPS || max < -extent - SAT_EPS);
    }

    private static boolean axisTestX01(double a, double b, double fa, double fb,
                                       double v0y, double v0z, double v2y, double v2z,
                                       double ey, double ez) {
        double p0 = a * v0y - b * v0z;
        double p2 = a * v2y - b * v2z;
        double rad = fa * ey + fb * ez;
        return !(Math.min(p0, p2) > rad + SAT_EPS || Math.max(p0, p2) < -rad - SAT_EPS);
    }

    private static boolean axisTestY02(double a, double b, double fa, double fb,
                                       double v0x, double v0z, double v2x, double v2z,
                                       double ex, double ez) {
        double p0 = -a * v0x + b * v0z;
        double p2 = -a * v2x + b * v2z;
        double rad = fa * ex + fb * ez;
        return !(Math.min(p0, p2) > rad + SAT_EPS || Math.max(p0, p2) < -rad - SAT_EPS);
    }

    private static boolean axisTestZ12(double a, double b, double fa, double fb,
                                       double v0x, double v0y, double v1x, double v1y,
                                       double ex, double ey) {
        double p1 = a * v0x - b * v0y;
        double p2 = a * v1x - b * v1y;
        double rad = fa * ex + fb * ey;
        return !(Math.min(p1, p2) > rad + SAT_EPS || Math.max(p1, p2) < -rad - SAT_EPS);
    }

    private enum Axis {
        X, Y, Z
    }
}
