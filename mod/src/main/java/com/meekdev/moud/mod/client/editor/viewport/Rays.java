package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.math.Vector3;

final class Rays {

    private Rays() {}

    static double alongLine(Vector3 rayOrigin, Vector3 rayDirection, Vector3 lineOrigin, Vector3 lineDirection) {
        Vector3 w = rayOrigin.sub(lineOrigin);
        double b = lineDirection.dot(rayDirection);
        double d = lineDirection.dot(w);
        double e = rayDirection.dot(w);
        double denominator = 1.0 - b * b;
        if (Math.abs(denominator) < 1.0e-6) return d;
        return (d - b * e) / denominator;
    }

    static double snap(double value, double step) {
        return step <= 0 ? value : Math.round(value / step) * step;
    }
}
