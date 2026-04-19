package com.moud.client.fabric.render.scene.subrender.particle;

import com.moud.client.fabric.render.scene.math.Pose;

public final class ParticleSpawner {
    private static final int ABSOLUTE_MAX = 1_000_000;
    private final float[] scratch = new float[3];

    public int spawnContinuous(EmitterState state, EmitterConfig c, Pose world, float dt) {
        if (!c.emitting || c.rate <= 0f || dt <= 0f) {
            state.spawnAccumulator = 0f;
            return 0;
        }
        state.spawnAccumulator += c.rate * dt;
        int toSpawn = (int) state.spawnAccumulator;
        if (toSpawn <= 0) return 0;
        state.spawnAccumulator -= toSpawn;
        return spawnBatch(state, c, world, toSpawn);
    }

    public int spawnBurst(EmitterState state, EmitterConfig c, Pose world, int count) {
        if (count <= 0) return 0;
        return spawnBatch(state, c, world, count);
    }

    private int spawnBatch(EmitterState state, EmitterConfig c, Pose world, int toSpawn) {
        int cap = Math.min(Math.max(0, c.maxParticles), ABSOLUTE_MAX);
        int available = cap - state.particles.size();
        if (available <= 0) return 0;
        toSpawn = Math.min(toSpawn, available);
        state.particles.ensureCapacity(state.particles.size() + toSpawn);
        state.poolLimit = Math.min(ABSOLUTE_MAX, Math.max(state.poolLimit, cap));

        float baseMag = (float) Math.sqrt(c.velocityX * c.velocityX + c.velocityY * c.velocityY + c.velocityZ * c.velocityZ);
        float dirX, dirY, dirZ;
        if (baseMag > 1.0e-5f) {
            dirX = c.velocityX / baseMag;
            dirY = c.velocityY / baseMag;
            dirZ = c.velocityZ / baseMag;
        } else {
            dirX = 0f; dirY = 1f; dirZ = 0f;
        }
        float cosMax = (float) Math.cos(Math.toRadians(c.spreadDeg));
        int totalFrames = Math.max(1, c.frameCount);

        for (int i = 0; i < toSpawn; i++) {
            Particle p = state.acquireParticle();
            ShapeSampler.sample(c, state.rng, scratch);
            p.x = world.pos.x + scratch[0];
            p.y = world.pos.y + scratch[1];
            p.z = world.pos.z + scratch[2];

            float cosTheta = cosMax + state.rng.nextFloat() * (1f - cosMax);
            float sinTheta = (float) Math.sqrt(Math.max(0f, 1f - cosTheta * cosTheta));
            float phi = (float) (state.rng.nextFloat() * Math.PI * 2.0);
            float lx = sinTheta * (float) Math.cos(phi);
            float ly = sinTheta * (float) Math.sin(phi);
            float lz = cosTheta;
            float[] rotated = rotateZTo(lx, ly, lz, dirX, dirY, dirZ);

            float speed = baseMag + state.rng.nextSymmetric() * c.velocityRandom;
            p.vx = rotated[0] * speed;
            p.vy = rotated[1] * speed;
            p.vz = rotated[2] * speed;

            p.age = 0f;
            p.lifetime = Math.max(0.01f, c.lifetime + state.rng.nextSymmetric() * c.lifetimeVariance);
            p.sizeStart = c.sizeStart;
            p.sizeEnd = c.sizeEnd;
            p.r0 = c.cr0; p.g0 = c.cg0; p.b0 = c.cb0; p.a0 = c.ca0;
            p.r1 = c.cr1; p.g1 = c.cg1; p.b1 = c.cb1; p.a1 = c.ca1;
            p.rotation = c.rotationStart + state.rng.nextSymmetric() * c.rotationVariance;
            p.angularVelocity = c.angularVelocity + state.rng.nextSymmetric() * c.angularVariance;
            p.frameOffset = c.frameRandomStart ? state.rng.nextInt(totalFrames) : c.frameStart;
            p.seedNoise = state.rng.nextFloat() * 1000f;
            state.particles.add(p);
        }
        return toSpawn;
    }

    private static float[] rotateZTo(float lx, float ly, float lz, float dx, float dy, float dz) {
        if (dz > 0.9999f) return new float[]{lx, ly, lz};
        if (dz < -0.9999f) return new float[]{lx, -ly, -lz};
        float ax = -dy, ay = dx;
        float axisLen = (float) Math.sqrt(ax * ax + ay * ay);
        ax /= axisLen; ay /= axisLen;
        float c = dz;
        float s = (float) Math.sqrt(Math.max(0f, 1f - c * c));
        float oc = 1f - c;
        float m00 = c + ax * ax * oc;
        float m01 = ax * ay * oc;
        float m02 = ay * s;
        float m10 = ay * ax * oc;
        float m11 = c + ay * ay * oc;
        float m12 = -ax * s;
        float m20 = -ay * s;
        float m21 = ax * s;
        float m22 = c;
        return new float[]{
                m00 * lx + m01 * ly + m02 * lz,
                m10 * lx + m11 * ly + m12 * lz,
                m20 * lx + m21 * ly + m22 * lz
        };
    }
}
