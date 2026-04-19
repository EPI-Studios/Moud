package com.moud.client.fabric.render.scene.subrender.particle;

public final class ParticleApi {
    private static volatile ParticleRenderer active;

    private ParticleApi() {
    }

    static void bind(ParticleRenderer renderer) {
        active = renderer;
    }

    public static boolean emit(long nodeId, int count) {
        ParticleRenderer r = active;
        if (r == null || count <= 0) return false;
        EmitterState s = r.state(nodeId);
        if (s == null) return false;
        s.pendingBursts.add(count);
        return true;
    }

    public static boolean burst(long nodeId) {
        return setForceEmit(nodeId);
    }

    public static boolean restart(long nodeId) {
        ParticleRenderer r = active;
        if (r == null) return false;
        EmitterState s = r.state(nodeId);
        if (s == null) return false;
        s.restartRequested = true;
        return true;
    }

    public static boolean setEmitting(long nodeId, boolean emitting) {
        ParticleRenderer r = active;
        if (r == null) return false;
        EmitterState s = r.state(nodeId);
        if (s == null) return false;
        s.emittingOverride = emitting;
        return true;
    }

    public static int aliveCount(long nodeId) {
        ParticleRenderer r = active;
        if (r == null) return 0;
        EmitterState s = r.state(nodeId);
        return s == null ? 0 : s.particles.size();
    }

    public static boolean setRate(long nodeId, float rate) {
        ParticleRenderer r = active;
        if (r == null) return false;
        EmitterState s = r.state(nodeId);
        if (s == null) return false;
        s.rateOverride = Math.max(0f, rate);
        return true;
    }

    public static boolean setLifetime(long nodeId, float lifetime) {
        ParticleRenderer r = active;
        if (r == null) return false;
        EmitterState s = r.state(nodeId);
        if (s == null) return false;
        s.lifetimeOverride = Math.max(0.01f, lifetime);
        return true;
    }

    public static boolean moveTo(long nodeId, double x, double y, double z) {
        ParticleRenderer r = active;
        if (r == null) return false;
        EmitterState s = r.state(nodeId);
        if (s == null) return false;
        s.poseOffsetX = x;
        s.poseOffsetY = y;
        s.poseOffsetZ = z;
        s.hasPoseOffset = true;
        return true;
    }

    public static boolean emitAt(long nodeId, double x, double y, double z, int count) {
        ParticleRenderer r = active;
        if (r == null || count <= 0) return false;
        EmitterState s = r.state(nodeId);
        if (s == null) return false;
        s.poseOffsetX = x;
        s.poseOffsetY = y;
        s.poseOffsetZ = z;
        s.hasPoseOffset = true;
        s.pendingBursts.add(count);
        return true;
    }

    public static boolean clearMoveTo(long nodeId) {
        ParticleRenderer r = active;
        if (r == null) return false;
        EmitterState s = r.state(nodeId);
        if (s == null) return false;
        s.hasPoseOffset = false;
        return true;
    }

    private static boolean setForceEmit(long nodeId) {
        ParticleRenderer r = active;
        if (r == null) return false;
        EmitterState s = r.state(nodeId);
        if (s == null) return false;
        s.forceEmitOnce = true;
        return true;
    }
}
