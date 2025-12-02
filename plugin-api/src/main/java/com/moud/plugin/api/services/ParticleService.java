package com.moud.plugin.api.services;

import com.moud.api.particle.ParticleDescriptor;
import com.moud.api.particle.ParticleEmitterConfig;

import java.util.Collection;

/**
 * Client-side particle helpers available to plugins.
 */
public interface ParticleService {
    /**
     * Spawn a single, immediate particle burst descriptor.
     */
    void spawn(ParticleDescriptor descriptor);

    /**
     * Spawn many particles at once.
     */
    void spawnMany(Collection<ParticleDescriptor> descriptors);

    /**
     * Create or update a named emitter that runs client-side.
     */
    void upsertEmitter(ParticleEmitterConfig config);

    /**
     * Remove a named emitter by id.
     */
    void removeEmitter(String id);

    /**
     * Remove all emitters.
     */
    void clearEmitters();
}
