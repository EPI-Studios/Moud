package com.moud.client.fabric.render.scene.subrender.particle;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Queue;

public final class EmitterState {
    public final ArrayList<Particle> particles = new ArrayList<>();
    public final ArrayList<Particle> pendingAdd = new ArrayList<>();
    public final ArrayDeque<Particle> pool = new ArrayDeque<>();
    public final Queue<Integer> pendingBursts = new ArrayDeque<>();
    public int poolLimit = 8192;

    public Particle acquireParticle() {
        Particle p = pool.pollLast();
        if (p == null) return new Particle();
        p.reset();
        return p;
    }

    public void recycleParticle(Particle p) {
        if (p == null) return;
        if (pool.size() < poolLimit) pool.add(p);
    }
    public EmitterConfig cachedConfig;
    public long cachedConfigNanos;
    public int cachedPropsIdentity;
    public float spawnAccumulator;
    public double lastEmitterX, lastEmitterY, lastEmitterZ;
    public boolean hasLastPos;
    public boolean initialized;
    public boolean oneShotFired;
    public long seedVersion;
    public SeededRng rng;
    public float accumulatedTime;
    public float noiseTime;
    public boolean forceEmitOnce;
    public boolean restartRequested;
    public Boolean emittingOverride;

    public Float rateOverride;
    public Float lifetimeOverride;
    public double poseOffsetX, poseOffsetY, poseOffsetZ;
    public boolean hasPoseOffset;

    public double parentVelX, parentVelY, parentVelZ;
    public boolean lowDetail;
    public float lodSizeScale = 1f;
}
