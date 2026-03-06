package com.moud.core.math;

public record Transform(Vec3 pos, Quat rot, Vec3 scale) {

    public static final Transform IDENTITY = new Transform(new Vec3(0, 0, 0), Quat.IDENTITY, new Vec3(1, 1, 1));

    public Transform compose(Transform child) {
        Vec3 scaled = new Vec3(
                child.pos.x() * scale.x(),
                child.pos.y() * scale.y(),
                child.pos.z() * scale.z()
        );
        Vec3 worldPos = pos.add(rot.rotate(scaled));
        Quat worldRot = rot.mul(child.rot).normalized();
        Vec3 worldScale = scale.mul(child.scale);
        return new Transform(worldPos, worldRot, worldScale);
    }
}
