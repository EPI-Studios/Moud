package com.meekdev.moud.core.image;

import com.meekdev.moud.core.math.Vector3;

final class Projector {

    record Spot(double u, double v, double strength) {}

    private final Vector3 position;
    private final Vector3 forward;
    private final Vector3 right;
    private final Vector3 up;
    private final Vector3 size;
    private final double fade;

    private Projector(Vector3 position, Vector3 forward, Vector3 right, Vector3 up, Vector3 size, double fade) {
        this.position = position;
        this.forward = forward;
        this.right = right;
        this.up = up;
        this.size = size;
        this.fade = fade;
    }

    static Projector of(Vector3 position, Vector3 direction, Vector3 up, Vector3 size, double fadeAngle) {
        Vector3 forward = direction.normalize();
        if (forward.lengthSq() == 0 || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) return null;
        Vector3 right = forward.cross(up).normalize();
        if (right.lengthSq() == 0) {
            Vector3 fallback = Math.abs(forward.y()) < 0.9 ? Vector3.UP : Vector3.RIGHT;
            right = forward.cross(fallback).normalize();
        }
        return new Projector(position, forward, right, right.cross(forward), size, Math.clamp(fadeAngle, 0, 90));
    }

    Spot spot(Vector3 point, Vector3 normal) {
        Vector3 offset = point.sub(position);
        double depth = offset.dot(forward);
        if (depth < 0 || depth > size.z()) return null;
        double u = offset.dot(right) / size.x() + 0.5;
        double v = 0.5 - offset.dot(up) / size.y();
        if (u < 0 || u >= 1 || v < 0 || v >= 1) return null;
        double facing = -normal.dot(forward);
        if (facing <= 0) return null;
        return new Spot(u, v, strength(facing));
    }

    private double strength(double facing) {
        double degrees = Math.toDegrees(Math.acos(Math.min(1, facing)));
        if (degrees <= fade) return 1;
        return Math.max(0, (90 - degrees) / (90 - fade));
    }
}
