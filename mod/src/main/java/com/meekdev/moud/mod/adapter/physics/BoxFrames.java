package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.Vec3;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;

final class BoxFrames {

    private BoxFrames() {}

    static CFrame of(B3Body body) {
        var rotation = body.rotation();
        return new CFrame(vector(body.position()), new Quat(rotation.x(), rotation.y(), rotation.z(), rotation.s()));
    }

    static Vector3 vector(Vec3 v) {
        return new Vector3(v.x(), v.y(), v.z());
    }

    static com.meekdev.box3d.Quat quat(Quat q) {
        return new com.meekdev.box3d.Quat((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
    }

    static Vec3 vec(Vector3 v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}
