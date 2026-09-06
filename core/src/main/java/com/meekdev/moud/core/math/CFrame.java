package com.meekdev.moud.core.math;

// stored decomposed because replication wants a quaternion and interpolation wants slerp
public record CFrame(Vec3 position, Quat rotation) {

    public static final CFrame IDENTITY = new CFrame(Vec3.ZERO, Quat.IDENTITY);

    public static CFrame at(Vec3 position) {
        return new CFrame(position, Quat.IDENTITY);
    }

    public static CFrame at(double x, double y, double z) {
        return new CFrame(new Vec3(x, y, z), Quat.IDENTITY);
    }

    public static CFrame angles(double pitchX, double yawY, double rollZ) {
        return new CFrame(Vec3.ZERO, Quat.euler(pitchX, yawY, rollZ));
    }

    public static CFrame lookAt(Vec3 from, Vec3 to) {
        return lookAt(from, to, Vec3.UP);
    }

    public static CFrame lookAt(Vec3 from, Vec3 to, Vec3 up) {
        return new CFrame(from, Quat.lookAt(to.sub(from), up));
    }

    // this frame then the other one, so base.mul(offset) puts offset in base's space
    public CFrame mul(CFrame o) {
        return new CFrame(position.add(rotation.rotate(o.position)), rotation.mul(o.rotation).normalize());
    }

    public Vec3 pointToWorld(Vec3 local) {
        return position.add(rotation.rotate(local));
    }

    public Vec3 pointToObject(Vec3 world) {
        return rotation.inverse().rotate(world.sub(position));
    }

    public Vec3 vectorToWorld(Vec3 local) {
        return rotation.rotate(local);
    }

    public Vec3 vectorToObject(Vec3 world) {
        return rotation.inverse().rotate(world);
    }

    public CFrame inverse() {
        Quat r = rotation.inverse();
        return new CFrame(r.rotate(position).neg(), r);
    }

    public Vec3 lookVector() {
        return rotation.rotate(Vec3.FORWARD);
    }

    public Vec3 rightVector() {
        return rotation.rotate(Vec3.RIGHT);
    }

    public Vec3 upVector() {
        return rotation.rotate(Vec3.UP);
    }

    public CFrame withPosition(Vec3 p) {
        return new CFrame(p, rotation);
    }

    public CFrame withRotation(Quat r) {
        return new CFrame(position, r);
    }

    public CFrame lerp(CFrame o, double t) {
        return new CFrame(position.lerp(o.position, t), rotation.slerp(o.rotation, t));
    }
}
