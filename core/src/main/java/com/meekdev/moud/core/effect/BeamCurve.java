package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;

public final class BeamCurve {

    private BeamCurve() {}

    public static Vector3[] points(CFrame from, CFrame to, double curve0, double curve1, int segments) {
        int steps = Math.max(1, segments);
        Vector3 p0 = from.position();
        Vector3 p3 = to.position();
        Vector3 p1 = p0.add(from.rightVector().mul(curve0));
        Vector3 p2 = p3.sub(to.rightVector().mul(curve1));
        boolean straight = curve0 == 0 && curve1 == 0;
        Vector3[] out = new Vector3[steps + 1];
        for (int n = 0; n <= steps; n++) {
            double t = n / (double) steps;
            out[n] = straight ? p0.lerp(p3, t) : at(p0, p1, p2, p3, t);
        }
        return out;
    }

    public static Vector3 at(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3, double t) {
        double u = 1 - t;
        double a = u * u * u;
        double b = 3 * u * u * t;
        double c = 3 * u * t * t;
        double d = t * t * t;
        return new Vector3(
                a * p0.x() + b * p1.x() + c * p2.x() + d * p3.x(),
                a * p0.y() + b * p1.y() + c * p2.y() + d * p3.y(),
                a * p0.z() + b * p1.z() + c * p2.z() + d * p3.z());
    }

    public static double[] distances(Vector3[] points) {
        double[] out = new double[points.length];
        for (int n = 1; n < points.length; n++) out[n] = out[n - 1] + points[n].distance(points[n - 1]);
        return out;
    }

    public static double textureV(TextureMode mode, double along, double total, double textureLength, double scroll) {
        double v = switch (mode) {
            case STRETCH -> total <= 0 ? 0 : along / total * textureLength;
            case WRAP, STATIC -> along / textureLength;
        };
        return v - scroll;
    }
}
