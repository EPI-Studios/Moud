package com.meekdev.moud.script.host.render;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.effect.ParticleEmitter;
import com.meekdev.moud.core.effect.Trail;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;

public final class EffectLibrary {

    private static final int MOST_AT_ONCE = 10_000;

    private EffectLibrary() {}

    public static void install(Host host) {
        host.instances().of(Classes.PARTICLE_EMITTER).method("emit", "(count: number?) -> ()", a -> {
            ParticleEmitter emitter = a.self(ParticleEmitter.class);
            int count = a.integer(1, 16);
            if (count < 0 || count > MOST_AT_ONCE) throw new HostError("emit takes 0 to %d particles, got %d", MOST_AT_ONCE, count);
            if (host.client()) emitter.queue(count);
            else Instances.setNum(emitter, Classes.PARTICLE_EMITTER.property("emitted"), (double) emitter.emitted + count);
            return null;
        });
        host.instances().of(Classes.TRAIL).method("clear", "() -> ()", a -> {
            Trail trail = a.self(Trail.class);
            if (host.client()) trail.requestClear();
            else Instances.setNum(trail, Classes.TRAIL.property("clears"), (double) trail.clears + 1);
            return null;
        });
    }
}
