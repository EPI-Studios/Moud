package com.moud.client.particle;

import com.moud.api.particle.ParticleDescriptor;
import com.moud.api.particle.RenderType;
import com.moud.api.particle.ParticleEmitterConfig;
import com.moud.api.particle.Vector3f;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;


public final class ParticleEmitterSystem {
    private final Map<String, ClientEmitter> emitters = new ConcurrentHashMap<>();

    public void upsert(List<ParticleEmitterConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        for (ParticleEmitterConfig config : configs) {
            emitters.compute(config.id(), (id, existing) -> {
                if (existing == null) {
                    return new ClientEmitter(config);
                }
                existing.update(config);
                return existing;
            });
        }
    }

    public void remove(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            emitters.remove(id);
        }
    }

    public void clear() {
        emitters.clear();
    }

    public void tick(float dt, ParticleSystem system, ClientWorld world, Vec3d cameraPos) {
        if (system == null) {
            return;
        }
        for (ClientEmitter emitter : emitters.values()) {
            emitter.emit(dt, system, world, cameraPos);
        }
    }

    private static final class ClientEmitter {
        private ParticleEmitterConfig config;
        private Random rng;
        private float accumulator = 0f;
        private static final float NEAR_FULL_RATE = 32f;
        private static final float FAR_CULL = 192f;
        private static final float BASE_BUDGET_WINDOW_SECONDS = 0.25f;
        private static final float LAG_WINDOW_MULTIPLIER = 2f;
        private static final int MIN_PER_EMITTER_FRAME_BUDGET = 8;
        private static final int MAX_PER_EMITTER_FRAME_BUDGET = 4096;

        ClientEmitter(ParticleEmitterConfig config) {
            update(config);
        }

        void update(ParticleEmitterConfig newConfig) {
            this.config = newConfig;
            this.rng = new Random(newConfig.seed());
            this.accumulator = 0f;
        }

        void emit(float dt, ParticleSystem system, ClientWorld world, Vec3d cameraPos) {
            if (config == null || !config.enabled()) {
                return;
            }

            float distanceFactor = 1f;
            if (cameraPos != null && config.descriptor().renderType() != RenderType.ADDITIVE) {
                Vec3d emitterPos = new Vec3d(config.descriptor().position().x(), config.descriptor().position().y(), config.descriptor().position().z());
                double distSq = cameraPos.squaredDistanceTo(emitterPos);
                double farSq = FAR_CULL * FAR_CULL;
                if (distSq > farSq) {
                    return;
                }
                double nearSq = NEAR_FULL_RATE * NEAR_FULL_RATE;
                distanceFactor = distSq <= nearSq ? 1f : (float) Math.max(0.0, (Math.sqrt(farSq) - Math.sqrt(distSq)) / (FAR_CULL - NEAR_FULL_RATE));
            }

            accumulator += config.ratePerSecond() * dt * distanceFactor;
            int emitCount = (int) Math.floor(accumulator);
            accumulator -= emitCount;

            if (emitCount <= 0) {
                return;
            }

            float frameWindow = Math.max(BASE_BUDGET_WINDOW_SECONDS, dt * LAG_WINDOW_MULTIPLIER);
            int perEmitterFrameBudget = Math.min(
                    MAX_PER_EMITTER_FRAME_BUDGET,
                    Math.max(MIN_PER_EMITTER_FRAME_BUDGET, (int) Math.ceil(config.ratePerSecond() * frameWindow))
            );

            int remainingBudget = Math.max(0, system.getCapacity() - system.getActiveCount());
            if (remainingBudget <= 0) {
                return;
            }
            int toSpawn = Math.min(Math.min(emitCount, remainingBudget), perEmitterFrameBudget);

            for (int i = 0; i < toSpawn; i++) {
                ParticleDescriptor descriptor = applyJitter(config, rng);
                system.spawn(descriptor);
            }
        }

        private ParticleDescriptor applyJitter(ParticleEmitterConfig emitterConfig, Random random) {
            ParticleDescriptor base = emitterConfig.descriptor();
            Vector3f pos = base.position();
            Vector3f vel = base.velocity();
            Vector3f posJitter = emitterConfig.positionJitter();
            Vector3f velJitter = emitterConfig.velocityJitter();

            float px = pos.x() + sample(random, posJitter.x());
            float py = pos.y() + sample(random, posJitter.y());
            float pz = pos.z() + sample(random, posJitter.z());

            float vx = vel.x() + sample(random, velJitter.x());
            float vy = vel.y() + sample(random, velJitter.y());
            float vz = vel.z() + sample(random, velJitter.z());

            float lifetime = Math.max(0.01f, base.lifetimeSeconds() + sample(random, emitterConfig.lifetimeJitter()));
            String texture = resolveTexture(emitterConfig, base, random);

            return new ParticleDescriptor(
                    texture,
                    base.renderType(),
                    base.billboarding(),
                    base.collisionMode(),
                    base.collideWithPlayers(),
                    new Vector3f(px, py, pz),
                    new Vector3f(vx, vy, vz),
                    base.acceleration(),
                    base.drag(),
                    base.gravityMultiplier(),
                    lifetime,
                    base.sizeOverLife(),
                    base.rotationOverLife(),
                    base.colorOverLife(),
                    base.alphaOverLife(),
                    base.uvRegion(),
                    base.frameAnimation(),
                    base.behaviors(),
                    base.behaviorPayload(),
                    base.light(),
                    base.sortHint(),
                    base.impostorSlices()
            );
        }

        private float sample(Random random, float magnitude) {
            if (magnitude == 0f) {
                return 0f;
            }
            return (random.nextFloat() * 2f - 1f) * magnitude;
        }

        private String resolveTexture(ParticleEmitterConfig emitterConfig, ParticleDescriptor base, Random random) {
            List<String> pool = emitterConfig.texturePool();
            if (pool != null && !pool.isEmpty()) {
                int idx = random.nextInt(pool.size());
                return pool.get(idx);
            }
            return base.texture();
        }
    }
}
