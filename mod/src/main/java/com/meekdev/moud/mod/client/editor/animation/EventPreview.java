package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.audio.ResonaAudio;
import com.meekdev.moud.script.api.AudioRef;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;

final class EventPreview {

    private static final int PARTICLES = 16;
    private static final double SPREAD = 0.35;

    private EventPreview() {}

    static void crossed(AnimClip clip, double from, double to, Vector3 at) {
        if (to <= from) return;
        for (AnimClip.Event event : clip.events) {
            if (!event.preview()) continue;
            if (event.time() > from && event.time() <= to || from == 0 && event.time() == 0 && to > 0) fire(event, at);
        }
    }

    static void fire(AnimClip.Event event, Vector3 at) {
        if (!event.sound().isEmpty()) {
            try {
                ResonaAudio.INSTANCE.play(event.sound(), AudioRef.Options.DEFAULT);
            } catch (RuntimeException ignored) {
            }
        }
        if (!event.particle().isEmpty()) particles(event.particle(), at);
    }

    private static void particles(String id, Vector3 at) {
        ClientLevel level = Minecraft.getInstance().level;
        Identifier key = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (level == null || key == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(key)) return;
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(key);
        if (!(type instanceof SimpleParticleType simple)) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int n = 0; n < PARTICLES; n++) {
            level.addParticle(simple, at.x() + random.nextGaussian() * SPREAD, at.y() + random.nextGaussian() * SPREAD,
                    at.z() + random.nextGaussian() * SPREAD, random.nextGaussian() * 0.02, random.nextDouble() * 0.05, random.nextGaussian() * 0.02);
        }
    }
}
