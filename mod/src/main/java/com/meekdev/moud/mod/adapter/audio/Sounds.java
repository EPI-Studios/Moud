package com.meekdev.moud.mod.adapter.audio;

import com.meekdev.moud.core.audio.Sound;
import com.meekdev.moud.core.audio.SoundBus;
import com.meekdev.moud.core.audio.SoundEffect;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.resona.api.PlaySettings;
import com.meekdev.resona.api.Resona;
import com.meekdev.resona.api.SoundHandle;
import com.meekdev.resona.bank.EventDefinition;
import com.meekdev.resona.bank.SoundBank;
import com.meekdev.resona.dsp.AudioEffect;
import com.meekdev.resona.dsp.EffectChain;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class Sounds {

    private static final PropertyDef TIME_LENGTH = Classes.SOUND.property("timeLength");
    private static final PropertyDef IS_LOADED = Classes.SOUND.property("isLoaded");

    private static final class Voice {
        @Nullable SoundHandle handle;
        int plays;
        boolean wanted;
        boolean paused;
        boolean processed;
        double volume;
        double pitch;
        double at;
        @Nullable EffectChain chain;
        List<SoundEffect> shape = List.of();
        String asked = "";
        @Nullable Identifier sound;
    }

    private static final Map<Sound, Voice> VOICES = new HashMap<>();

    private Sounds() {}

    public static void frame(float partialTick) {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) {
            stopAll();
            return;
        }
        for (SoundBus bus : tree.ofClass(Classes.SOUND_BUS)) {
            var mixed = Resona.bus(bus.bus);
            mixed.setVolume((float) bus.volume);
            mixed.setMuted(bus.muted);
            mixed.setLowPass((float) bus.lowPass);
        }

        Motion motion = ClientScene.motion();
        for (Sound sound : tree.ofClass(Classes.SOUND)) {
            if (Instance.outOfWorld(sound)) continue;
            Voice voice = VOICES.computeIfAbsent(sound, s -> new Voice());
            Instance anchor = anchor(sound);
            Vector3 at = anchor == null ? null : motion.sample(anchor, partialTick).position();

            List<SoundEffect> effects = SoundEffects.of(sound, tree);
            List<AudioEffect> units = effects.isEmpty() ? List.of() : SoundEffects.units(effects);
            if (!effects.equals(voice.shape)) {
                voice.shape = effects;
                if (voice.chain != null) voice.chain.set(units);
            }
            load(sound, voice);

            boolean rebuild = voice.handle != null && voice.processed != !effects.isEmpty();
            if (!sound.playing && voice.handle != null) {
                voice.handle.stop();
                voice.handle = null;
            }
            if (sound.playing && (!voice.wanted || sound.plays != voice.plays || rebuild)) {
                double from = rebuild && voice.handle != null ? voice.handle.position() : 0;
                if (voice.handle != null) voice.handle.stop();
                voice.chain = effects.isEmpty() ? null : new EffectChain();
                if (voice.chain != null) voice.chain.set(units);
                voice.processed = !effects.isEmpty();
                voice.handle = start(sound, at, voice.chain);
                voice.volume = sound.volume;
                voice.pitch = sound.pitch;
                voice.paused = false;
                voice.at = from;
                if (voice.handle != null && from > 0) voice.handle.seek(from);
                if (voice.handle != null && !rebuild) sound.played.fire(sound);
            }
            voice.plays = sound.plays;
            voice.wanted = sound.playing;

            if (voice.handle == null) {
                sound.playbackLoudness = 0;
                continue;
            }
            if (!voice.handle.isPlaying()) {
                voice.handle = null;
                sound.playbackLoudness = 0;
                sound.ended.fire(sound);
                continue;
            }
            if (sound.volume != voice.volume) voice.handle.setVolume((float) sound.volume);
            if (sound.pitch != voice.pitch) voice.handle.setPitch((float) sound.pitch);
            voice.volume = sound.volume;
            voice.pitch = sound.pitch;
            if (sound.paused != voice.paused) {
                if (sound.paused) voice.handle.pause();
                else voice.handle.resume();
                voice.paused = sound.paused;
            }
            if (sound.timePosition != voice.at) {
                voice.handle.seek(sound.timePosition);
                voice.at = sound.timePosition;
            } else {
                voice.at = voice.handle.position();
                sound.timePosition = voice.at;
            }
            sound.playbackLoudness = Math.min(1000, Math.max(0, voice.handle.loudness() * 1000));
            if (at != null) voice.handle.setPosition(ResonaAudio.vec(at));
        }

        Iterator<Map.Entry<Sound, Voice>> gone = VOICES.entrySet().iterator();
        while (gone.hasNext()) {
            Map.Entry<Sound, Voice> entry = gone.next();
            if (entry.getKey().isAlive() && entry.getKey().tree() == tree) continue;
            if (entry.getValue().handle != null) entry.getValue().handle.stop();
            gone.remove();
        }
    }

    private static void load(Sound sound, Voice voice) {
        if (!sound.soundId.equals(voice.asked)) {
            voice.asked = sound.soundId;
            voice.sound = ResonaAudio.INSTANCE.sounds.id(sound.soundId);
            if (voice.sound != null) Resona.preload(voice.sound);
            if (sound.isLoaded) Instances.setBool(sound, IS_LOADED, false);
            if (sound.timeLength != 0) Instances.setNum(sound, TIME_LENGTH, 0);
        }
        if (voice.sound == null || sound.isLoaded) return;
        if (!Resona.isLoaded(voice.sound)) return;
        double length = Resona.lengthOf(voice.sound);
        Instances.setNum(sound, TIME_LENGTH, Math.max(0, length));
        Instances.setBool(sound, IS_LOADED, true);
        sound.loaded.fire(sound);
    }

    private static @Nullable Instance anchor(Sound sound) {
        for (Instance at = sound.parent(); at != null; at = at.parent()) {
            if (at instanceof ViewportFrame) return null;
            if (at instanceof Spatial && at.parent() != null) return at;
        }
        return null;
    }

    private static @Nullable SoundHandle start(Sound sound, @Nullable Vector3 at, @Nullable EffectChain chain) {
        PlaySettings settings;
        if (!sound.event.isEmpty()) {
            Optional<EventDefinition> event = SoundBank.event(sound.event);
            if (event.isEmpty()) return null;
            settings = event.get().toSettings();
            settings.volume(settings.volume() * (float) sound.volume)
                    .pitch(settings.pitch() * (float) sound.pitch)
                    .loop(settings.loop() || sound.looped);
        } else {
            Identifier id = ResonaAudio.INSTANCE.sounds.id(sound.soundId);
            if (id == null) return null;
            settings = PlaySettings.of(id).bus(sound.bus).volume((float) sound.volume)
                    .pitch((float) sound.pitch).loop(sound.looped);
        }
        settings.priority(sound.priority)
                .distance((float) sound.minDistance, (float) sound.maxDistance, (float) sound.rollOff)
                .fadeIn((float) sound.fadeIn)
                .stream(sound.stream)
                .effects(chain);
        if (at != null) settings.at(ResonaAudio.vec(at));
        return Resona.play(settings);
    }

    public static void stopAll() {
        for (Voice voice : VOICES.values()) {
            if (voice.handle != null) voice.handle.stop();
        }
        VOICES.clear();
        SoundEffects.forget();
    }
}
