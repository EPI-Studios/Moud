package com.meekdev.moud.core.math;

public record CFrame(Vector3 position, Quat rotation) {

    public static final CFrame IDENTITY = new CFrame(Vector3.ZERO, Quat.IDENTITY);

    public static CFrame at(Vector3 position) {
        return new CFrame(position, Quat.IDENTITY);
    }

    public static CFrame at(double x, double y, double z) {
        return new CFrame(new Vector3(x, y, z), Quat.IDENTITY);
    }

    public static CFrame angles(double pitchX, double yawY, double rollZ) {
        return new CFrame(Vector3.ZERO, Quat.euler(pitchX, yawY, rollZ));
    }

    public static CFrame lookAt(Vector3 from, Vector3 to) {
        return lookAt(from, to, Vector3.UP);
    }

    public static CFrame lookAt(Vector3 from, Vector3 to, Vector3 up) {
        return new CFrame(from, Quat.lookAt(to.sub(from), up));
    }

    public CFrame mul(CFrame o) {
        return new CFrame(position.add(rotation.rotate(o.position)), rotation.mul(o.rotation).normalize());
    }

    public Vector3 pointToWorld(Vector3 local) {
        return position.add(rotation.rotate(local));
    }

    public Vector3 pointToObject(Vector3 world) {
        return rotation.inverse().rotate(world.sub(position));
    }

    public Vector3 vectorToWorld(Vector3 local) {
        return rotation.rotate(local);
    }

    public Vector3 vectorToObject(Vector3 world) {
        return rotation.inverse().rotate(world);
    }

    public CFrame inverse() {
        Quat r = rotation.inverse();
        return new CFrame(r.rotate(position).neg(), r);
    }

    public Vector3 lookVector() {
        return rotation.rotate(Vector3.FORWARD);
    }

    public double roll(Vector3 reference) {
        Vector3 look = lookVector();
        Vector3 flat = look.cross(reference);
        if (flat.lengthSq() < 1e-12) return 0;
        Vector3 level = flat.normalize().cross(look).normalize();
        Vector3 up = upVector();
        return Math.atan2(level.cross(up).dot(look), level.dot(up));
    }

    public Vector3 rightVector() {
        return rotation.rotate(Vector3.RIGHT);
    }

    public Vector3 upVector() {
        return rotation.rotate(Vector3.UP);
    }

    public CFrame withPosition(Vector3 p) {
        return new CFrame(p, rotation);
    }

    public CFrame withRotation(Quat r) {
        return new CFrame(position, r);
    }

    public CFrame lerp(CFrame o, double t) {
        return new CFrame(position.lerp(o.position, t), rotation.slerp(o.rotation, t));
    }
}
