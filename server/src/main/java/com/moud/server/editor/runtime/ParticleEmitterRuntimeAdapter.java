package com.moud.server.editor.runtime;

import com.moud.network.MoudPackets;
import com.moud.server.MoudEngine;
import com.moud.server.particle.ParticleBatcher;
import com.moud.server.particle.ParticleEmitterManager;
import com.moud.server.proxy.ParticleAPIProxy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ParticleEmitterRuntimeAdapter implements SceneRuntimeAdapter {
    private String lastId;

    public ParticleEmitterRuntimeAdapter() {
    }

    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) {
        update(snapshot);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        Map<String, Object> snapshotProps = snapshot.properties();
        ConcurrentHashMap<String, Object> props = snapshotProps != null
                ? new ConcurrentHashMap<>(snapshotProps)
                : new ConcurrentHashMap<>();
        props.put("id", snapshot.objectId());
        lastId = snapshot.objectId();

        MoudEngine engine = MoudEngine.getInstance();
        if (engine == null) {
            return;
        }

        ParticleBatcher batcher = engine.getParticleBatcher();
        ParticleEmitterManager emitterManager = engine.getParticleEmitterManager();
        if (batcher == null || emitterManager == null) {
            return;
        }

        ParticleAPIProxy proxy = new ParticleAPIProxy(batcher, emitterManager);
        proxy.createEmitter(props);
    }

    @Override
    public void remove() {
        if (lastId != null) {
            MoudEngine engine = MoudEngine.getInstance();
            if (engine == null || engine.getParticleEmitterManager() == null) {
                return;
            }
            engine.getParticleEmitterManager().remove(lastId);
        }
    }
}
