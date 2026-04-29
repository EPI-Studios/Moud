package com.moud.client.fabric.physics.rapier;

import com.moud.physics.api.Quat;
import com.moud.physics.api.Transform;
import com.moud.physics.api.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class BodyInterpolator {

    private static final long INTERP_DELAY_MS = 16L;
    private static final long EXTRAPOLATE_FREEZE_MS = 200L;

    private static final class Entry {
        Transform prev;
        Transform curr;
        Vec3 currVel;
        long prevRecvMs;
        long currRecvMs;
        boolean snap;
    }

    private final Map<Long, Entry> entries = new HashMap<>();

    public void onSnapshot(long id, Transform t, Vec3 vel, long recvMs, boolean teleport) {
        Entry e = entries.computeIfAbsent(id, k -> new Entry());
        e.prev = e.curr;
        e.prevRecvMs = e.currRecvMs;
        e.curr = t;
        e.currVel = vel;
        e.currRecvMs = recvMs;
        e.snap = teleport;
        if (e.prev == null) e.prev = t;
    }

    public Optional<Transform> sample(long id, long renderTimeMs) {
        Entry e = entries.get(id);
        if (e == null) return Optional.empty();
        if (e.snap) return Optional.of(e.curr);
        long t = renderTimeMs - INTERP_DELAY_MS;
        if (t >= e.currRecvMs + EXTRAPOLATE_FREEZE_MS) return Optional.of(e.curr);
        if (e.currRecvMs == e.prevRecvMs) return Optional.of(e.curr);
        float a = (float) (t - e.prevRecvMs) / (float) (e.currRecvMs - e.prevRecvMs);
        a = Math.max(0f, Math.min(1f, a));
        return Optional.of(lerp(e.prev, e.curr, a));
    }

    public void remove(long id) {
        entries.remove(id);
    }

    public void clear() {
        entries.clear();
    }

    private static Transform lerp(Transform a, Transform b, float t) {
        Vec3 p = new Vec3(
                a.pos().x() + (b.pos().x() - a.pos().x()) * t,
                a.pos().y() + (b.pos().y() - a.pos().y()) * t,
                a.pos().z() + (b.pos().z() - a.pos().z()) * t);
        Quat q = slerp(a.rot(), b.rot(), t);
        return new Transform(p, q);
    }

    private static Quat slerp(Quat a, Quat b, float t) {
        float dot = a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w();
        float bx = b.x(), by = b.y(), bz = b.z(), bw = b.w();
        if (dot < 0f) { dot = -dot; bx = -bx; by = -by; bz = -bz; bw = -bw; }
        if (dot > 0.9995f) {
            float x = a.x() + (bx - a.x()) * t;
            float y = a.y() + (by - a.y()) * t;
            float z = a.z() + (bz - a.z()) * t;
            float w = a.w() + (bw - a.w()) * t;
            float inv = 1f / (float) Math.sqrt(x * x + y * y + z * z + w * w);
            return new Quat(x * inv, y * inv, z * inv, w * inv);
        }
        float theta0 = (float) Math.acos(dot);
        float theta = theta0 * t;
        float sinT = (float) Math.sin(theta);
        float sinT0 = (float) Math.sin(theta0);
        float s0 = (float) Math.cos(theta) - dot * sinT / sinT0;
        float s1 = sinT / sinT0;
        return new Quat(
                s0 * a.x() + s1 * bx,
                s0 * a.y() + s1 * by,
                s0 * a.z() + s1 * bz,
                s0 * a.w() + s1 * bw);
    }
}
