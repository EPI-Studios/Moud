package com.moud.api.particle;

import java.util.Objects;
import java.util.List;

/**
 * Describes a continuous particle emitter that runs client-side.
 */
public record ParticleEmitterConfig(
        String id,
        ParticleDescriptor descriptor,
        float ratePerSecond,
        boolean enabled,
        int maxParticles,
        Vector3f positionJitter,
        Vector3f velocityJitter,
        float lifetimeJitter,
        long seed,
        List<String> texturePool
) {
    public ParticleEmitterConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(descriptor, "descriptor");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Emitter id cannot be blank");
        }
        ratePerSecond = Math.max(0f, ratePerSecond);
        maxParticles = Math.max(0, maxParticles);
        positionJitter = positionJitter == null ? new Vector3f(0f, 0f, 0f) : positionJitter;
        velocityJitter = velocityJitter == null ? new Vector3f(0f, 0f, 0f) : velocityJitter;
        lifetimeJitter = Math.max(0f, lifetimeJitter);
        texturePool = texturePool == null ? List.of() : List.copyOf(texturePool);
    }
}
