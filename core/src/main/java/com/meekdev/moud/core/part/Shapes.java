package com.meekdev.moud.core.part;

import com.meekdev.moud.core.math.Aabb;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public final class Shapes {

    public record Plane(Vector3 normal, double distance) {}

    public record Hit(double distance, Vector3 normal) {}

    public static final int SIDES = 16;
    public static final int RINGS = 8;
    public static final int COLLIDING_SIDES = 8;
    public static final int COLLIDING_RINGS = 4;

    private static final double TINY = 1.0e-12;

    private Shapes() {}

    public static Vector3 drawScale(PartShape shape, Vector3 size) {
        double across = across(shape, size) * 2;
        return switch (shape) {
            case BALL -> new Vector3(across, across, across);
            case CYLINDER -> new Vector3(size.x(), across, across);
            default -> size;
        };
    }

    public static double across(PartShape shape, Vector3 size) {
        return switch (shape) {
            case BALL -> Math.min(size.x(), Math.min(size.y(), size.z())) * 0.5;
            case CYLINDER -> Math.min(size.y(), size.z()) * 0.5;
            default -> 0;
        };
    }

    public static Vector3 half(PartShape shape, Vector3 size) {
        Vector3 scale = drawScale(shape, size);
        return new Vector3(scale.x() * 0.5, scale.y() * 0.5, scale.z() * 0.5);
    }

    public static List<Plane> planes(PartShape shape, Vector3 size) {
        double hx = size.x() * 0.5;
        double hy = size.y() * 0.5;
        double hz = size.z() * 0.5;
        List<Plane> planes = new ArrayList<>(6);
        planes.add(new Plane(new Vector3(0, -1, 0), hy));
        switch (shape) {
            case WEDGE -> {
                planes.add(new Plane(Vector3.RIGHT, hx));
                planes.add(new Plane(Vector3.RIGHT.neg(), hx));
                planes.add(new Plane(new Vector3(0, 0, 1), hz));
                planes.add(new Plane(new Vector3(0, hz, -hy).normalize(), 0));
            }
            case CORNER_WEDGE -> {
                planes.add(new Plane(Vector3.RIGHT, hx));
                planes.add(new Plane(new Vector3(0, 0, -1), hz));
                planes.add(new Plane(new Vector3(0, hz, hy).normalize(), 0));
                planes.add(new Plane(new Vector3(-hy, hx, 0).normalize(), 0));
            }
            default -> {
                planes.add(new Plane(Vector3.RIGHT, hx));
                planes.add(new Plane(Vector3.RIGHT.neg(), hx));
                planes.add(new Plane(Vector3.UP, hy));
                planes.add(new Plane(new Vector3(0, 0, 1), hz));
                planes.add(new Plane(new Vector3(0, 0, -1), hz));
            }
        }
        return planes;
    }

    public static List<Vector3> faceNormals(PartShape shape, Vector3 size) {
        List<Vector3> normals = new ArrayList<>(6);
        if (shape == PartShape.BALL) return normals;
        if (shape == PartShape.CYLINDER) {
            normals.add(Vector3.RIGHT);
            for (int side = 0; side < SIDES / 2; side++) {
                double turn = Math.PI * side / (SIDES / 2);
                normals.add(new Vector3(0, Math.cos(turn), Math.sin(turn)));
            }
            return normals;
        }
        for (Plane plane : planes(shape, size)) normals.add(plane.normal());
        return normals;
    }

    public static List<Vector3> corners(PartShape shape, Vector3 size) {
        return corners(shape, size, SIDES, RINGS);
    }

    public static List<Vector3> corners(PartShape shape, Vector3 size, int sides, int rings) {
        double hx = size.x() * 0.5;
        double hy = size.y() * 0.5;
        double hz = size.z() * 0.5;
        List<Vector3> points = new ArrayList<>(8);
        switch (shape) {
            case BALL -> {
                double r = across(shape, size);
                for (int ring = 1; ring < rings; ring++) {
                    double pitch = Math.PI * ring / rings;
                    for (int side = 0; side < sides; side++) {
                        double turn = 2 * Math.PI * side / sides;
                        points.add(new Vector3(r * Math.sin(pitch) * Math.cos(turn), r * Math.cos(pitch),
                                r * Math.sin(pitch) * Math.sin(turn)));
                    }
                }
                points.add(new Vector3(0, r, 0));
                points.add(new Vector3(0, -r, 0));
            }
            case CYLINDER -> {
                double r = across(shape, size);
                for (int side = 0; side < sides; side++) {
                    double turn = 2 * Math.PI * side / sides;
                    double y = r * Math.cos(turn);
                    double z = r * Math.sin(turn);
                    points.add(new Vector3(-hx, y, z));
                    points.add(new Vector3(hx, y, z));
                }
            }
            case WEDGE -> {
                points.add(new Vector3(hx, hy, hz));
                points.add(new Vector3(-hx, hy, hz));
                points.add(new Vector3(hx, -hy, hz));
                points.add(new Vector3(-hx, -hy, hz));
                points.add(new Vector3(hx, -hy, -hz));
                points.add(new Vector3(-hx, -hy, -hz));
            }
            case CORNER_WEDGE -> {
                points.add(new Vector3(hx, hy, -hz));
                points.add(new Vector3(-hx, -hy, -hz));
                points.add(new Vector3(hx, -hy, -hz));
                points.add(new Vector3(hx, -hy, hz));
                points.add(new Vector3(-hx, -hy, hz));
            }
            default -> {
                for (int corner = 0; corner < 8; corner++) {
                    points.add(new Vector3((corner & 1) == 0 ? -hx : hx,
                            (corner & 2) == 0 ? -hy : hy, (corner & 4) == 0 ? -hz : hz));
                }
            }
        }
        return points;
    }

    public static boolean contains(PartShape shape, Vector3 size, Vector3 point, double margin) {
        switch (shape) {
            case BALL -> {
                double r = across(shape, size) + margin;
                return point.lengthSq() <= r * r;
            }
            case CYLINDER -> {
                double r = across(shape, size) + margin;
                return Math.abs(point.x()) <= size.x() * 0.5 + margin
                        && point.y() * point.y() + point.z() * point.z() <= r * r;
            }
            default -> {
                for (Plane plane : planes(shape, size)) {
                    if (plane.normal().dot(point) > plane.distance() + margin) return false;
                }
                return true;
            }
        }
    }

    public static Vector3 closest(PartShape shape, Vector3 size, Vector3 point) {
        switch (shape) {
            case BALL -> {
                double r = across(shape, size);
                double length = point.length();
                return length <= r ? point : point.mul(r / Math.max(length, TINY));
            }
            case CYLINDER -> {
                double r = across(shape, size);
                double hx = size.x() * 0.5;
                double flat = Math.hypot(point.y(), point.z());
                double scale = flat <= r ? 1 : r / Math.max(flat, TINY);
                return new Vector3(Math.clamp(point.x(), -hx, hx), point.y() * scale, point.z() * scale);
            }
            case BLOCK -> {
                return new Vector3(Math.clamp(point.x(), -size.x() * 0.5, size.x() * 0.5),
                        Math.clamp(point.y(), -size.y() * 0.5, size.y() * 0.5),
                        Math.clamp(point.z(), -size.z() * 0.5, size.z() * 0.5));
            }
            default -> {
                if (contains(shape, size, point, 0)) return point;
                Vector3 best = null;
                double nearest = Double.MAX_VALUE;
                for (Vector3[] face : triangles(shape, size)) {
                    Vector3 on = onTriangle(face[0], face[1], face[2], point);
                    double away = on.sub(point).lengthSq();
                    if (away < nearest) {
                        nearest = away;
                        best = on;
                    }
                }
                return best == null ? point : best;
            }
        }
    }

    public static Hit rayHit(PartShape shape, Vector3 size, Vector3 from, Vector3 way,
                                       double range, double grow) {
        switch (shape) {
            case BALL -> {
                return roundHit(from, way, range, across(shape, size) + grow, false, 0);
            }
            case CYLINDER -> {
                return roundHit(from, way, range, across(shape, size) + grow, true, size.x() * 0.5 + grow);
            }
            default -> {
                double near = 0;
                double far = range;
                Vector3 face = null;
                for (Plane plane : planes(shape, size)) {
                    double gap = plane.normal().dot(from) - plane.distance() - grow;
                    double speed = plane.normal().dot(way);
                    if (Math.abs(speed) < TINY) {
                        if (gap > 0) return null;
                        continue;
                    }
                    double when = -gap / speed;
                    if (speed > 0) {
                        far = Math.min(far, when);
                    } else if (when > near) {
                        near = when;
                        face = plane.normal();
                    }
                    if (near > far) return null;
                }
                return face == null ? null : new Hit(near, face);
            }
        }
    }

    private static Hit roundHit(Vector3 from, Vector3 way, double range, double radius,
                                          boolean alongX, double half) {
        double ox = alongX ? 0 : from.x();
        double dx = alongX ? 0 : way.x();
        double a = dx * dx + way.y() * way.y() + way.z() * way.z();
        double b = 2 * (ox * dx + from.y() * way.y() + from.z() * way.z());
        double c = ox * ox + from.y() * from.y() + from.z() * from.z() - radius * radius;
        double near = 0;
        double far = range;
        Vector3 face = null;
        if (a < TINY) {
            if (c > 0) return null;
        } else {
            double discriminant = b * b - 4 * a * c;
            if (discriminant < 0) return null;
            double root = Math.sqrt(discriminant);
            double one = (-b - root) / (2 * a);
            double two = (-b + root) / (2 * a);
            if (one > near) {
                near = one;
                Vector3 at = from.add(way.mul(one));
                face = new Vector3(alongX ? 0 : at.x(), at.y(), at.z()).normalize();
            }
            far = Math.min(far, two);
        }
        if (alongX) {
            double speed = way.x();
            if (Math.abs(speed) < TINY) {
                if (Math.abs(from.x()) > half) return null;
            } else {
                double one = (-half - from.x()) / speed;
                double two = (half - from.x()) / speed;
                double sign = speed > 0 ? -1 : 1;
                if (one > two) {
                    double swap = one;
                    one = two;
                    two = swap;
                }
                if (one > near) {
                    near = one;
                    face = new Vector3(sign, 0, 0);
                }
                far = Math.min(far, two);
            }
        }
        return face == null || near > far ? null : new Hit(near, face);
    }

    public static List<Aabb> slabs(PartShape shape, Vector3 size, int count) {
        List<Aabb> boxes = new ArrayList<>(count);
        if (shape == PartShape.BLOCK) {
            boxes.add(Aabb.around(Vector3.ZERO, size));
            return boxes;
        }
        double top = shape == PartShape.BALL || shape == PartShape.CYLINDER
                ? across(shape, size) : size.y() * 0.5;
        double bottom = -top;
        double step = (top - bottom) / count;
        for (int slab = 0; slab < count; slab++) {
            double low = bottom + step * slab;
            double high = low + step;
            double[] under = section(shape, size, low);
            double[] over = section(shape, size, high);
            if (under == null && over == null) continue;
            double[] span = under == null ? over : over == null ? under : new double[] {
                Math.min(under[0], over[0]), Math.max(under[1], over[1]),
                Math.min(under[2], over[2]), Math.max(under[3], over[3])};
            boxes.add(new Aabb(span[0], low, span[2], span[1], high, span[3]));
        }
        return boxes;
    }

    public static double[] section(PartShape shape, Vector3 size, double y) {
        double hx = size.x() * 0.5;
        double hy = size.y() * 0.5;
        double hz = size.z() * 0.5;
        switch (shape) {
            case BALL -> {
                double r = across(shape, size);
                double flat = r * r - y * y;
                if (flat <= 0) return null;
                double wide = Math.sqrt(flat);
                return new double[] {-wide, wide, -wide, wide};
            }
            case CYLINDER -> {
                double r = across(shape, size);
                double flat = r * r - y * y;
                if (flat <= 0) return null;
                double wide = Math.sqrt(flat);
                return new double[] {-hx, hx, -wide, wide};
            }
            case WEDGE -> {
                if (y < -hy || y > hy) return null;
                return new double[] {-hx, hx, hz * (hy < TINY ? 1 : y / hy), hz};
            }
            case CORNER_WEDGE -> {
                if (y < -hy || y > hy) return null;
                double up = hy < TINY ? 1 : (y + hy) / (2 * hy);
                return new double[] {-hx + 2 * hx * up, hx, -hz, hz - 2 * hz * up};
            }
            default -> {
                if (y < -hy || y > hy) return null;
                return new double[] {-hx, hx, -hz, hz};
            }
        }
    }

    public static List<Vector3[]> triangles(PartShape shape, Vector3 size) {
        return triangles(shape, size, SIDES, RINGS);
    }

    public static List<Vector3[]> triangles(PartShape shape, Vector3 size, int sides, int rings) {
        double hx = size.x() * 0.5;
        double hy = size.y() * 0.5;
        double hz = size.z() * 0.5;
        List<Vector3[]> faces = new ArrayList<>();
        switch (shape) {
            case BALL -> ball(faces, across(shape, size), sides, rings);
            case CYLINDER -> cylinder(faces, hx, across(shape, size), sides);
            case WEDGE -> {
                Vector3 t1 = new Vector3(hx, hy, hz);
                Vector3 t2 = new Vector3(-hx, hy, hz);
                Vector3 b1 = new Vector3(hx, -hy, hz);
                Vector3 b2 = new Vector3(-hx, -hy, hz);
                Vector3 f1 = new Vector3(hx, -hy, -hz);
                Vector3 f2 = new Vector3(-hx, -hy, -hz);
                face(faces, f2, f1, b1);
                face(faces, f2, b1, b2);
                face(faces, b2, b1, t1);
                face(faces, b2, t1, t2);
                face(faces, t1, f1, f2);
                face(faces, t1, f2, t2);
                face(faces, t1, b1, f1);
                face(faces, t2, f2, b2);
            }
            case CORNER_WEDGE -> {
                Vector3 peak = new Vector3(hx, hy, -hz);
                Vector3 a = new Vector3(-hx, -hy, -hz);
                Vector3 b = new Vector3(hx, -hy, -hz);
                Vector3 c = new Vector3(hx, -hy, hz);
                Vector3 d = new Vector3(-hx, -hy, hz);
                face(faces, a, b, c);
                face(faces, a, c, d);
                face(faces, peak, c, b);
                face(faces, peak, b, a);
                face(faces, peak, d, c);
                face(faces, peak, a, d);
            }
            default -> {
                Vector3 a = new Vector3(-hx, -hy, -hz);
                Vector3 b = new Vector3(hx, -hy, -hz);
                Vector3 c = new Vector3(hx, -hy, hz);
                Vector3 d = new Vector3(-hx, -hy, hz);
                Vector3 e = new Vector3(-hx, hy, -hz);
                Vector3 f = new Vector3(hx, hy, -hz);
                Vector3 g = new Vector3(hx, hy, hz);
                Vector3 h = new Vector3(-hx, hy, hz);
                face(faces, a, b, c);
                face(faces, a, c, d);
                face(faces, e, h, g);
                face(faces, e, g, f);
                face(faces, a, e, f);
                face(faces, a, f, b);
                face(faces, d, c, g);
                face(faces, d, g, h);
                face(faces, b, f, g);
                face(faces, b, g, c);
                face(faces, a, d, h);
                face(faces, a, h, e);
            }
        }
        return faces;
    }

    private static void ball(List<Vector3[]> faces, double r, int sides, int rings) {
        for (int ring = 0; ring < rings; ring++) {
            double high = Math.PI * ring / rings;
            double low = Math.PI * (ring + 1) / rings;
            for (int side = 0; side < sides; side++) {
                double one = 2 * Math.PI * side / sides;
                double two = 2 * Math.PI * (side + 1) / sides;
                Vector3 topLeft = onBall(r, high, one);
                Vector3 topRight = onBall(r, high, two);
                Vector3 lowLeft = onBall(r, low, one);
                Vector3 lowRight = onBall(r, low, two);
                if (ring > 0) face(faces, topLeft, lowLeft, topRight);
                if (ring < rings - 1) face(faces, topRight, lowLeft, lowRight);
            }
        }
    }

    private static Vector3 onBall(double r, double pitch, double turn) {
        return new Vector3(r * Math.sin(pitch) * Math.sin(turn), r * Math.cos(pitch),
                r * Math.sin(pitch) * Math.cos(turn));
    }

    private static void cylinder(List<Vector3[]> faces, double hx, double r, int sides) {
        Vector3 right = new Vector3(hx, 0, 0);
        Vector3 left = new Vector3(-hx, 0, 0);
        for (int side = 0; side < sides; side++) {
            double one = 2 * Math.PI * side / sides;
            double two = 2 * Math.PI * (side + 1) / sides;
            Vector3 rightOne = new Vector3(hx, r * Math.cos(one), r * Math.sin(one));
            Vector3 rightTwo = new Vector3(hx, r * Math.cos(two), r * Math.sin(two));
            Vector3 leftOne = new Vector3(-hx, rightOne.y(), rightOne.z());
            Vector3 leftTwo = new Vector3(-hx, rightTwo.y(), rightTwo.z());
            face(faces, rightOne, leftOne, leftTwo);
            face(faces, rightOne, leftTwo, rightTwo);
            face(faces, right, rightOne, rightTwo);
            face(faces, left, leftTwo, leftOne);
        }
    }

    private static void face(List<Vector3[]> faces, Vector3 a, Vector3 b, Vector3 c) {
        faces.add(new Vector3[] {a, b, c});
    }

    private static Vector3 onTriangle(Vector3 a, Vector3 b, Vector3 c, Vector3 point) {
        Vector3 ab = b.sub(a);
        Vector3 ac = c.sub(a);
        Vector3 ap = point.sub(a);
        double d1 = ab.dot(ap);
        double d2 = ac.dot(ap);
        if (d1 <= 0 && d2 <= 0) return a;
        Vector3 bp = point.sub(b);
        double d3 = ab.dot(bp);
        double d4 = ac.dot(bp);
        if (d3 >= 0 && d4 <= d3) return b;
        double vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0) return a.add(ab.mul(d1 / (d1 - d3)));
        Vector3 cp = point.sub(c);
        double d5 = ab.dot(cp);
        double d6 = ac.dot(cp);
        if (d6 >= 0 && d5 <= d6) return c;
        double vb = d5 * d2 - d1 * d6;
        if (vb <= 0 && d2 >= 0 && d6 <= 0) return a.add(ac.mul(d2 / (d2 - d6)));
        double va = d3 * d6 - d5 * d4;
        if (va <= 0 && d4 - d3 >= 0 && d5 - d6 >= 0) {
            return b.add(c.sub(b).mul((d4 - d3) / (d4 - d3 + d5 - d6)));
        }
        double denominator = 1 / (va + vb + vc);
        return a.add(ab.mul(vb * denominator)).add(ac.mul(vc * denominator));
    }
}
