package com.moud.api.math;

import org.graalvm.polyglot.HostAccess;

/**
 * Utility class for common geometric calculations and operations
 */
public class GeometryUtils {

    @HostAccess.Export
    public static float distancePointToLine(Vector3 point, Vector3 lineStart, Vector3 lineEnd) {
        Vector3 line = lineEnd.subtract(lineStart);
        Vector3 pointToStart = point.subtract(lineStart);

        float lineLengthSquared = line.lengthSquared();
        if (lineLengthSquared < MathUtils.EPSILON) {
            return point.distance(lineStart);
        }

        float t = MathUtils.clamp(pointToStart.dot(line) / lineLengthSquared, 0.0f, 1.0f);
        Vector3 projection = lineStart.add(line.multiply(t));
        return point.distance(projection);
    }

    @HostAccess.Export
    public static Vector3 closestPointOnLine(Vector3 point, Vector3 lineStart, Vector3 lineEnd) {
        Vector3 line = lineEnd.subtract(lineStart);
        Vector3 pointToStart = point.subtract(lineStart);

        float lineLengthSquared = line.lengthSquared();
        if (lineLengthSquared < MathUtils.EPSILON) {
            return lineStart;
        }

        float t = MathUtils.clamp(pointToStart.dot(line) / lineLengthSquared, 0.0f, 1.0f);
        return lineStart.add(line.multiply(t));
    }

    @HostAccess.Export
    public static float distancePointToPlane(Vector3 point, Vector3 planePoint, Vector3 planeNormal) {
        Vector3 normalizedNormal = planeNormal.normalize();
        return normalizedNormal.dot(point.subtract(planePoint));
    }

    @HostAccess.Export
    public static Vector3 projectPointOnPlane(Vector3 point, Vector3 planePoint, Vector3 planeNormal) {
        Vector3 normalizedNormal = planeNormal.normalize();
        float distance = distancePointToPlane(point, planePoint, normalizedNormal);
        return point.subtract(normalizedNormal.multiply(distance));
    }

    @HostAccess.Export
    public static boolean rayPlaneIntersection(Vector3 rayOrigin, Vector3 rayDirection,
                                               Vector3 planePoint, Vector3 planeNormal) {
        Vector3 normalizedNormal = planeNormal.normalize();
        float denominator = rayDirection.dot(normalizedNormal);

        if (MathUtils.abs(denominator) < MathUtils.EPSILON) {
            return false; // Ray is parallel to plane
        }

        float t = planePoint.subtract(rayOrigin).dot(normalizedNormal) / denominator;
        return t >= 0.0f;
    }

    @HostAccess.Export
    public static Vector3 rayPlaneIntersectionPoint(Vector3 rayOrigin, Vector3 rayDirection,
                                                    Vector3 planePoint, Vector3 planeNormal) {
        Vector3 normalizedNormal = planeNormal.normalize();
        float denominator = rayDirection.dot(normalizedNormal);

        if (MathUtils.abs(denominator) < MathUtils.EPSILON) {
            return rayOrigin; // Ray is parallel to plane, return origin
        }

        float t = planePoint.subtract(rayOrigin).dot(normalizedNormal) / denominator;
        if (t < 0.0f) {
            return rayOrigin; // Intersection is behind the ray origin
        }

        return rayOrigin.add(rayDirection.multiply(t));
    }

    @HostAccess.Export
    public static boolean sphereIntersection(Vector3 center1, float radius1, Vector3 center2, float radius2) {
        float distance = center1.distance(center2);
        return distance <= (radius1 + radius2);
    }

    @HostAccess.Export
    public static boolean pointInSphere(Vector3 point, Vector3 sphereCenter, float sphereRadius) {
        return point.distanceSquared(sphereCenter) <= sphereRadius * sphereRadius;
    }

    @HostAccess.Export
    public static boolean pointInBox(Vector3 point, Vector3 boxMin, Vector3 boxMax) {
        return point.x >= boxMin.x && point.x <= boxMax.x &&
                point.y >= boxMin.y && point.y <= boxMax.y &&
                point.z >= boxMin.z && point.z <= boxMax.z;
    }

    @HostAccess.Export
    public static Vector3 barycentric(Vector3 point, Vector3 a, Vector3 b, Vector3 c) {
        Vector3 v0 = c.subtract(a);
        Vector3 v1 = b.subtract(a);
        Vector3 v2 = point.subtract(a);

        float dot00 = v0.dot(v0);
        float dot01 = v0.dot(v1);
        float dot02 = v0.dot(v2);
        float dot11 = v1.dot(v1);
        float dot12 = v1.dot(v2);

        float invDenom = 1.0f / (dot00 * dot11 - dot01 * dot01);
        float u = (dot11 * dot02 - dot01 * dot12) * invDenom;
        float v = (dot00 * dot12 - dot01 * dot02) * invDenom;

        return new Vector3(1.0f - u - v, v, u);
    }

    @HostAccess.Export
    public static boolean pointInTriangle(Vector3 point, Vector3 a, Vector3 b, Vector3 c) {
        Vector3 bary = barycentric(point, a, b, c);
        return bary.x >= 0.0f && bary.y >= 0.0f && bary.z >= 0.0f &&
                bary.x <= 1.0f && bary.y <= 1.0f && bary.z <= 1.0f;
    }

    @HostAccess.Export
    public static Vector3 triangleNormal(Vector3 a, Vector3 b, Vector3 c) {
        Vector3 ab = b.subtract(a);
        Vector3 ac = c.subtract(a);
        return ab.cross(ac).normalize();
    }

    @HostAccess.Export
    public static float triangleArea(Vector3 a, Vector3 b, Vector3 c) {
        Vector3 ab = b.subtract(a);
        Vector3 ac = c.subtract(a);
        return ab.cross(ac).length() * 0.5f;
    }

    @HostAccess.Export
    public static Vector3 reflect(Vector3 incident, Vector3 normal) {
        return incident.subtract(normal.multiply(2.0f * incident.dot(normal)));
    }

    @HostAccess.Export
    public static Vector3 refract(Vector3 incident, Vector3 normal, float eta) {
        float cosI = -normal.dot(incident);
        float sinT2 = eta * eta * (1.0f - cosI * cosI);

        if (sinT2 > 1.0f) {
            return Vector3.zero(); // Total internal reflection
        }

        float cosT = MathUtils.sqrt(1.0f - sinT2);
        return incident.multiply(eta).add(normal.multiply(eta * cosI - cosT));
    }

    @HostAccess.Export
    public static float fresnel(Vector3 incident, Vector3 normal, float n1, float n2) {
        float eta = n1 / n2;
        float cosI = -normal.dot(incident);
        float sinT2 = eta * eta * (1.0f - cosI * cosI);

        if (sinT2 > 1.0f) {
            return 1.0f; // Total internal reflection
        }

        float cosT = MathUtils.sqrt(1.0f - sinT2);
        float rPerp = (n1 * cosI - n2 * cosT) / (n1 * cosI + n2 * cosT);
        float rPar = (n2 * cosI - n1 * cosT) / (n2 * cosI + n1 * cosT);

        return (rPerp * rPerp + rPar * rPar) * 0.5f;
    }

    @HostAccess.Export
    public static Vector3 bezierCubic(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3, float t) {
        float u = 1.0f - t;
        float tt = t * t;
        float uu = u * u;
        float uuu = uu * u;
        float ttt = tt * t;

        Vector3 point = p0.multiply(uuu);
        point = point.add(p1.multiply(3.0f * uu * t));
        point = point.add(p2.multiply(3.0f * u * tt));
        point = point.add(p3.multiply(ttt));

        return point;
    }

    @HostAccess.Export
    public static Vector3 bezierQuadratic(Vector3 p0, Vector3 p1, Vector3 p2, float t) {
        float u = 1.0f - t;
        float uu = u * u;
        float tt = t * t;

        Vector3 point = p0.multiply(uu);
        point = point.add(p1.multiply(2.0f * u * t));
        point = point.add(p2.multiply(tt));

        return point;
    }

    @HostAccess.Export
    public static Vector3 catmullRom(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3, float t) {
        float tt = t * t;
        float ttt = tt * t;

        Vector3 c0 = p1;
        Vector3 c1 = p2.subtract(p0).multiply(0.5f);
        Vector3 c2 = p0.multiply(2.0f).subtract(p1.multiply(5.0f)).add(p2.multiply(4.0f)).subtract(p3).multiply(0.5f);
        Vector3 c3 = p1.multiply(3.0f).subtract(p0).subtract(p2.multiply(3.0f)).add(p3).multiply(0.5f);

        return c0.add(c1.multiply(t)).add(c2.multiply(tt)).add(c3.multiply(ttt));
    }

    @HostAccess.Export
    public static float signedVolumeOfTetrahedron(Vector3 a, Vector3 b, Vector3 c, Vector3 d) {
        return b.subtract(a).dot(c.subtract(a).cross(d.subtract(a))) / 6.0f;
    }

    @HostAccess.Export
    public static boolean sameSide(Vector3 p1, Vector3 p2, Vector3 a, Vector3 b) {
        Vector3 ab = b.subtract(a);
        Vector3 cp1 = ab.cross(p1.subtract(a));
        Vector3 cp2 = ab.cross(p2.subtract(a));
        return cp1.dot(cp2) >= 0.0f;
    }

    @HostAccess.Export
    public static Vector3 circumcenter(Vector3 a, Vector3 b, Vector3 c) {
        Vector3 ab = b.subtract(a);
        Vector3 ac = c.subtract(a);
        Vector3 abXac = ab.cross(ac);

        if (abXac.lengthSquared() < MathUtils.EPSILON) {
            return a; // Points are collinear
        }

        Vector3 toCircumcenter = ab.cross(abXac).multiply(ac.lengthSquared())
                .add(abXac.cross(ac).multiply(ab.lengthSquared()))
                .divide(2.0f * abXac.lengthSquared());

        return a.add(toCircumcenter);
    }

    @HostAccess.Export
    public static float circumradius(Vector3 a, Vector3 b, Vector3 c) {
        Vector3 center = circumcenter(a, b, c);
        return center.distance(a);
    }

    @HostAccess.Export
    public static Vector3 incenter(Vector3 a, Vector3 b, Vector3 c) {
        float sideA = b.distance(c);
        float sideB = a.distance(c);
        float sideC = a.distance(b);
        float perimeter = sideA + sideB + sideC;

        if (perimeter < MathUtils.EPSILON) {
            return a; // Degenerate triangle
        }

        return a.multiply(sideA / perimeter)
                .add(b.multiply(sideB / perimeter))
                .add(c.multiply(sideC / perimeter));
    }

    @HostAccess.Export
    public static float inradius(Vector3 a, Vector3 b, Vector3 c) {
        float area = triangleArea(a, b, c);
        float sideA = b.distance(c);
        float sideB = a.distance(c);
        float sideC = a.distance(b);
        float semiperimeter = (sideA + sideB + sideC) * 0.5f;

        if (semiperimeter < MathUtils.EPSILON) {
            return 0.0f;
        }

        return area / semiperimeter;
    }

    @HostAccess.Export
    public static Vector3[] generateSpherePoints(int count) {
        Vector3[] points = new Vector3[count];
        float goldenRatio = (1.0f + MathUtils.sqrt(5.0f)) * 0.5f;
        float angleIncrement = MathUtils.TWO_PI * goldenRatio;

        for (int i = 0; i < count; i++) {
            float t = (float) i / count;
            float inclination = MathUtils.acos(1.0f - 2.0f * t);
            float azimuth = angleIncrement * i;

            float x = MathUtils.sin(inclination) * MathUtils.cos(azimuth);
            float y = MathUtils.sin(inclination) * MathUtils.sin(azimuth);
            float z = MathUtils.cos(inclination);

            points[i] = new Vector3(x, y, z);
        }

        return points;
    }

    @HostAccess.Export
    public static Vector3 sphericalToCartesian(float radius, float theta, float phi) {
        float x = radius * MathUtils.sin(phi) * MathUtils.cos(theta);
        float y = radius * MathUtils.cos(phi);
        float z = radius * MathUtils.sin(phi) * MathUtils.sin(theta);
        return new Vector3(x, y, z);
    }

    @HostAccess.Export
    public static Vector3 cartesianToSpherical(Vector3 point) {
        float radius = point.length();
        float theta = MathUtils.atan2(point.z, point.x);
        float phi = MathUtils.acos(point.y / radius);
        return new Vector3(radius, theta, phi);
    }

    @HostAccess.Export
    public static Vector3 cylindricalToCartesian(float radius, float theta, float height) {
        float x = radius * MathUtils.cos(theta);
        float y = height;
        float z = radius * MathUtils.sin(theta);
        return new Vector3(x, y, z);
    }

    @HostAccess.Export
    public static Vector3 cartesianToCylindrical(Vector3 point) {
        float radius = MathUtils.sqrt(point.x * point.x + point.z * point.z);
        float theta = MathUtils.atan2(point.z, point.x);
        return new Vector3(radius, theta, point.y);
    }
}