package com.moud.client.fabric.render.scene.subrender.particle;

import com.moud.client.fabric.physics.ClientPhysicsWorld;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.BlockView;

public final class ParticleSimulation {
    private static final float FIXED_STEP = 1.0f / 30.0f;
    private static final int MAX_SUBSTEPS = 2;
    private static final float PHYSICS_PROBE_RADIUS = 0.02f;
    private static final float PHYSICS_EPSILON = 0.002f;
    private final float[] curl = new float[3];
    private final Mutable scratchPos = new Mutable();

    public void tick(EmitterState state, EmitterConfig c, float frameDt, BlockView world, ClientPhysicsWorld physics) {
        if (frameDt <= 0f || state.particles.isEmpty()) {
            flushPending(state);
            return;
        }
        float remaining = Math.min(frameDt, FIXED_STEP * MAX_SUBSTEPS);
        int steps = Math.min(MAX_SUBSTEPS, Math.max(1, (int) Math.ceil(remaining / FIXED_STEP)));
        float step = remaining / steps;
        for (int i = 0; i < steps; i++) {
            stepFixed(state, c, step, world, physics);
        }
    }

    private void stepFixed(EmitterState state, EmitterConfig c, float dt, BlockView world, ClientPhysicsWorld physics) {
        state.noiseTime += dt * c.turbSpeed;
        float dampFactor = c.damping > 0f ? (float) Math.exp(-c.damping * dt) : 1f;
        boolean hasTurb = c.turbStrength > 0f;
        boolean hasCollision = c.collision && world != null;

        List<Particle> particles = state.particles;
        int write = 0;
        for (int read = 0; read < particles.size(); read++) {
            Particle p = particles.get(read);
            p.age += dt;
            if (p.age >= p.lifetime) {
                p.alive = false;
                if (c.subEmitCount > 0 && p.canSubEmit) spawnSubEmitters(state, c, p);
                state.recycleParticle(p);
                continue;
            }
            p.vy += c.gravity * dt;
            if (c.windX != 0f) p.vx += c.windX * dt;
            if (c.windY != 0f) p.vy += c.windY * dt;
            if (c.windZ != 0f) p.vz += c.windZ * dt;
            if (hasTurb) {
                NoiseField.sampleCurl(
                        (float) (p.x * c.turbScale) + p.seedNoise,
                        (float) (p.y * c.turbScale) + state.noiseTime,
                        (float) (p.z * c.turbScale),
                        curl);
                p.vx += curl[0] * c.turbStrength * dt;
                p.vy += curl[1] * c.turbStrength * dt;
                p.vz += curl[2] * c.turbStrength * dt;
            }
            if (dampFactor != 1f) {
                p.vx *= dampFactor;
                p.vy *= dampFactor;
                p.vz *= dampFactor;
            }
            p.rotation += p.angularVelocity * dt;

            double nx = p.x + p.vx * dt;
            double ny = p.y + p.vy * dt;
            double nz = p.z + p.vz * dt;

            if (hasCollision) {
                boolean resolved = false;
                if (isSolid(world, nx, ny, nz)) {
                    if (Math.abs(p.vy) > Math.abs(p.vx) && Math.abs(p.vy) > Math.abs(p.vz)) {
                        p.vy = -p.vy * c.bounce;
                        ny = p.y;
                        p.vx *= (1f - c.friction);
                        p.vz *= (1f - c.friction);
                        if (Math.abs(p.vy) < 0.15f) { p.vy = 0f; p.grounded = true; }
                    } else if (Math.abs(p.vx) >= Math.abs(p.vz)) {
                        p.vx = -p.vx * c.bounce;
                        nx = p.x;
                        p.vy *= (1f - c.friction);
                        p.vz *= (1f - c.friction);
                    } else {
                        p.vz = -p.vz * c.bounce;
                        nz = p.z;
                        p.vx *= (1f - c.friction);
                        p.vy *= (1f - c.friction);
                    }
                    resolved = true;
                }
                if (!resolved && physics != null && physics.isAvailable()) {
                    double sx = nx - p.x, sy = ny - p.y, sz = nz - p.z;
                    if (sx * sx + sy * sy + sz * sz > 1.0e-8) {
                        Optional<ClientPhysicsWorld.RayHit> maybeHit = physics.raycastAny(p.x, p.y, p.z, sx, sy, sz, PHYSICS_PROBE_RADIUS);
                        if (maybeHit.isPresent()) {
                            ClientPhysicsWorld.RayHit hit = maybeHit.get();
                            float f = Math.max(0f, hit.fraction() - PHYSICS_EPSILON);
                            nx = p.x + sx * f;
                            ny = p.y + sy * f;
                            nz = p.z + sz * f;
                            float nxN = hit.nx(), nyN = hit.ny(), nzN = hit.nz();
                            float vDot = p.vx * nxN + p.vy * nyN + p.vz * nzN;
                            if (vDot < 0f) {
                                p.vx -= (1f + c.bounce) * vDot * nxN;
                                p.vy -= (1f + c.bounce) * vDot * nyN;
                                p.vz -= (1f + c.bounce) * vDot * nzN;
                            }
                            float tangentDamp = 1f - c.friction;
                            float vnNow = p.vx * nxN + p.vy * nyN + p.vz * nzN;
                            float tvx = p.vx - vnNow * nxN;
                            float tvy = p.vy - vnNow * nyN;
                            float tvz = p.vz - vnNow * nzN;
                            p.vx = vnNow * nxN + tvx * tangentDamp;
                            p.vy = vnNow * nyN + tvy * tangentDamp;
                            p.vz = vnNow * nzN + tvz * tangentDamp;
                            if (Math.abs(p.vy) < 0.15f && nyN > 0.6f) { p.vy = 0f; p.grounded = true; }
                        }
                    }
                }
            }

            p.x = nx; p.y = ny; p.z = nz;
            if (write != read) particles.set(write, p);
            write++;
        }
        if (write < particles.size()) {
            for (int i = particles.size() - 1; i >= write; i--) particles.remove(i);
        }
        flushPending(state);
    }

    private static void flushPending(EmitterState state) {
        if (!state.pendingAdd.isEmpty()) {
            state.particles.addAll(state.pendingAdd);
            state.pendingAdd.clear();
        }
    }

    private static void spawnSubEmitters(EmitterState state, EmitterConfig c, Particle dying) {
        int cap = Math.max(0, c.maxParticles);
        int live = state.particles.size() + state.pendingAdd.size();
        int budget = Math.max(0, cap - live);
        int toSpawn = Math.min(c.subEmitCount, budget);
        for (int i = 0; i < toSpawn; i++) {
            Particle child = state.acquireParticle();
            child.canSubEmit = false;
            child.x = dying.x; child.y = dying.y; child.z = dying.z;
            float vx = c.subEmitInheritVelocity ? dying.vx : 0f;
            float vy = c.subEmitInheritVelocity ? dying.vy : 0f;
            float vz = c.subEmitInheritVelocity ? dying.vz : 0f;
            child.vx = vx + state.rng.nextSymmetric() * c.subEmitSpeed;
            child.vy = vy + state.rng.nextSymmetric() * c.subEmitSpeed;
            child.vz = vz + state.rng.nextSymmetric() * c.subEmitSpeed;
            child.lifetime = c.subEmitLifetime;
            child.age = 0f;
            child.sizeStart = c.subEmitSize;
            child.sizeEnd = 0f;
            child.r0 = c.cr1; child.g0 = c.cg1; child.b0 = c.cb1; child.a0 = c.ca1 > 0f ? c.ca1 : 1f;
            child.r1 = c.cr1; child.g1 = c.cg1; child.b1 = c.cb1; child.a1 = 0f;
            child.rotation = state.rng.nextSymmetric() * 3.14159f;
            child.angularVelocity = state.rng.nextSymmetric() * 2f;
            child.seedNoise = state.rng.nextFloat() * 1000f;
            state.pendingAdd.add(child);
        }
    }

    public void applyLocalSpaceDrift(EmitterState state, float dx, float dy, float dz) {
        if (dx == 0f && dy == 0f && dz == 0f) return;
        for (Particle p : state.particles) {
            p.x += dx;
            p.y += dy;
            p.z += dz;
        }
    }

    private boolean isSolid(BlockView world, double x, double y, double z) {
        try {
            scratchPos.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            return !world.getBlockState(scratchPos).getCollisionShape(world, scratchPos).isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }
}

