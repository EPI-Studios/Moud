package com.moud.server.plugin.impl;

import com.moud.api.particle.ParticleDescriptor;
import com.moud.api.particle.ParticleEmitterConfig;
import com.moud.plugin.api.services.ParticleService;
import com.moud.server.particle.ParticleBatcher;
import com.moud.server.particle.ParticleEmitterManager;
import org.slf4j.Logger;

import java.util.Collection;

public final class ParticleServiceImpl implements ParticleService {
    private final ParticleBatcher batcher;
    private final ParticleEmitterManager emitterManager;
    private final Logger logger;

    public ParticleServiceImpl(Logger logger) {
        this.logger = logger;
        this.batcher = com.moud.server.MoudEngine.getInstance().getParticleBatcher();
        this.emitterManager = ParticleEmitterManager.getInstance();
    }

    @Override
    public void spawn(ParticleDescriptor descriptor) {
        if (descriptor == null) return;
        batcher.enqueue(descriptor);
    }

    @Override
    public void spawnMany(Collection<ParticleDescriptor> descriptors) {
        if (descriptors == null) return;
        for (ParticleDescriptor d : descriptors) {
            if (d != null) {
                batcher.enqueue(d);
            }
        }
    }

    @Override
    public void upsertEmitter(ParticleEmitterConfig config) {
        if (config == null) {
            return;
        }
        emitterManager.upsert(config);
    }

    @Override
    public void removeEmitter(String id) {
        if (id == null) {
            return;
        }
        emitterManager.remove(id);
    }

    @Override
    public void clearEmitters() {
        emitterManager.upsertAll(java.util.List.of());
    }
}
