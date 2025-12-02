package com.moud.server.editor.runtime;

import com.moud.network.MoudPackets;
import com.moud.server.MoudEngine;
import com.moud.server.particle.ParticleEmitterManager;
import com.moud.server.proxy.ParticleAPIProxy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ParticleEmitterRuntimeAdapter implements SceneRuntimeAdapter {
    private final ParticleAPIProxy proxy;
    private final ParticleEmitterManager emitterManager;
    private String lastId;

    public ParticleEmitterRuntimeAdapter() {
        this.emitterManager = ParticleEmitterManager.getInstance();
        this.proxy = new ParticleAPIProxy(MoudEngine.getInstance().getParticleBatcher(), emitterManager);
    }

    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) {
        update(snapshot);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) {
        if (snapshot == null) return;
        ConcurrentHashMap<String, Object> props = snapshot.properties() != null
                ? new ConcurrentHashMap<>((Map<String, Object>) snapshot.properties())
                : new ConcurrentHashMap<>();
        props.put("id", snapshot.objectId());
        lastId = snapshot.objectId();
        proxy.createEmitter(props);
    }

    @Override
    public void remove() {
        if (lastId != null) {
            emitterManager.remove(lastId);
        }
    }
}
